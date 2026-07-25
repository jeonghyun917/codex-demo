export function clampUnit(value) {
    return Number.isFinite(value) ? Math.min(Math.max(value, 0), 1) : 0;
}

const TAU = Math.PI * 2;
const RING_DURATION = 28000;
const clamp = (value, min, max) =>
    Math.min(Math.max(Number(value) || 0, min), max);

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

const resolveRange = (value, start, end) =>
    clampUnit((value - start) / Math.max(end - start, Number.EPSILON));

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

export function wrapIndex(index, count = 6) {
    const length = Math.max(1, Math.trunc(Number(count)) || 1);
    return ((Math.trunc(Number(index)) || 0) % length + length) % length;
}

export function advanceRingAngle(
    angle = 0, deltaMs = 0, paused = false, reduceMotion = false
) {
    const current = Number.isFinite(angle) ? angle : 0;
    if (paused || reduceMotion) return current;
    const delta = clamp(deltaMs, 0, 100);
    return current + delta / RING_DURATION * TAU;
}

export function createNavigationRingState({
    rotationAngle = 0, dragAngle = 0, selectedIndex = 0,
    snapToSelected = false, itemCount = 6
} = {}) {
    const count = Math.max(1, Math.trunc(Number(itemCount)) || 1);
    const selected = wrapIndex(selectedIndex, count);
    const step = TAU / count;
    const automatic = snapToSelected
        ? -selected * step : Number(rotationAngle) || 0;
    const baseAngle = automatic
        + (snapToSelected ? 0 : clamp(dragAngle, -TAU * 2, TAU * 2));
    const items = Array.from({ length: count }, (_, index) => {
        const angle = baseAngle + index * step;
        const depth = (Math.cos(angle) + 1) / 2;
        return {
            index, angle, depth,
            opacity: .35 + depth * .65,
            scale: .72 + depth * .40,
            zIndex: Math.round(depth * 100)
        };
    });
    const frontIndex = items.reduce((front, item) =>
        item.depth > items[front].depth ? item.index : front, 0);
    return { baseAngle, frontIndex, items };
}

export function createIntroState({
    elapsed = 0, ready = false, readyElapsed = 0,
    timedOut = false, reduceMotion = false
} = {}) {
    const total = Math.max(0, Number(elapsed) || 0);
    const sinceReady = Math.max(0, Number(readyElapsed) || 0);
    if (timedOut || (reduceMotion && ready)) {
        return {
            phase: "ready", loaderOpacity: 0, markProgress: 1,
            splitProgress: 0, revealProgress: 1, worldScale: 1,
            interactionReady: true
        };
    }
    if (reduceMotion) {
        return {
            phase: "boot", loaderOpacity: 1, markProgress: 1,
            splitProgress: 0, revealProgress: 0, worldScale: 1,
            interactionReady: false
        };
    }
    const markProgress = clamp((total - 250) / 625, 0, 1);
    const splitProgress = ready ? smoothStep(sinceReady / 400) : 0;
    const revealProgress = ready
        ? smoothStep((sinceReady - 250) / 600) : 0;
    const interactionReady = ready && revealProgress >= 1;
    const phase = interactionReady ? "ready"
        : ready ? (sinceReady < 250 ? "split" : "reveal")
            : total < 250 ? "boot" : "mark";
    return {
        phase,
        loaderOpacity: 1 - revealProgress,
        markProgress,
        splitProgress,
        revealProgress,
        worldScale: .88 + revealProgress * .12,
        interactionReady
    };
}

export function createOrbitPose({
    time = 0, dragAngle = 0, pointer = { x: 0, y: 0 },
    reduceMotion = false
} = {}) {
    const pointerX = clamp(pointer?.x, -1, 1);
    const pointerY = clamp(pointer?.y, -1, 1);
    const drag = clamp(dragAngle, -TAU * 2, TAU * 2);
    const ambient = reduceMotion ? 0
        : (Math.max(0, Number(time) || 0) % RING_DURATION)
            / RING_DURATION * TAU;
    return {
        cameraDistance: 8.9,
        cameraYaw: clamp(drag * .18 + pointerX * .0698, -TAU, TAU),
        cameraPitch: clamp(.12 + pointerY * .052, -.34, .34),
        planetYaw: drag + ambient * .08,
        planetPitch: clamp(-.08 + pointerY * .02, -.18, .18),
        planetRoll: pointerX * .018
    };
}

export function createCourierLoopPose(time = 0, reduceMotion = false) {
    if (reduceMotion) {
        return {
            routeProgress: .5, routeAngle: .19, elevation: .195,
            heading: .19 + Math.PI / 2, walkPhase: 0, idleBlend: 1,
            routeActivity: .45,
            landmarkActivity: { observe: 0, resolve: 1, act: 0 }
        };
    }
    const elapsed = Math.max(0, Number(time) || 0);
    const cycle = elapsed % 24000;
    let routeProgress;
    let direction = 1;
    let idleBlend = 0;
    if (cycle < 6000) {
        routeProgress = .5 * smoothStep(cycle / 6000);
    } else if (cycle < 8000) {
        routeProgress = .5;
        idleBlend = 1;
    } else if (cycle < 14000) {
        routeProgress = .5 + .5 * smoothStep((cycle - 8000) / 6000);
    } else if (cycle < 16000) {
        routeProgress = 1;
        idleBlend = 1;
    } else if (cycle < 22000) {
        direction = -1;
        routeProgress = 1 - smoothStep((cycle - 16000) / 6000);
    } else {
        direction = -1;
        routeProgress = 0;
        idleBlend = 1;
    }
    const routeAngle = -.9 + routeProgress * 2.18;
    const observe = 1 - smoothStep(resolveRange(routeProgress, 0, .25));
    const resolve = 1 - smoothStep(
        clamp(Math.abs(routeProgress - .5) / .25, 0, 1)
    );
    const act = smoothStep(resolveRange(routeProgress, .75, 1));
    const phase = (elapsed / 260) % TAU;
    return {
        routeProgress,
        routeAngle,
        elevation: .17 + Math.sin(routeProgress * Math.PI) * .025,
        heading: routeAngle + (direction > 0 ? Math.PI / 2 : -Math.PI / 2),
        walkPhase: phase < 0 ? phase + TAU : phase,
        idleBlend,
        routeActivity: 1 - idleBlend * .55,
        landmarkActivity: { observe, resolve, act }
    };
}

export function shouldRenderFrame(state) {
    return Boolean(
        state?.visible && !state.hidden && state.webgl
        && (state.dirty || state.ambient)
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
            setChapter(name) { root?.setAttribute?.("data-planet-chapter", name || ""); },
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
        setChapter(name) {
            root?.setAttribute?.("data-planet-chapter", name || "");
        },
        destroy() {
            animations.forEach((animation) => animation?.cancel?.());
            animations.length = 0;
        }
    };
}
