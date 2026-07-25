import {
    createDomMotion,
    createPageLifecycle,
    normalizePointer,
    normalizeScrollProgress,
    resolveLocalTestOverrides,
    resolvePlanetChapter,
    selectPlanetQualityProfile
} from "./home-quant-planet-motion.js";
import { createScrollController } from "./home-quant-planet-scroll.js";
import { createQuantPlanetScene } from "./home-quant-planet-scene.js";

const LOOPBACK_HOSTS = new Set(["localhost", "127.0.0.1", "::1", "[::1]"]);
const root = document.documentElement;
const story = document.querySelector("[data-home-story]");
const sceneElement = document.querySelector("[data-home-scene]");
const canvas = document.querySelector("[data-home-canvas]");
const revealElements = [...document.querySelectorAll("[data-home-reveal]")];
const overrides = resolveLocalTestOverrides({
    hostname: window.location.hostname,
    search: window.location.search
});
const detectedReducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
const detectedWebgl = detectWebglSupport();
const environment = {
    reduceMotion: overrides.forceReduced || detectedReducedMotion,
    mobile: window.matchMedia("(max-width: 767px)").matches,
    cores: navigator.hardwareConcurrency || 4,
    dpr: window.devicePixelRatio || 1,
    webgl: detectedWebgl && !overrides.forceStatic
};
const profile = selectPlanetQualityProfile(environment);
const staticMode = profile.name === "static";
const motionDisabled = environment.reduceMotion || staticMode;
const isLoopback = LOOPBACK_HOSTS.has(window.location.hostname);

function detectWebglSupport() {
    try {
        const probe = document.createElement("canvas");
        return Boolean(probe.getContext("webgl2") || probe.getContext("webgl"));
    } catch {
        return false;
    }
}

if (story && sceneElement && canvas) {
    await startQuantPlanet();
} else {
    root.classList.add("planet-webgl-failed");
}

async function startQuantPlanet() {
    let storyVisible = false;
    let scene = null;
    let storyObserver = null;
    let revealObserver = null;
    const renderTimes = [];
    const canAnimate = !motionDisabled
        && Boolean(window.Motion?.animate)
        && "IntersectionObserver" in window;

    root.classList.toggle("planet-motion-enabled", canAnimate);

    const domMotion = createDomMotion({
        MotionAPI: window.Motion,
        root,
        reduceMotion: motionDisabled || !canAnimate
    });
    const scroll = createScrollController({
        LenisClass: window.Lenis,
        reduceMotion: motionDisabled,
        onScroll: updateStoryProgress
    });

    try {
        if (profile.webgl) {
            scene = await createQuantPlanetScene({ canvas, profile });
            scene.setVisible(false);
            root.classList.add("planet-webgl-ready");
            root.classList.remove("planet-webgl-failed");
        } else {
            root.classList.add("planet-webgl-failed");
        }
    } catch {
        scene?.dispose();
        scene = null;
        root.classList.remove("planet-motion-enabled");
        root.classList.remove("planet-webgl-ready");
        root.classList.add("planet-webgl-failed");
    }

    function updateStoryProgress() {
        if (!storyVisible) {
            return;
        }
        const rect = story.getBoundingClientRect();
        const progress = normalizeScrollProgress(
            rect.top, rect.height, window.innerHeight
        );
        scene?.setProgress(progress);
        const chapter = resolvePlanetChapter(progress);
        root.dataset.planetChapter = chapter;
        domMotion.setChapter(chapter);
    }

    function updateSceneVisibility() {
        scene?.setVisible(storyVisible && !document.hidden);
    }

    function handlePointer(event) {
        if (!profile.pointerEnabled) {
            return;
        }
        scene?.setPointer(normalizePointer(
            event.clientX,
            event.clientY,
            sceneElement.getBoundingClientRect()
        ));
    }

    function handleVisibilityChange() {
        if (document.hidden) {
            scroll.stop();
        } else {
            scroll.start();
            updateStoryProgress();
        }
        updateSceneVisibility();
    }

    function handleResize() {
        scene?.resize();
        updateStoryProgress();
    }

    function recordLocalDiagnostics(startedAt, rendered) {
        if (!isLoopback || !rendered || !scene) {
            return;
        }
        renderTimes.push(performance.now() - startedAt);
        if (renderTimes.length > 60) {
            renderTimes.shift();
        }
        const average = renderTimes.reduce((sum, value) => sum + value, 0) / renderTimes.length;
        const diagnostics = scene.getDiagnostics();
        canvas.dataset.renderMs = average.toFixed(2);
        canvas.dataset.drawCalls = String(diagnostics.drawCalls);
        canvas.dataset.triangles = String(diagnostics.triangles);
    }

    function tick(time) {
        scroll.raf(time);
        if (storyVisible && !document.hidden) {
            updateStoryProgress();
        }
        const startedAt = isLoopback ? performance.now() : 0;
        const rendered = scene?.render(time) || false;
        recordLocalDiagnostics(startedAt, rendered);
    }

    function pauseForPageCache() {
        scroll.stop();
        scene?.setVisible(false);
    }

    function resumeFromPageCache() {
        scene?.resize();
        if (!document.hidden) {
            scroll.start();
        }
        updateSceneVisibility();
        updateStoryProgress();
    }

    function destroy() {
        storyObserver?.disconnect();
        revealObserver?.disconnect();
        scroll.destroy();
        domMotion.destroy();
        scene?.dispose();
        document.removeEventListener("visibilitychange", handleVisibilityChange);
        window.removeEventListener("resize", handleResize);
        window.removeEventListener("pagehide", lifecycle.handlePageHide);
        window.removeEventListener("pageshow", lifecycle.handlePageShow);
        sceneElement.removeEventListener("pointermove", handlePointer);
    }

    const lifecycle = createPageLifecycle({
        requestFrame: (callback) => requestAnimationFrame(callback),
        cancelFrame: (id) => cancelAnimationFrame(id),
        onFrame: tick,
        onPause: pauseForPageCache,
        onResume: resumeFromPageCache,
        onDestroy: destroy
    });

    storyObserver = "IntersectionObserver" in window
        ? new IntersectionObserver(([entry]) => {
            storyVisible = Boolean(entry?.isIntersecting);
            updateSceneVisibility();
            if (storyVisible) {
                updateStoryProgress();
            }
        }, { threshold: .01 })
        : null;
    if (storyObserver) {
        storyObserver.observe(story);
    } else {
        storyVisible = true;
    }

    revealObserver = canAnimate
        ? new IntersectionObserver((entries, observer) => {
            entries.forEach((entry) => {
                if (!entry.isIntersecting) {
                    return;
                }
                domMotion.reveal(entry.target);
                observer.unobserve(entry.target);
            });
        }, { threshold: .14 })
        : null;
    if (revealObserver) {
        revealElements.forEach((element) => revealObserver.observe(element));
    } else {
        revealElements.forEach((element) => domMotion.reveal(element));
    }

    if (scene && profile.pointerEnabled) {
        sceneElement.addEventListener("pointermove", handlePointer, { passive: true });
    }
    document.addEventListener("visibilitychange", handleVisibilityChange);
    window.addEventListener("resize", handleResize, { passive: true });
    window.addEventListener("pagehide", lifecycle.handlePageHide);
    window.addEventListener("pageshow", lifecycle.handlePageShow);

    updateSceneVisibility();
    updateStoryProgress();
    if (scene) {
        scene.resize();
        lifecycle.start();
    }
}
