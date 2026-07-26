import {
    advanceRingAngle,
    createIntroState,
    createNavigationRingState,
    createPageLifecycle,
    selectPlanetQualityProfile
} from "./home-quant-planet-motion.js";
import {
    createOrbitInputController
} from "./home-quant-planet-input.js";

const INTRO_TIMEOUT_MS = 4000;

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
        motionDisabled: environment.reduceMotion || profile.name === "static"
    };
}

export function createQuantPlanetRuntime({
    root,
    experience,
    canvas,
    loader,
    introMark,
    productLinks,
    profile,
    motionDisabled = profile?.name === "static",
    isLoopback = false,
    createScene,
    createInput = createOrbitInputController,
    requestFrame,
    cancelFrame,
    now,
    documentTarget,
    windowTarget
} = {}) {
    const staticMode = profile?.name === "static";
    const startedAt = now();
    const renderTimes = [];
    let scene = null;
    let sceneReadyAt = null;
    let lifecycle = null;
    let inputController = null;
    let inputState = {
        dragAngle: 0,
        selectedIndex: 0,
        dragging: false,
        focused: false,
        hovered: false,
        pressed: false,
        pointer: { x: 0, y: 0 },
        paused: false,
        snapToSelected: false
    };
    let ringAngle = 0;
    let lastFrameTime = null;
    let animationRunning = false;
    let introTimedOut = false;
    let runtimeFailed = false;
    let destroyed = false;

    root.classList.add("planet-runtime-starting");

    function applyIntroState(state) {
        root.dataset.introPhase = state.phase;
        root.style.setProperty(
            "--intro-mark-progress",
            String(state.markProgress)
        );
        root.style.setProperty(
            "--intro-split-progress",
            String(state.splitProgress)
        );
        root.style.setProperty(
            "--intro-reveal-progress",
            String(state.revealProgress)
        );
        root.style.setProperty(
            "--intro-world-scale",
            String(state.worldScale)
        );
        loader.style.opacity = String(state.loaderOpacity);
        if (introMark?.style) {
            introMark.style.opacity = String(state.markProgress);
        }
    }

    function markIntroReady() {
        root.classList.remove("planet-runtime-starting");
        root.classList.add("planet-intro-ready");
    }

    function applyRingState(state) {
        const radiusX = Math.min(
            windowTarget.innerWidth * .34,
            windowTarget.innerHeight * .48
        );
        const radiusY = Math.min(windowTarget.innerHeight * .10, 88);
        state.items.forEach((item, index) => {
            const link = productLinks[index];
            link.style.setProperty(
                "--orbit-x",
                `${Math.sin(item.angle) * radiusX}px`
            );
            link.style.setProperty(
                "--orbit-y",
                `${Math.sin(item.angle * 2) * radiusY}px`
            );
            link.style.setProperty(
                "--orbit-z",
                `${(item.depth - .5) * 240}px`
            );
            link.style.setProperty(
                "--orbit-scale",
                item.scale.toFixed(3)
            );
            link.style.setProperty(
                "--orbit-opacity",
                item.opacity.toFixed(3)
            );
            link.style.setProperty(
                "--orbit-z-index",
                String(item.zIndex)
            );
        });
        root.dataset.productFront = String(state.frontIndex);
    }

    const initialIntroState = staticMode
        ? createIntroState({
            elapsed: 0,
            ready: true,
            readyElapsed: 0,
            timedOut: false,
            reduceMotion: true
        })
        : createIntroState({
            elapsed: 0,
            ready: false,
            readyElapsed: 0,
            timedOut: false,
            reduceMotion: false
        });
    applyIntroState(initialIntroState);
    if (initialIntroState.interactionReady) {
        markIntroReady();
    }

    let scenePromise = null;
    if (staticMode) {
        root.classList.remove("planet-webgl-ready");
        root.classList.add("planet-webgl-failed");
    } else {
        try {
            scenePromise = Promise.resolve(createScene({ canvas, profile }));
        } catch (error) {
            scenePromise = Promise.reject(error);
        }
    }

    function currentRingState() {
        return createNavigationRingState({
            rotationAngle: ringAngle,
            dragAngle: inputState.dragAngle,
            selectedIndex: inputState.selectedIndex,
            snapToSelected: inputState.snapToSelected,
            itemCount: productLinks.length
        });
    }

    function applyCurrentRingState() {
        applyRingState(currentRingState());
    }

    inputController = createInput({
        surface: experience,
        links: productLinks,
        onChange(state) {
            inputState = state;
            if (!animationRunning) {
                applyCurrentRingState();
                scene?.setPointer(inputState.pointer);
                scene?.setDragAngle(inputState.dragAngle);
            }
        }
    });
    inputController.connect();

    function dismissIntro() {
        introTimedOut = true;
        applyIntroState(createIntroState({
            elapsed: INTRO_TIMEOUT_MS,
            ready: Boolean(scene),
            readyElapsed: 0,
            timedOut: true,
            reduceMotion: motionDisabled
        }));
        markIntroReady();
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

    function pauseRuntime() {
        animationRunning = false;
        lastFrameTime = null;
        scene?.setVisible(false);
    }

    function disposeScene() {
        const activeScene = scene;
        scene = null;
        try {
            activeScene?.dispose();
        } catch {
            // Continue removing input and lifecycle listeners after GPU teardown.
        }
    }

    function resumeRuntime() {
        if (runtimeFailed || destroyed) {
            return;
        }
        scene?.resize();
        scene?.setVisible(!documentTarget.hidden);
        animationRunning = !motionDisabled && !documentTarget.hidden;
    }

    function destroyRuntime() {
        if (destroyed) {
            return;
        }
        destroyed = true;
        animationRunning = false;
        inputController?.destroy();
        disposeScene();
        documentTarget.removeEventListener(
            "visibilitychange",
            handleVisibilityChange
        );
        windowTarget.removeEventListener("resize", handleResize);
        windowTarget.removeEventListener(
            "pagehide",
            lifecycle.handlePageHide
        );
        windowTarget.removeEventListener(
            "pageshow",
            lifecycle.handlePageShow
        );
        canvas.removeEventListener(
            "webglcontextlost",
            handleContextLost
        );
    }

    function tick(time) {
        const frameTime = Number.isFinite(time) ? time : now();
        const elapsed = Math.max(0, frameTime - startedAt);
        if (!introTimedOut && elapsed >= INTRO_TIMEOUT_MS) {
            introTimedOut = true;
        }
        const readyElapsed = sceneReadyAt === null
            ? 0
            : Math.max(0, frameTime - sceneReadyAt);
        const introState = createIntroState({
            elapsed,
            ready: Boolean(scene),
            readyElapsed,
            timedOut: introTimedOut,
            reduceMotion: motionDisabled
        });
        applyIntroState(introState);
        if (introState.interactionReady) {
            markIntroReady();
        }

        const deltaTime = lastFrameTime === null
            ? 0
            : frameTime - lastFrameTime;
        lastFrameTime = frameTime;
        ringAngle = advanceRingAngle(
            ringAngle,
            deltaTime,
            inputState.paused,
            motionDisabled
        );
        applyCurrentRingState();

        scene?.setPointer(inputState.pointer);
        scene?.setDragAngle(inputState.dragAngle);
        const startedRenderAt = isLoopback ? now() : 0;
        try {
            const rendered = scene?.render(frameTime) || false;
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
        animationRunning = false;
        lifecycle.pause();
        windowTarget.removeEventListener(
            "pageshow",
            lifecycle.handlePageShow
        );
        disposeScene();
        root.classList.remove("planet-webgl-ready");
        root.classList.add("planet-webgl-failed");
        dismissIntro();
        applyCurrentRingState();
    }

    function handleContextLost(event) {
        event.preventDefault();
        failRuntime();
    }

    function handleVisibilityChange() {
        if (documentTarget.hidden) {
            lifecycle.pause();
        } else if (!runtimeFailed) {
            lifecycle.resume();
        }
    }

    function handleResize() {
        scene?.resize();
        applyCurrentRingState();
    }

    documentTarget.addEventListener(
        "visibilitychange",
        handleVisibilityChange
    );
    windowTarget.addEventListener("resize", handleResize, { passive: true });
    windowTarget.addEventListener("pagehide", lifecycle.handlePageHide);
    windowTarget.addEventListener("pageshow", lifecycle.handlePageShow);
    canvas.addEventListener("webglcontextlost", handleContextLost);

    applyCurrentRingState();
    if (!motionDisabled) {
        animationRunning = !documentTarget.hidden;
        lifecycle.start();
        if (documentTarget.hidden) {
            lifecycle.pause();
        }
    }

    scenePromise?.then((createdScene) => {
        if (destroyed || runtimeFailed) {
            try {
                createdScene.dispose();
            } catch {
                // The retired scene cannot block lifecycle cleanup.
            }
            return;
        }
        scene = createdScene;
        sceneReadyAt = now();
        scene.resize();
        scene.setVisible(!documentTarget.hidden);
        scene.setPointer(inputState.pointer);
        scene.setDragAngle(inputState.dragAngle);
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
