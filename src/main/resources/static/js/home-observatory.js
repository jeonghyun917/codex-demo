import { createScrollController } from "./home-observatory-scroll.js";
import {
    createDomMotion,
    createPageLifecycle,
    normalizePointer,
    normalizeScrollProgress,
    resolveLocalTestOverrides,
    selectHomeQualityProfile
} from "./home-observatory-motion.js";
import { createQuantCoreScene } from "./home-observatory-scene.js";

const root = document.documentElement;
const story = document.querySelector("[data-home-story]");
const sceneElement = document.querySelector("[data-home-scene]");
const canvas = document.querySelector("[data-home-canvas]");
const chapters = [...document.querySelectorAll("[data-home-chapter]")];
const revealElements = [...document.querySelectorAll("[data-home-reveal]")];
const overrides = resolveLocalTestOverrides({
    hostname: window.location.hostname,
    search: window.location.search
});
const environment = {
    reduceMotion: overrides.forceReduced
        || window.matchMedia("(prefers-reduced-motion: reduce)").matches,
    mobile: window.matchMedia("(max-width: 767px)").matches,
    tablet: window.matchMedia("(min-width: 768px) and (max-width: 1024px)").matches,
    cores: navigator.hardwareConcurrency || 4,
    dpr: window.devicePixelRatio || 1,
    webgl: !overrides.forceStatic && detectWebGLSupport()
};
const profile = selectHomeQualityProfile(environment);
const isLoopback = ["127.0.0.1", "localhost", "::1"].includes(window.location.hostname);

function detectWebGLSupport() {
    const probe = document.createElement("canvas");
    return Boolean(probe.getContext("webgl2") || probe.getContext("webgl"));
}

if (story && sceneElement && canvas) {
    await startObservatory();
} else {
    root.classList.add("home-webgl-failed");
}

async function startObservatory() {
    let storyVisible = false;
    let pageHidden = document.hidden;
    let activeChapter = "";
    const renderTimes = [];
    const canAnimate = !environment.reduceMotion
        && Boolean(window.Motion?.animate)
        && "IntersectionObserver" in window;

    root.classList.toggle("home-motion-enabled", canAnimate);

    const domMotion = createDomMotion({
        MotionAPI: window.Motion,
        root,
        reduceMotion: environment.reduceMotion
    });
    const scroll = createScrollController({
        LenisClass: window.Lenis,
        reduceMotion: environment.reduceMotion,
        onScroll: updateStoryProgress
    });

    let scene = null;
    try {
        scene = profile.webgl
            ? await createQuantCoreScene({ canvas, profile })
            : null;
        root.classList.toggle("home-webgl-ready", Boolean(scene));
        root.classList.toggle("home-webgl-failed", !scene);
    } catch {
        root.classList.add("home-webgl-failed");
    }

    const storyObserver = "IntersectionObserver" in window
        ? new IntersectionObserver(([entry]) => {
            storyVisible = Boolean(entry?.isIntersecting);
            root.classList.toggle("home-scene-visible", storyVisible);
            updateSceneVisibility();
            if (storyVisible) {
                updateStoryProgress();
            }
        }, { threshold: 0.01 })
        : null;

    if (storyObserver) {
        storyObserver.observe(story);
    } else {
        storyVisible = true;
        root.classList.add("home-scene-visible");
    }

    const revealObserver = canAnimate
        ? new IntersectionObserver((entries, observer) => {
            entries.forEach((entry) => {
                if (!entry.isIntersecting) {
                    return;
                }
                domMotion.reveal(entry.target);
                observer.unobserve(entry.target);
            });
        }, { threshold: 0.14 })
        : null;

    if (revealObserver) {
        revealElements.forEach((element) => revealObserver.observe(element));
    } else {
        revealElements.forEach((element) => domMotion.reveal(element));
    }

    function updateStoryProgress() {
        if (!storyVisible) {
            return;
        }
        const rect = story.getBoundingClientRect();
        const progress = normalizeScrollProgress(rect.top, rect.height, window.innerHeight);
        scene?.setProgress(progress);

        const chapterIndex = progress < 0.34 ? 0 : progress < 0.68 ? 1 : 2;
        const chapter = chapters[chapterIndex]?.dataset.homeChapter || "";
        if (chapter !== activeChapter) {
            activeChapter = chapter;
            domMotion.setChapter(chapter);
        }
    }

    function updateSceneVisibility() {
        scene?.setVisible(storyVisible && !pageHidden);
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
        canvas.dataset.renderMs = average.toFixed(2);
        canvas.dataset.drawCalls = String(scene.getDiagnostics().drawCalls);
    }

    function tick(time) {
        scroll.raf(time);
        if (storyVisible && !pageHidden) {
            updateStoryProgress();
        }
        const startedAt = isLoopback ? performance.now() : 0;
        const rendered = scene?.render(time) || false;
        recordLocalDiagnostics(startedAt, rendered);
    }

    function handlePointer(event) {
        if (!scene || !profile.pointerEnabled) {
            return;
        }
        scene.setPointer(normalizePointer(
            event.clientX,
            event.clientY,
            sceneElement.getBoundingClientRect()
        ));
    }

    function handleVisibilityChange() {
        pageHidden = document.hidden;
        if (pageHidden) {
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

    function pauseForPageCache() {
        scroll.stop();
        scene?.setVisible(false);
    }

    function resumeFromPageCache() {
        pageHidden = document.hidden;
        if (!pageHidden) {
            scroll.start();
        }
        scene?.resize();
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
        if (profile.pointerEnabled) {
            sceneElement.removeEventListener("pointermove", handlePointer);
        }
    }

    const lifecycle = createPageLifecycle({
        requestFrame: (callback) => requestAnimationFrame(callback),
        cancelFrame: (id) => cancelAnimationFrame(id),
        onFrame: tick,
        onPause: pauseForPageCache,
        onResume: resumeFromPageCache,
        onDestroy: destroy
    });

    if (profile.pointerEnabled) {
        sceneElement.addEventListener("pointermove", handlePointer, { passive: true });
    }
    document.addEventListener("visibilitychange", handleVisibilityChange);
    window.addEventListener("resize", handleResize, { passive: true });
    window.addEventListener("pagehide", lifecycle.handlePageHide);
    window.addEventListener("pageshow", lifecycle.handlePageShow);

    updateSceneVisibility();
    updateStoryProgress();
    lifecycle.start();
}
