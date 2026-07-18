import { readEnvironment, selectQualityProfile } from "./home-cinematic-quality.js";
import { createLaboratory } from "./home-cinematic-lab.js";
import { createQuantEngine } from "./home-cinematic-engine.js";
import { createDataVortex } from "./home-cinematic-particles.js";

const THREE_MODULE_URL = "/js/vendor/three.module.js";
const CAMERA_START_Z = 14.2;
const CAMERA_END_Z = 5.8;
const root = document.querySelector("[data-cinematic-lab]");
const canvas = document.querySelector("[data-cinematic-canvas]");

if (root && canvas) {
    startCinematicLaboratory(root, canvas).catch(() => {
        document.body.classList.add("is-fallback");
        startCanvasFallback(canvas);
    });
}

async function startCinematicLaboratory(stage, targetCanvas) {
    const THREE = await import(THREE_MODULE_URL);
    const quality = selectQualityProfile(readEnvironment());
    const renderer = new THREE.WebGLRenderer({
        canvas: targetCanvas,
        alpha: true,
        antialias: quality.name !== "low",
        powerPreference: "high-performance"
    });
    renderer.setClearColor(0x03050b, 0);
    renderer.outputColorSpace = THREE.SRGBColorSpace;
    renderer.toneMapping = THREE.ACESFilmicToneMapping;
    renderer.toneMappingExposure = 0.94;
    renderer.shadowMap.enabled = quality.shadows;
    renderer.shadowMap.type = THREE.PCFSoftShadowMap;

    const scene = new THREE.Scene();
    scene.fog = new THREE.FogExp2(0x03050b, 0.024);
    scene.environment = createStudioEnvironment(THREE, renderer);
    const camera = new THREE.PerspectiveCamera(40, 1, 0.1, 80);
    const laboratory = createLaboratory(THREE, quality);
    const engine = createQuantEngine(THREE, quality);
    const vortex = createDataVortex(THREE, quality);
    scene.add(laboratory.group, engine.group, vortex.group);

    const state = {
        width: 0,
        height: 0,
        progress: 0,
        targetProgress: 0,
        visible: true,
        pointerX: 0,
        pointerY: 0,
        targetPointerX: 0,
        targetPointerY: 0,
        startedAt: performance.now()
    };

    const resize = () => {
        const rect = targetCanvas.getBoundingClientRect();
        const width = Math.max(1, Math.round(rect.width));
        const height = Math.max(1, Math.round(rect.height));
        if (width === state.width && height === state.height) {
            return;
        }
        state.width = width;
        state.height = height;
        renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, quality.pixelRatio));
        renderer.setSize(width, height, false);
        camera.aspect = width / height;
        camera.fov = camera.aspect < 0.78 ? 46 : 40;
        camera.updateProjectionMatrix();
    };

    const readProgress = () => {
        const rect = stage.getBoundingClientRect();
        const travel = Math.max(1, rect.height - window.innerHeight);
        state.targetProgress = quality.name === "reduced" ? 0.72 : clamp(-rect.top / travel, 0, 1);
    };

    const readPointer = (event) => {
        if (quality.name === "reduced") {
            return;
        }
        state.targetPointerX = clamp(event.clientX / window.innerWidth * 2 - 1, -1, 1);
        state.targetPointerY = clamp(event.clientY / window.innerHeight * -2 + 1, -1, 1);
    };

    const observer = new IntersectionObserver((entries) => {
        state.visible = entries.some((entry) => entry.isIntersecting) && !document.hidden;
    }, { threshold: 0.01 });
    observer.observe(stage);

    window.addEventListener("resize", resize, { passive: true });
    window.addEventListener("scroll", readProgress, { passive: true });
    window.addEventListener("pointermove", readPointer, { passive: true });
    window.addEventListener("pointerleave", () => {
        state.targetPointerX = 0;
        state.targetPointerY = 0;
    }, { passive: true });
    document.addEventListener("visibilitychange", () => {
        state.visible = !document.hidden;
    });

    const frame = (now) => {
        resize();
        readProgress();
        state.progress += (state.targetProgress - state.progress) * 0.075;
        state.pointerX += (state.targetPointerX - state.pointerX) * 0.05;
        state.pointerY += (state.targetPointerY - state.pointerY) * 0.05;
        const time = quality.name === "reduced" ? 0 : (now - state.startedAt) * 0.001;

        updateCamera(camera, state, quality);
        laboratory.update(time, state.progress, state);
        engine.update(time, state.progress, state);
        vortex.update(time, state.progress, state);
        updateOverlay(state.progress);

        if (state.visible) {
            renderer.render(scene, camera);
        }
        requestAnimationFrame(frame);
    };

    requestAnimationFrame(frame);
}

