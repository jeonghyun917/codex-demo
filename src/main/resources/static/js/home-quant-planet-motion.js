import {
    REFERENCE_HERO
} from "./home-quant-planet-composition.js";

export function clampUnit(value) {
    return Number.isFinite(value) ? Math.min(Math.max(value, 0), 1) : 0;
}

export function normalizePointer(clientX, clientY, rect) {
    const width = Number.isFinite(rect?.width) && rect.width > 0 ? rect.width : 0;
    const height = Number.isFinite(rect?.height) && rect.height > 0 ? rect.height : 0;
    const left = Number.isFinite(rect?.left) ? rect.left : 0;
    const top = Number.isFinite(rect?.top) ? rect.top : 0;

    return {
        x: width ? clampUnit((clientX - left) / width) * 2 - 1 : 0,
        y: height ? clampUnit((clientY - top) / height) * 2 - 1 : 0
    };
}

export function resolveLocalTestOverrides({ hostname, search } = {}) {
    const loopback = ["127.0.0.1", "localhost", "::1", "[::1]"].includes(hostname);
    if (!loopback) {
        return { forceStatic: false, forceReduced: false };
    }

    const params = new URLSearchParams(search || "");
    return {
        forceStatic: params.get("render") === "static",
        forceReduced: params.get("motion") === "reduce"
    };
}

const smoothStep = (value) => {
    const t = clampUnit(value);
    return t * t * (3 - 2 * t);
};

export function selectPlanetQualityProfile(environment = {}) {
    if (environment.reduceMotion || !environment.webgl) {
        return {
            name: "static", webgl: false, pixelRatio: 1,
            pointerEnabled: false, shadows: false, outlineLevel: 0,
            foliageCount: 0, cloudCount: 0, planetScale: 1
        };
    }
    if (environment.mobile || Number(environment.cores) <= 4) {
        return {
            name: "balanced", webgl: true, pixelRatio: 1.25,
            pointerEnabled: false, shadows: false, outlineLevel: 1,
            foliageCount: 18, cloudCount: 5, planetScale: .82
        };
    }
    return {
        name: "high", webgl: true,
        pixelRatio: Math.min(Math.max(Number(environment.dpr) || 1, 1), 1.5),
        pointerEnabled: true, shadows: true, outlineLevel: 2,
        foliageCount: 34, cloudCount: 8, planetScale: 1
    };
}

export function createHeroIntroState({
    elapsedMs = 0,
    sceneReady = false,
    reduceMotion = false
} = {}) {
    const elapsed = Math.max(0, Number(elapsedMs) || 0);
    if (reduceMotion || elapsed >= REFERENCE_HERO.introDeadlineMs) {
        return {
            phase: "ready",
            loader: 1,
            world: 1,
            title: 1,
            action: 1,
            ready: true
        };
    }

    const loader = smoothStep(elapsed / 600);
    const world = sceneReady
        ? smoothStep((elapsed - 600) / 700)
        : 0;
    const title = sceneReady
        ? smoothStep((elapsed - 1100) / 650)
        : 0;
    const action = sceneReady
        ? smoothStep((elapsed - 1600) / 500)
        : 0;
    const ready = sceneReady && action >= 1;
    const phase = ready ? "ready"
        : action > 0 ? "action"
            : title > 0 ? "title"
                : world > 0 ? "world" : "loader";

    return {
        phase,
        loader,
        world,
        title,
        action,
        ready
    };
}

export function createHeroPose({
    elapsedMs = 0,
    worldAngle = 0,
    pointer = { x: 0, y: 0 },
    titleProgress = 1,
    reduceMotion = false
} = {}) {
    const elapsed = Math.max(0, Number(elapsedMs) || 0);
    const ambient = reduceMotion
        ? 0
        : elapsed / REFERENCE_HERO.worldRevolutionMs * Math.PI * 2;
    return {
        worldAngle: (Number(worldAngle) || 0) + ambient,
        pointer: reduceMotion ? { x: 0, y: 0 } : {
            x: Number(pointer?.x) || 0,
            y: Number(pointer?.y) || 0
        },
        titleProgress: reduceMotion ? 1 : clampUnit(titleProgress),
        elapsedMs: elapsed,
        reduceMotion
    };
}

export function shouldRenderFrame(state) {
    const ambient = state?.ambient ?? Boolean(
        state?.profile?.webgl
        && state.profile.name !== "static"
        && !state?.reducedMotion
    );
    return Boolean(
        state?.visible && !state.hidden && state.webgl
        && (state.dirty || ambient)
    );
}

export function createPageLifecycle({
    requestFrame,
    cancelFrame,
    onFrame,
    onPause,
    onResume,
    onDestroy
} = {}) {
    let frameId = 0;
    let frameScheduled = false;
    let paused = false;
    let destroyed = false;
    let started = false;

    function scheduleFrame() {
        if (destroyed || paused || frameScheduled || typeof requestFrame !== "function") {
            return;
        }
        frameScheduled = true;
        frameId = requestFrame(runFrame);
    }

    function runFrame(time) {
        frameScheduled = false;
        frameId = 0;
        if (destroyed || paused) {
            return;
        }
        onFrame?.(time);
        scheduleFrame();
    }

    function start() {
        started = true;
        scheduleFrame();
    }

    function pause() {
        if (destroyed || paused) {
            return;
        }
        paused = true;
        if (frameScheduled) {
            cancelFrame?.(frameId);
            frameScheduled = false;
            frameId = 0;
        }
        onPause?.();
    }

    function resume() {
        if (destroyed || !paused) {
            return;
        }
        paused = false;
        onResume?.();
        if (started) {
            scheduleFrame();
        }
    }

    function destroy() {
        if (destroyed) {
            return;
        }
        destroyed = true;
        if (frameScheduled) {
            cancelFrame?.(frameId);
            frameScheduled = false;
            frameId = 0;
        }
        onDestroy?.();
    }

    function handlePageHide(event) {
        if (event?.persisted) {
            pause();
        } else {
            destroy();
        }
    }

    function handlePageShow(event) {
        if (event?.persisted) {
            resume();
        }
    }

    return {
        start,
        pause,
        resume,
        destroy,
        handlePageHide,
        handlePageShow
    };
}

function makeVisible(element) {
    if (!element?.style) {
        return;
    }
    element.style.opacity = "1";
    element.style.transform = "none";
}

function revealAll(root) {
    root?.querySelectorAll?.("[data-home-reveal]")?.forEach(makeVisible);
}

export function createDomMotion({ MotionAPI, root, reduceMotion } = {}) {
    const animations = [];
    const supportsMotion = Boolean(MotionAPI?.animate) && !reduceMotion;

    revealAll(root);

    if (!supportsMotion) {
        return {
            reveal: makeVisible,
            destroy() {}
        };
    }

    return {
        reveal(element) {
            if (!element) {
                return;
            }
            const animation = MotionAPI.animate(element, {
                opacity: [0, 1],
                transform: ["translateY(16px)", "translateY(0)"]
            }, { duration: 0.56, easing: "ease-out" });
            animations.push(animation);
        },
        destroy() {
            animations.forEach((animation) => animation?.cancel?.());
            animations.length = 0;
        }
    };
}
