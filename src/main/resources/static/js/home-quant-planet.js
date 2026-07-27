import {
    resolveLocalTestOverrides
} from "./home-quant-planet-motion.js";
import {
    createQuantPlanetRuntime,
    resolveQuantPlanetCapabilities
} from "./home-quant-planet-runtime.js";
import {
    createQuantPlanetScene
} from "./home-quant-planet-scene.js?v=2026072801";

const LOOPBACK_HOSTS = new Set([
    "localhost",
    "127.0.0.1",
    "::1",
    "[::1]"
]);
const root = document.documentElement;
const experience = document.querySelector("[data-home-experience]");
const loader = document.querySelector("[data-intro-loader]");
const sceneElement = document.querySelector("[data-home-scene]");
const canvas = document.querySelector("[data-home-canvas]");
const fallback = document.querySelector("[data-planet-fallback]");
const title = document.querySelector("[data-reference-title]");
const action = document.querySelector("[data-enter-quant]");
const actionLocation = window.location;
action?.addEventListener("keydown", (event) => {
    if (event.key !== "Enter") {
        return;
    }
    event.preventDefault();
    actionLocation.assign("/quant");
});
const overrides = resolveLocalTestOverrides({
    hostname: window.location.hostname,
    search: window.location.search
});
const capabilities = resolveQuantPlanetCapabilities({
    forceReduced: overrides.forceReduced,
    forceStatic: overrides.forceStatic,
    detectedReducedMotion: window
        .matchMedia("(prefers-reduced-motion: reduce)").matches,
    detectedWebgl: detectWebglSupport(),
    motionAvailable: Boolean(window.Motion?.animate),
    mobile: window.matchMedia("(max-width: 767px)").matches,
    cores: navigator.hardwareConcurrency || 4,
    dpr: window.devicePixelRatio || 1,
    viewportWidth: window.innerWidth,
    viewportHeight: window.innerHeight
});

function detectWebglSupport() {
    try {
        const probe = document.createElement("canvas");
        return Boolean(
            probe.getContext("webgl2")
            || probe.getContext("webgl")
        );
    } catch {
        return false;
    }
}

function revealStaticComposition() {
    experience.style.setProperty("--intro-world-progress", "1");
    experience.style.setProperty("--intro-title-progress", "1");
    experience.style.setProperty("--intro-action-progress", "1");
    root.classList.remove(
        "planet-runtime-starting",
        "planet-webgl-ready"
    );
    root.classList.add("planet-intro-ready", "planet-webgl-failed");
}

const selectorsAreValid = Boolean(
    experience
    && loader
    && sceneElement
    && canvas
    && fallback
    && title
    && action
);

if (selectorsAreValid) {
    createQuantPlanetRuntime({
        root,
        experience,
        loader,
        sceneElement,
        canvas,
        fallback,
        title,
        action,
        profile: capabilities.profile,
        rearProfile: capabilities.rearProfile,
        motionDisabled: capabilities.motionDisabled,
        isLoopback: LOOPBACK_HOSTS.has(window.location.hostname),
        createScene: createQuantPlanetScene,
        requestFrame: (callback) => requestAnimationFrame(callback),
        cancelFrame: (id) => cancelAnimationFrame(id),
        now: () => performance.now(),
        documentTarget: document,
        windowTarget: window
    });
} else {
    revealStaticComposition();
}
