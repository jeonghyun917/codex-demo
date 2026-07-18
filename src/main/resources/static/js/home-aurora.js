import { createAuroraField } from "./home-aurora-field.js";
import {
    createSpring,
    normalizedScrollProgress,
    smoothstep,
    stepSpring
} from "./home-aurora-motion.js";
import { readEnvironment, selectAuroraQuality } from "./home-aurora-quality.js";

const THREE_MODULE_URL = "/js/vendor/three.module.js";
const POINTER_ENERGY_SPRING = Object.freeze({ stiffness: 70, damping: 18, mass: 1 });
const SCROLL_SPRING = Object.freeze({ stiffness: 82, damping: 22, mass: 1 });
const stage = document.querySelector("[data-aurora-core]");
const canvas = document.querySelector("[data-aurora-canvas]");

if (stage && canvas) {
    startAuroraCore(stage, canvas).catch(() => {
        document.body.classList.add("is-webgl-fallback");
        stage.classList.add("is-ready", "has-metrics", "is-resolved");
    });
}

async function startAuroraCore(root, targetCanvas) {
    const THREE = await import(THREE_MODULE_URL);
    const quality = selectAuroraQuality(readEnvironment());
    const renderer = new THREE.WebGLRenderer({
        canvas: targetCanvas,
        antialias: false,
        alpha: true,
        powerPreference: quality.name === "low" ? "default" : "high-performance"
    });
    renderer.setClearColor(0x02040b, 0);
    renderer.outputColorSpace = THREE.SRGBColorSpace;
    renderer.toneMapping = THREE.ACESFilmicToneMapping;
    renderer.toneMappingExposure = 1;

    const scene = new THREE.Scene();
    const camera = new THREE.Camera();
    const field = createAuroraField(THREE, quality);
    scene.add(field.mesh);

    const metrics = Array.from(root.querySelectorAll("[data-aurora-metric]"));
    const progressBar = root.querySelector("[data-aurora-progress] span");
    const summaryCopy = root.querySelector(".aurora-summary");
    const resolvedCopy = root.querySelector(".aurora-resolved-copy");
    const pointerX = createSpring(0.5);
    const pointerY = createSpring(0.5);
    const pointerEnergy = createSpring(0);
    const initialProgress = quality.name === "reduced" ? 1 : 0;
    const scrollSpring = createSpring(initialProgress);
    let staticFrameRendered = false;
    let resolvedState = true;
    const state = {
        width: 0,
        height: 0,
        intersecting: true,
        running: false,
        animationFrame: 0,
        previousTime: performance.now(),
        elapsed: 0,
        pointerSampleX: window.innerWidth * 0.5,
        pointerSampleY: window.innerHeight * 0.5
    };

    const resize = () => {
        const rect = targetCanvas.getBoundingClientRect();
        const width = Math.max(1, Math.round(rect.width));
        const height = Math.max(1, Math.round(rect.height));
        if (width === state.width && height === state.height) {
            return false;
        }
        state.width = width;
        state.height = height;
        renderer.setPixelRatio(quality.pixelRatio);
        renderer.setSize(width, height, false);
        field.resize(width * quality.pixelRatio, height * quality.pixelRatio);
        return true;
    };

    const readScroll = () => {
        const rect = root.getBoundingClientRect();
        scrollSpring.target = quality.name === "reduced"
            ? 1
            : normalizedScrollProgress(rect.top, rect.height, window.innerHeight);
    };

    const readPointer = (event) => {
        if (!quality.pointerEnabled) {
            return;
        }
        const nextX = event.clientX / Math.max(1, window.innerWidth);
        const nextY = 1 - event.clientY / Math.max(1, window.innerHeight);
        const speed = Math.hypot(
            event.clientX - state.pointerSampleX,
            event.clientY - state.pointerSampleY
        );
        state.pointerSampleX = event.clientX;
        state.pointerSampleY = event.clientY;
        pointerX.target = nextX;
        pointerY.target = nextY;
        pointerEnergy.target = Math.min(1, 0.16 + speed / 54);
    };

    const clearPointer = () => {
        pointerX.target = 0.5;
        pointerY.target = 0.5;
        pointerEnergy.target = 0;
    };

    const projectInterfaceState = (progress) => {
        root.style.setProperty("--aurora-progress", progress.toFixed(4));
        root.style.setProperty("--pointer-x", `${(pointerX.value * 100).toFixed(2)}%`);
        root.style.setProperty("--pointer-y", `${((1 - pointerY.value) * 100).toFixed(2)}%`);
        root.classList.toggle("has-metrics", progress >= 0.24);
        const nextResolvedState = progress >= 0.74;
        if (nextResolvedState !== resolvedState) {
            root.classList.toggle("is-resolved", nextResolvedState);
            if (summaryCopy) {
                summaryCopy.setAttribute("aria-hidden", String(nextResolvedState));
            }
            if (resolvedCopy) {
                resolvedCopy.setAttribute("aria-hidden", String(!nextResolvedState));
            }
            resolvedState = nextResolvedState;
        }
        for (let index = 0; index < metrics.length; index += 1) {
            const metric = metrics[index];
            metric.classList.toggle("is-visible", progress >= 0.28 + index * 0.08);
        }
        if (progressBar) {
            progressBar.style.transform = `scaleY(${progress.toFixed(4)})`;
        }
    };

    const stopFrameLoop = () => {
        if (state.animationFrame) {
            cancelAnimationFrame(state.animationFrame);
        }
        state.animationFrame = 0;
        state.running = false;
    };

    const ensureFrameLoop = () => {
        if (state.running || !state.intersecting || document.hidden
            || (quality.name === "reduced" && staticFrameRendered)) {
            return;
        }
        state.running = true;
        state.previousTime = performance.now();
        state.animationFrame = requestAnimationFrame(frame);
    };

    const synchronizePlayback = () => {
        if (state.intersecting && !document.hidden) {
            ensureFrameLoop();
        } else {
            stopFrameLoop();
        }
    };

    const frame = (now) => {
        if (!state.intersecting || document.hidden) {
            stopFrameLoop();
            return;
        }
        const deltaSeconds = Math.min((now - state.previousTime) / 1000, 1 / 20);
        state.previousTime = now;
        state.elapsed += deltaSeconds * quality.ambientSpeed;
        pointerEnergy.target *= Math.exp(-deltaSeconds * 5);
        stepSpring(pointerX, deltaSeconds);
        stepSpring(pointerY, deltaSeconds);
        stepSpring(pointerEnergy, deltaSeconds, POINTER_ENERGY_SPRING);
        stepSpring(scrollSpring, deltaSeconds, SCROLL_SPRING);

        const scroll = smoothstep(0, 1, scrollSpring.value);
        field.update(
            state.elapsed,
            pointerX.value,
            pointerY.value,
            pointerEnergy.value,
            scroll
        );
        projectInterfaceState(scroll);
        renderer.render(scene, camera);

        if (quality.name === "reduced") {
            staticFrameRendered = true;
            stopFrameLoop();
            return;
        }
        state.animationFrame = requestAnimationFrame(frame);
    };

    const observer = new IntersectionObserver((entries) => {
        const latestEntry = entries[entries.length - 1];
        if (!latestEntry) {
            return;
        }
        state.intersecting = latestEntry.isIntersecting;
        synchronizePlayback();
    }, { threshold: 0.01 });
    observer.observe(root);

    window.addEventListener("resize", () => {
        const resized = resize();
        readScroll();
        if (resized && quality.name === "reduced") {
            staticFrameRendered = false;
        }
        ensureFrameLoop();
    }, { passive: true });
    window.addEventListener("scroll", readScroll, { passive: true });
    window.addEventListener("pointermove", readPointer, { passive: true });
    window.addEventListener("pointerleave", clearPointer, { passive: true });
    document.addEventListener("visibilitychange", synchronizePlayback);

    resize();
    readScroll();
    root.classList.add("is-ready");
    projectInterfaceState(initialProgress);
    ensureFrameLoop();
}
