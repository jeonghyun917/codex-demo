import {
    createHeroIntroState,
    createHeroPose,
    createPageLifecycle,
    selectPlanetQualityProfile
} from "./home-quant-planet-motion.js";
import {
    createHeroInputController
} from "./home-quant-planet-input.js";
import {
    REFERENCE_HERO
} from "./home-quant-planet-composition.js";

export function resolveQuantPlanetCapabilities({
    forceReduced = false,
    forceStatic = false,
    detectedReducedMotion = false,
    detectedWebgl = false,
    motionAvailable = false,
    mobile = false,
    cores = 4,
    dpr = 1
} = {}) {
    const environment = {
        reduceMotion: forceReduced || detectedReducedMotion,
        mobile,
        cores,
        dpr,
        webgl: detectedWebgl && !forceStatic && motionAvailable
    };
    const profile = selectPlanetQualityProfile(environment);
    return {
        environment,
        profile,
        staticMode: profile.name === "static",
        motionDisabled: environment.reduceMotion
            || profile.name === "static"
    };
}

export function createQuantPlanetRuntime({
    root,
    experience,
    loader,
    sceneElement,
    canvas,
    fallback,
    title,
    action,
    profile,
    motionDisabled = profile?.name === "static",
    isLoopback = false,
    createScene,
    createInput = createHeroInputController,
    requestFrame,
    cancelFrame,
    now = () => 0,
    documentTarget,
    windowTarget
} = {}) {
    const staticMode = motionDisabled
        || profile?.name === "static"
        || !profile?.webgl;
    const startedAt = now();
    const renderTimes = [];
    const motionAnimations = new Set();
    let activeElapsedMs = 0;
    let destroyed = false;
    let inputController = null;
    let inputState = {
        ...DEFAULT_INPUT_STATE,
        pointer: { ...DEFAULT_INPUT_STATE.pointer }
    };
    let introDeadlineId = null;
    let introElapsedMs = 0;
    let introReady = false;
    let lastFrameTime = startedAt;
    let lifecycle = null;
    let runtimeFailed = false;
    let scene = null;
    let sceneReady = false;
    let worldAngle = 0;

    root.classList.add("planet-runtime-starting");

    function applyIntroState(state) {
        root.dataset.introPhase = state.phase;
        root.style.setProperty(
            "--intro-world-progress",
            String(state.world)
        );
        root.style.setProperty(
            "--intro-title-progress",
            String(state.title)
        );
        root.style.setProperty(
            "--intro-action-progress",
            String(state.action)
        );
    }

    function cancelMotionAnimations() {
        motionAnimations.forEach((animation) => {
            try {
                animation?.cancel?.();
            } catch {
                // CSS progress still reaches the complete static state.
            }
        });
        motionAnimations.clear();
    }

    function clearIntroDeadline() {
        if (introDeadlineId === null) {
            return;
        }
        windowTarget?.clearTimeout?.(introDeadlineId);
        introDeadlineId = null;
    }

    function revealFinal({ failed = false } = {}) {
        if (!introReady) {
            introReady = true;
            applyIntroState(createHeroIntroState({
                elapsedMs: REFERENCE_HERO.introDeadlineMs,
                sceneReady: true,
                reduceMotion: true
            }));
        }
        clearIntroDeadline();
        cancelMotionAnimations();
        root.classList.remove("planet-runtime-starting");
        root.classList.add("planet-intro-ready");
        if (failed) {
            root.classList.remove("planet-webgl-ready");
            root.classList.add("planet-webgl-failed");
        }
    }

    function scheduleIntroDeadline() {
        clearIntroDeadline();
        if (
            introReady
            || destroyed
            || typeof windowTarget?.setTimeout !== "function"
        ) {
            return;
        }
        const remaining = Math.max(
            0,
            startedAt + REFERENCE_HERO.introDeadlineMs - now()
        );
        if (remaining === 0) {
            revealFinal({ failed: !sceneReady });
            return;
        }
        introDeadlineId = windowTarget.setTimeout(() => {
            introDeadlineId = null;
            revealFinal({ failed: !sceneReady });
        }, remaining);
    }

    function startLoaderMotion() {
        if (staticMode || typeof windowTarget?.Motion?.animate !== "function") {
            return;
        }
        try {
            const animation = windowTarget.Motion.animate(loader, {
                transform: [
                    "scale(1)",
                    "scale(1.035)",
                    "scale(1)"
                ]
            }, {
                duration: .9,
                easing: "ease-in-out",
                repeat: Infinity
            });
            motionAnimations.add(animation);
        } catch {
            // CSS owns the loader if Motion cannot construct an animation.
        }
    }

    function disposeInput() {
        const activeInput = inputController;
        inputController = null;
        try {
            activeInput?.destroy?.();
        } catch {
            // Continue GPU and lifecycle cleanup after input teardown.
        }
    }

    function disposeScene() {
        const activeScene = scene;
        scene = null;
        sceneReady = false;
        try {
            activeScene?.dispose?.();
        } catch {
            // Continue listener and RAF cleanup after GPU teardown.
        }
    }

    function pauseRuntime() {
        lastFrameTime = null;
        scene?.setVisible?.(false);
    }

    function resumeRuntime() {
        if (destroyed || runtimeFailed) {
            return;
        }
        lastFrameTime = null;
        scene?.resize?.();
        scene?.setVisible?.(!documentTarget.hidden);
    }

    function removeListeners() {
        documentTarget.removeEventListener(
            "visibilitychange",
            handleVisibilityChange
        );
        windowTarget.removeEventListener("resize", handleResize);
        windowTarget.removeEventListener("pagehide", handlePageHide);
        windowTarget.removeEventListener("pageshow", handlePageShow);
        canvas.removeEventListener(
            "webglcontextlost",
            handleContextLost
        );
    }

    function destroyRuntime() {
        if (destroyed) {
            return;
        }
        destroyed = true;
        clearIntroDeadline();
        cancelMotionAnimations();
        disposeInput();
        disposeScene();
        removeListeners();
    }

    function recordLocalDiagnostics(startedRenderAt, rendered) {
        if (!isLoopback || !rendered || !scene) {
            return;
        }
        renderTimes.push(now() - startedRenderAt);
        if (renderTimes.length > 60) {
            renderTimes.shift();
        }
        const average = renderTimes.reduce(
            (sum, value) => sum + value,
            0
        ) / renderTimes.length;
        const diagnostics = scene.getDiagnostics();
        canvas.dataset.renderMs = average.toFixed(2);
        canvas.dataset.drawCalls = String(diagnostics.drawCalls);
        canvas.dataset.triangles = String(diagnostics.triangles);
    }

    function updateIntro(deltaTime) {
        if (introReady) {
            return;
        }
        if (sceneReady) {
            introElapsedMs += deltaTime;
        } else {
            introElapsedMs = Math.min(600, introElapsedMs + deltaTime);
        }
        const state = createHeroIntroState({
            elapsedMs: introElapsedMs,
            sceneReady,
            reduceMotion: false
        });
        applyIntroState(state);
        if (state.ready) {
            revealFinal();
        }
    }

    function tick(time) {
        if (destroyed || runtimeFailed) {
            return;
        }
        const frameTime = Number.isFinite(time) ? time : now();
        const deltaTime = lastFrameTime === null
            ? 0
            : Math.max(0, frameTime - lastFrameTime);
        lastFrameTime = frameTime;
        activeElapsedMs += deltaTime;
        updateIntro(deltaTime);

        const incrementalPose = createHeroPose({
            elapsedMs: deltaTime,
            worldAngle,
            dragAngle: inputState.dragAngle,
            pointer: inputState.pointer,
            reduceMotion: false
        });
        worldAngle = incrementalPose.worldAngle;
        const pose = {
            ...incrementalPose,
            elapsedMs: activeElapsedMs
        };
        const startedRenderAt = isLoopback ? now() : 0;
        try {
            const rendered = scene?.render?.(pose) || false;
            recordLocalDiagnostics(startedRenderAt, rendered);
        } catch {
            failRuntime();
        }
    }

    lifecycle = createPageLifecycle({
        requestFrame,
        cancelFrame,
        onFrame: tick,
        onPause: pauseRuntime,
        onResume: resumeRuntime,
        onDestroy: destroyRuntime
    });

    function failRuntime() {
        if (runtimeFailed || destroyed) {
            return;
        }
        runtimeFailed = true;
        clearIntroDeadline();
        cancelMotionAnimations();
        lifecycle.pause();
        disposeInput();
        disposeScene();
        revealFinal({ failed: true });
    }

    function handleContextLost(event) {
        event?.preventDefault?.();
        failRuntime();
    }

    function handleVisibilityChange() {
        if (documentTarget.hidden) {
            lifecycle.pause();
        } else if (!runtimeFailed) {
            lifecycle.resume();
        }
    }

    function handlePageHide(event) {
        if (event?.persisted) {
            clearIntroDeadline();
        }
        lifecycle.handlePageHide(event);
    }

    function handlePageShow(event) {
        lifecycle.handlePageShow(event);
        if (event?.persisted) {
            scheduleIntroDeadline();
        }
    }

    function handleResize() {
        scene?.resize?.();
    }

    documentTarget.addEventListener(
        "visibilitychange",
        handleVisibilityChange
    );
    windowTarget.addEventListener("resize", handleResize, {
        passive: true
    });
    windowTarget.addEventListener("pagehide", handlePageHide);
    windowTarget.addEventListener("pageshow", handlePageShow);
    canvas.addEventListener("webglcontextlost", handleContextLost);

    applyIntroState(createHeroIntroState({
        elapsedMs: 0,
        sceneReady: false,
        reduceMotion: staticMode
    }));

    if (staticMode) {
        revealFinal({ failed: true });
    } else {
        startLoaderMotion();
        inputController = createInput({
            surface: canvas,
            windowTarget,
            onStateChange(state) {
                inputState = {
                    ...DEFAULT_INPUT_STATE,
                    ...state,
                    pointer: {
                        ...DEFAULT_INPUT_STATE.pointer,
                        ...state?.pointer
                    }
                };
            }
        });
        scheduleIntroDeadline();
        lifecycle.start();
        if (documentTarget.hidden) {
            lifecycle.pause();
        }
    }

    let scenePromise = null;
    if (!staticMode) {
        try {
            scenePromise = Promise.resolve(createScene({
                canvas,
                profile
            }));
        } catch (error) {
            scenePromise = Promise.reject(error);
        }
    }

    scenePromise?.then((createdScene) => {
        if (destroyed || runtimeFailed) {
            try {
                createdScene?.dispose?.();
            } catch {
                // A retired scene cannot block runtime cleanup.
            }
            return;
        }
        scene = createdScene;
        sceneReady = true;
        scene.resize?.();
        scene.setVisible?.(!documentTarget.hidden);
        root.classList.remove("planet-webgl-failed");
        root.classList.add("planet-webgl-ready");
    }).catch(() => {
        failRuntime();
    });

    return {
        destroy() {
            lifecycle.destroy();
        }
    };
}

const DEFAULT_INPUT_STATE = Object.freeze({
    pointer: Object.freeze({ x: 0, y: 0 }),
    dragAngle: 0,
    dragging: false,
    settling: false
});
