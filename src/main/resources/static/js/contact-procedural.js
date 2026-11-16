import * as THREE from "/js/vendor/three.module.js";
import { createProceduralCoreWorld } from "/js/contact-procedural-core.js";

const stage = document.querySelector("[data-procedural-stage]");
const canvas = document.querySelector("[data-procedural-canvas]");
const loaderValue = document.querySelector("[data-core-loader-value]");
const loaderTrack = document.querySelector("[data-core-loader-track]");
const errorMessage = document.querySelector("[data-procedural-error]");
const stability = document.querySelector("[data-core-stability]");
const coordinateX = document.querySelector("[data-core-x]");
const coordinateY = document.querySelector("[data-core-y]");
const coreField = document.querySelector("[data-core-field]");
const coreHz = document.querySelector("[data-core-hz]");
const coreClock = document.querySelector("[data-core-clock]");
const coreCycle = document.querySelector("[data-core-cycle]");
const corePhase = document.querySelector("[data-core-phase]");
const coreFlux = document.querySelector("[data-core-flux]");
const signalCursor = document.querySelector("[data-signal-cursor]");
const prefersReducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)");

if (stage && canvas) {
    initializeProceduralExperience().catch((error) => {
        showFallback(error);
    });
}

async function initializeProceduralExperience() {
    let progress = 0;
    const loadingTimer = window.setInterval(() => {
        progress = Math.min(92, progress + Math.max(1, Math.round((94 - progress) * 0.12)));
        if (loaderValue) loaderValue.textContent = String(progress).padStart(3, "0");
        if (loaderTrack) loaderTrack.style.width = `${progress}%`;
    }, 36);

    let renderer;
    let world;
    let animationActive = true;
    let disposed = false;
    let lastStabilityUpdate = 0;

    const pointerTarget = new THREE.Vector2();
    const pointer = new THREE.Vector2();
    const phaseNames = ["CALIBRATION", "SYNTHESIS", "COHERENCE", "RESOLUTION"];
    const scene = new THREE.Scene();
    const camera = new THREE.PerspectiveCamera(34, 1, 0.1, 80);
    const clock = new THREE.Clock(false);

    try {
        renderer = new THREE.WebGLRenderer({
            canvas,
            antialias: window.innerWidth > 720,
            alpha: false,
            powerPreference: "high-performance"
        });
    } catch (error) {
        window.clearInterval(loadingTimer);
        throw error;
    }

    renderer.setClearColor(0x030711, 1);
    renderer.outputColorSpace = THREE.SRGBColorSpace;
    renderer.toneMapping = THREE.ACESFilmicToneMapping;
    renderer.toneMappingExposure = 0.96;

    const particleCount = window.innerWidth < 720 ? 520 : 1240;
    world = createProceduralCoreWorld(THREE, { particleCount, seed: 917 });
    scene.add(world.root);
    stage.dataset.detailLayers = "backdrop,constellations,chambers,filaments,scan-volume,surface-network,telemetry,signal-arcs,micro-nodes";

    const resize = () => {
        const width = Math.max(1, stage.clientWidth);
        const height = Math.max(1, stage.clientHeight);
        const aspect = width / height;
        const mobile = width < 720;
        renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, mobile ? 1.25 : 1.5));
        renderer.setSize(width, height, false);
        camera.aspect = aspect;
        camera.position.z = mobile ? 12.45 : aspect > 2.1 ? 10.15 : 10.72;
        camera.updateProjectionMatrix();
        world.coreAssembly.scale.setScalar(mobile ? 0.84 : aspect > 2.1 ? 1.055 : 1);
        world.coreAssembly.position.y = mobile ? 0.14 : 0.04;
    };

    const updatePointer = (clientX, clientY) => {
        pointerTarget.x = (clientX / window.innerWidth) * 2 - 1;
        pointerTarget.y = -((clientY / window.innerHeight) * 2 - 1);
    };

    const onPointerMove = (event) => {
        updatePointer(event.clientX, event.clientY);
        stage.classList.add("pointer-active");
    };
    const onTouchMove = (event) => {
        const touch = event.touches[0];
        if (touch) updatePointer(touch.clientX, touch.clientY);
    };
    const resetPointer = () => pointerTarget.set(0, 0);
    const onPointerDown = () => stage.classList.add("pointer-pressed");
    const onPointerUp = () => stage.classList.remove("pointer-pressed");

    const renderFrame = () => {
        const delta = Math.min(clock.getDelta(), 0.05);
        const elapsed = prefersReducedMotion.matches ? 0.8 : clock.elapsedTime;
        const pointerEase = 1 - Math.exp(-delta * 4.2);
        const cameraEase = 1 - Math.exp(-delta * 2.15);
        pointer.x += (pointerTarget.x - pointer.x) * pointerEase;
        pointer.y += (pointerTarget.y - pointer.y) * pointerEase;

        world.update({
            elapsed,
            pointerX: prefersReducedMotion.matches ? 0 : pointer.x,
            pointerY: prefersReducedMotion.matches ? 0 : pointer.y
        });

        camera.position.x += (pointer.x * 0.3 - camera.position.x) * cameraEase;
        camera.position.y += (pointer.y * 0.2 - camera.position.y) * cameraEase;
        camera.lookAt(0, 0, 0);
        renderer.render(scene, camera);

        stage.style.setProperty("--pointer-x", pointer.x.toFixed(3));
        stage.style.setProperty("--pointer-y", pointer.y.toFixed(3));
        if (signalCursor) {
            const cursorX = (pointer.x + 1) * 0.5 * stage.clientWidth;
            const cursorY = (1 - pointer.y) * 0.5 * stage.clientHeight;
            signalCursor.style.transform = `translate3d(${cursorX.toFixed(2)}px, ${cursorY.toFixed(2)}px, 0) translate(-50%, -50%) rotate(${(elapsed * 9).toFixed(2)}deg)`;
        }

        if (elapsed - lastStabilityUpdate > 0.12) {
            const value = 98.72 + Math.sin(elapsed * 0.64) * 0.19;
            if (stability) stability.textContent = `${value.toFixed(2)}%`;
            if (coordinateX) coordinateX.textContent = signed(pointer.x * 18.4);
            if (coordinateY) coordinateY.textContent = signed(pointer.y * 18.4);
            if (coreField) coreField.textContent = (72.84 + Math.sin(elapsed * 0.47) * 1.26).toFixed(2);
            if (coreHz) coreHz.textContent = (917 + Math.sin(elapsed * 0.8) * 2.7).toFixed(1);
            if (coreCycle) coreCycle.textContent = `CYCLE ${String(Math.floor(elapsed * 6.4) % 10000).padStart(4, "0")}`;
            if (coreClock) coreClock.textContent = formatRuntime(elapsed);
            if (corePhase) {
                const phaseIndex = Math.floor(elapsed / 5.5) % phaseNames.length;
                corePhase.textContent = `${String(phaseIndex + 1).padStart(2, "0")} / ${phaseNames[phaseIndex]}`;
            }
            if (coreFlux) coreFlux.textContent = (0.784 + Math.sin(elapsed * 0.73) * 0.048).toFixed(3);
            lastStabilityUpdate = elapsed;
        }

        if (prefersReducedMotion.matches) renderer.setAnimationLoop(null);
    };

    const resume = () => {
        if (!disposed && animationActive) {
            if (!clock.running) clock.start();
            renderer.setAnimationLoop(renderFrame);
        }
    };
    const pause = () => {
        renderer.setAnimationLoop(null);
        clock.stop();
    };
    const onVisibilityChange = () => {
        if (document.hidden) pause();
        else resume();
    };
    const onMotionPreferenceChange = () => {
        if (prefersReducedMotion.matches) {
            pointerTarget.set(0, 0);
            renderer.setAnimationLoop(renderFrame);
        } else {
            resume();
        }
    };

    const dispose = () => {
        if (disposed) return;
        disposed = true;
        animationActive = false;
        pause();
        window.removeEventListener("resize", resize);
        window.removeEventListener("pointermove", onPointerMove);
        window.removeEventListener("touchmove", onTouchMove);
        window.removeEventListener("pointerleave", resetPointer);
        window.removeEventListener("pointerdown", onPointerDown);
        window.removeEventListener("pointerup", onPointerUp);
        document.removeEventListener("visibilitychange", onVisibilityChange);
        prefersReducedMotion.removeEventListener("change", onMotionPreferenceChange);
        world.dispose();
        renderer.dispose();
    };

    canvas.addEventListener("webglcontextlost", (event) => {
        event.preventDefault();
        animationActive = false;
        pause();
        showFallback(new Error("WebGL context lost"));
    }, { once: true });

    window.addEventListener("resize", resize, { passive: true });
    window.addEventListener("pointermove", onPointerMove, { passive: true });
    window.addEventListener("touchmove", onTouchMove, { passive: true });
    window.addEventListener("pointerleave", resetPointer, { passive: true });
    window.addEventListener("pointerdown", onPointerDown, { passive: true });
    window.addEventListener("pointerup", onPointerUp, { passive: true });
    document.addEventListener("visibilitychange", onVisibilityChange);
    prefersReducedMotion.addEventListener("change", onMotionPreferenceChange);
    window.addEventListener("pagehide", dispose, { once: true });

    resize();
    renderer.render(scene, camera);
    window.clearInterval(loadingTimer);
    if (loaderValue) loaderValue.textContent = "100";
    if (loaderTrack) loaderTrack.style.width = "100%";
    stage.dataset.renderState = "ready";
    window.requestAnimationFrame(() => stage.classList.add("procedural-ready"));
    resume();
}

function signed(value) {
    return `${value >= 0 ? "+" : ""}${value.toFixed(3)}`;
}

function formatRuntime(elapsed) {
    const totalSeconds = Math.max(0, Math.floor(elapsed));
    const hours = String(Math.floor(totalSeconds / 3600)).padStart(2, "0");
    const minutes = String(Math.floor((totalSeconds % 3600) / 60)).padStart(2, "0");
    const seconds = String(totalSeconds % 60).padStart(2, "0");
    return `${hours}:${minutes}:${seconds}`;
}

function showFallback(error) {
    stage.classList.add("procedural-failed", "procedural-ready");
    stage.dataset.renderState = "fallback";
    if (errorMessage) {
        errorMessage.textContent = "STATIC CORE / GRAPHICS FALLBACK ACTIVE";
    }
    console.warn("Procedural core fallback activated", error);
}
