import {
    resolveLocalTestOverrides
} from "./home-quant-planet-motion.js";
import {
    createQuantPlanetRuntime,
    resolveQuantPlanetCapabilities
} from "./home-quant-planet-runtime.js";
import {
    createQuantPlanetScene
} from "./home-quant-planet-scene.js";

const LOOPBACK_HOSTS = new Set(["localhost", "127.0.0.1", "::1", "[::1]"]);
const root = document.documentElement;
const experience = document.querySelector("[data-home-experience]");
const sceneElement = document.querySelector("[data-home-scene]");
const canvas = document.querySelector("[data-home-canvas]");
const loader = document.querySelector("[data-intro-loader]");
const introMark = loader?.querySelector("[data-intro-mark]");
const productLinks = [
    ...document.querySelectorAll("[data-product-orbit-link]")
];
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
    dpr: window.devicePixelRatio || 1
});

function detectWebglSupport() {
    try {
        const probe = document.createElement("canvas");
        return Boolean(probe.getContext("webgl2") || probe.getContext("webgl"));
    } catch {
        return false;
    }
}

const selectorsAreValid = Boolean(
    experience
    && sceneElement
    && canvas
    && loader
    && productLinks.length === 6
);

if (selectorsAreValid) {
    createQuantPlanetRuntime({
        root,
        experience,
        canvas,
        loader,
        introMark,
        productLinks,
        profile: capabilities.profile,
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
    root.classList.remove("planet-runtime-starting");
    root.classList.add("planet-intro-ready", "planet-webgl-failed");
    if (loader?.style) {
        loader.style.opacity = "0";
    }
}
