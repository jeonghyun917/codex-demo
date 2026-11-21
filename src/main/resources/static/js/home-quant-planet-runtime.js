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

const REAR_DENSITY_PROFILES = Object.freeze({
    high: Object.freeze({ name: "high" }),
    balanced: Object.freeze({ name: "balanced" }),
    low: Object.freeze({ name: "low" })
});

export function selectRearFieldProfile({
    qualityProfile = {},
    viewportWidth,
    viewportHeight
} = {}) {
    const width = Number.isFinite(viewportWidth)
        ? Math.max(1, viewportWidth)
        : Number.POSITIVE_INFINITY;
    const height = Number.isFinite(viewportHeight)
        ? Math.max(1, viewportHeight)
        : 0;
    const viewportProfile = width <= 480
        ? REAR_DENSITY_PROFILES.low
        : height > width
            ? REAR_DENSITY_PROFILES.balanced
            : REAR_DENSITY_PROFILES.high;

    if (qualityProfile.name === "static") {
        return REAR_DENSITY_PROFILES.low;
    }
    if (
        qualityProfile.name !== "high"
        && viewportProfile.name === "high"
    ) {
        return REAR_DENSITY_PROFILES.balanced;
    }
    return viewportProfile;
}

export function resolveQuantPlanetCapabilities({
    forceReduced = false,
    forceStatic = false,
    detectedReducedMotion = false,
    detectedWebgl = false,
    motionAvailable = false,
    mobile = false,
    cores = 4,
    dpr = 1,
    viewportWidth,
    viewportHeight
} = {}) {
    const environment = {
        reduceMotion: forceReduced || detectedReducedMotion,
        mobile,
        cores,
        dpr,
        webgl: detectedWebgl && !forceStatic && motionAvailable
    };
    const profile = selectPlanetQualityProfile(environment);
    const rearProfile = selectRearFieldProfile({
        qualityProfile: profile,
        viewportWidth,
        viewportHeight
    });
    return {
        environment,
        profile,
        rearProfile,
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
    rearProfile,
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
    let resolvedRearProfile = rearProfile || selectRearFieldProfile({
        qualityProfile: profile,
        viewportWidth: windowTarget?.innerWidth,
        viewportHeight: windowTarget?.innerHeight
    });
    let sceneConstructionRearProfile = null;
    const staticMode = motionDisabled
        || profile?.name === "static"
        || !profile?.webgl;
    const startedAt = now();
    const renderTimes = [];
    const motionAnimations = new Set();
    let activeElapsedMs = 0;
    let currentIntroState = null;
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
    let ambientFrameTime = null;
    let lifecycle = null;
    let runtimeFailed = false;
    let scene = null;
    let sceneReady = false;
    let resizePending = false;
    let worldAngle = 0;

    root.classList.remove(
        "planet-static-mode",
        "planet-reduced-motion",
        "planet-webgl-ready",
        "planet-webgl-failed"
    );
    root.classList.add("planet-runtime-starting");
    if (staticMode) {
        root.classList.add("planet-static-mode");
    }
    if (motionDisabled) {
        root.classList.add("planet-reduced-motion");
    }

    const actionTabIndex = action.getAttribute?.("tabindex") ?? null;

    function applyActionAvailability(available) {
        if (available) {
            root.classList.remove("planet-action-locked");
        } else {
            root.classList.add("planet-action-locked");
        }
        if (!available) {
            action.setAttribute?.("tabindex", "-1");
        } else if (actionTabIndex === null) {
            action.removeAttribute?.("tabindex");
        } else {
            action.setAttribute?.("tabindex", actionTabIndex);
        }
    }

    function applyIntroState(state) {
        currentIntroState = state;
        root.dataset.introPhase = state.phase;
        experience.style.setProperty(
            "--intro-world-progress",
            String(state.world)
        );
        experience.style.setProperty(
            "--intro-title-progress",
            String(state.title)
        );
        experience.style.setProperty(
            "--intro-action-progress",
            String(state.action)
        );
        applyActionAvailability(state.action > 0);
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
            ambientFrameTime = null;
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
        ambientFrameTime = null;
        scene?.setVisible?.(false);
    }

    function resumeRuntime() {
        if (destroyed || runtimeFailed) {
            return;
        }
        lastFrameTime = null;
        resizePending = false;
        syncRearProfile();
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

    function updateInputState(state) {
        inputState = {
            ...DEFAULT_INPUT_STATE,
            ...state,
            pointer: {
                ...DEFAULT_INPUT_STATE.pointer,
                ...state?.pointer
            }
        };
    }

    function syncRearProfile() {
        const nextProfile = selectRearFieldProfile({
            qualityProfile: profile,
            viewportWidth: windowTarget?.innerWidth,
            viewportHeight: windowTarget?.innerHeight
        });
        if (nextProfile.name === resolvedRearProfile.name) {
            return false;
        }
        resolvedRearProfile = nextProfile;
        scene?.setRearProfile?.(resolvedRearProfile);
        return true;
    }

    function tick(time) {
        if (destroyed || runtimeFailed) {
            return;
        }
        if (resizePending) {
            resizePending = false;
            syncRearProfile();
            scene?.resize?.();
        }
        const frameTime = Number.isFinite(time) ? time : now();
        const deltaTime = lastFrameTime === null
            ? 0
            : Math.max(0, frameTime - lastFrameTime);
        lastFrameTime = frameTime;
        activeElapsedMs += deltaTime;
        updateIntro(deltaTime);

        let ambientDeltaTime = 0;
        if (introReady && sceneReady) {
            if (ambientFrameTime !== null) {
                ambientDeltaTime = Math.max(
                    0,
                    frameTime - ambientFrameTime
                );
            }
            ambientFrameTime = frameTime;
        }
        const incrementalPose = createHeroPose({
            elapsedMs: ambientDeltaTime,
            worldAngle,
            pointer: inputState.pointer,
            titleProgress: currentIntroState.title,
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
        resizePending = true;
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
            onStateChange: updateInputState
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
            sceneConstructionRearProfile = resolvedRearProfile;
            scenePromise = Promise.resolve(createScene({
                canvas,
                profile,
                rearProfile: resolvedRearProfile
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
        resizePending = false;
        ambientFrameTime = null;
        const profileChangedAfterConstruction = syncRearProfile();
        if (
            !profileChangedAfterConstruction
            && sceneConstructionRearProfile?.name
            !== resolvedRearProfile.name
        ) {
            scene.setRearProfile?.(resolvedRearProfile);
        }
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
    touching: false
});