function updateCamera(camera, state, quality) {
    const portrait = state.width / Math.max(1, state.height) < 0.78;
    const startZ = portrait ? 16 : CAMERA_START_Z;
    const endZ = portrait ? 8.2 : CAMERA_END_Z;
    const performanceOffset = !portrait && quality.name === "low" ? 0.8 : 0;
    const dollyProgress = smoothstep(0, 1, state.progress);
    camera.position.set(
        state.pointerX * (0.14 - dollyProgress * 0.05),
        lerp(1.22, 0.92, dollyProgress) + state.pointerY * 0.06,
        lerp(startZ + performanceOffset, endZ + performanceOffset * 0.5, dollyProgress)
    );
    camera.lookAt(
        state.pointerX * 0.05,
        lerp(0.18, -0.18, dollyProgress),
        lerp(-2.2, -4.15, dollyProgress)
    );
}

function updateOverlay(progress) {
    document.documentElement.style.setProperty("--scene-progress", progress.toFixed(4));
    document.querySelector(".cinematic-intro")?.classList.toggle("is-hidden", progress > 0.2);
    document.querySelectorAll("[data-metric]").forEach((metric, index) => {
        metric.classList.toggle("is-visible", progress > 0.27 + index * 0.085 && progress < 0.84);
    });
    document.querySelector(".cinematic-handoff")?.classList.toggle("is-visible", progress > 0.86);
    document.querySelector(".cinematic-scroll-cue")?.classList.toggle("is-hidden", progress > 0.08);
}

export function startCanvasFallback(targetCanvas) {
    const context = targetCanvas.getContext("2d");
    if (!context) {
        return;
    }
    const draw = () => {
        const width = targetCanvas.clientWidth || window.innerWidth;
        const height = targetCanvas.clientHeight || window.innerHeight;
        const ratio = Math.min(window.devicePixelRatio || 1, 1.25);
        targetCanvas.width = Math.round(width * ratio);
        targetCanvas.height = Math.round(height * ratio);
        context.setTransform(ratio, 0, 0, ratio, 0, 0);
        const gradient = context.createRadialGradient(
            width / 2,
            height * 0.56,
            10,
            width / 2,
            height * 0.56,
            width * 0.58
        );
        gradient.addColorStop(0, "#1f5fc4");
        gradient.addColorStop(0.22, "#0a1d42");
        gradient.addColorStop(1, "#03050b");
        context.fillStyle = gradient;
        context.fillRect(0, 0, width, height);
    };
    draw();
    window.addEventListener("resize", draw, { passive: true });
}

function createStudioEnvironment(THREE, renderer) {
    const studio = new THREE.Scene();
    studio.background = new THREE.Color(0x02040a);
    const cardGeometry = new THREE.PlaneGeometry(8, 8);

    [
        { color: 0x8eb7ff, position: [-4, 3, 3], rotation: [0, 0.75, 0], scale: [0.5, 1.4] },
        { color: 0x315fba, position: [4, 1, 1], rotation: [0, -0.9, 0], scale: [0.45, 1.1] },
        { color: 0xff9b61, position: [-1, 4, -3], rotation: [1.25, 0, 0], scale: [0.7, 0.32] }
    ].forEach((configuration) => {
        const card = new THREE.Mesh(
            cardGeometry,
            new THREE.MeshBasicMaterial({
                color: configuration.color,
                side: THREE.DoubleSide,
                toneMapped: false
            })
        );
        card.position.set(...configuration.position);
        card.rotation.set(...configuration.rotation);
        card.scale.set(configuration.scale[0], configuration.scale[1], 1);
        studio.add(card);
    });

    const pmremGenerator = new THREE.PMREMGenerator(renderer);
    const renderTarget = pmremGenerator.fromScene(studio, 0.04);
    pmremGenerator.dispose();
    return renderTarget.texture;
}

function clamp(value, min, max) {
    return Math.min(max, Math.max(min, value));
}

function lerp(start, end, amount) {
    return start + (end - start) * amount;
}

function smoothstep(edge0, edge1, value) {
    const x = clamp((value - edge0) / Math.max(0.0001, edge1 - edge0), 0, 1);
    return x * x * (3 - 2 * x);
}
