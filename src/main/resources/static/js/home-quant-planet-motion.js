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

export function normalizeScrollProgress(top, height, viewportHeight) {
    const safeTop = Number.isFinite(top) ? top : 0;
    const safeHeight = Number.isFinite(height) ? height : 0;
    const safeViewport = Number.isFinite(viewportHeight) ? viewportHeight : 0;
    const range = Math.max(safeHeight - safeViewport, Number.EPSILON);
    return clampUnit(-safeTop / range);
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

export function resolvePlanetChapter(progress) {
    const p = clampUnit(progress);
    return p < .30 ? "observe" : p < .66 ? "resolve" : "act";
}

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

export function createCourierPose(progress, time = 0) {
    const p = clampUnit(progress);
    const routeAngle = -.9 + p * 2.18;
    const elapsed = Number.isFinite(time) ? time : 0;
    const walkPhase = ((p * 20) + elapsed / 330) % (Math.PI * 2);
    return {
        routeAngle,
        elevation: .17 + Math.sin(p * Math.PI) * .025,
        heading: routeAngle + Math.PI / 2,
        walkPhase: walkPhase < 0 ? walkPhase + Math.PI * 2 : walkPhase,
        idleBlend: smoothStep(resolveRange(p, .9, 1))
    };
}

export function createPlanetPose(progress, pointer = { x: 0, y: 0 }) {
    const p = clampUnit(progress);
    const resolve = smoothStep(resolveRange(p, .24, .70));
    const act = smoothStep(resolveRange(p, .62, 1));
    const pointerX = Math.max(-1, Math.min(1, Number(pointer?.x) || 0));
    const pointerY = Math.max(-1, Math.min(1, Number(pointer?.y) || 0));
    return {
        cameraDistance: 10.8 - smoothStep(p) * 3.2,
        cameraYaw: -.95 + p * 1.9 + pointerX * .0698,
        cameraPitch: .32 - resolve * .18 + pointerY * .052,
        planetYaw: -.48 + p * 1.35,
        planetPitch: -.1 + resolve * .08,
        planetRoll: -.04 + act * .07,
        routeActivity: .28 + Math.sin(p * Math.PI) * .72,
        landmarkActivity: {
            observe: 1 - resolve,
            resolve: resolve * (1 - act),
            act
        },
        metricOpacity: clampUnit(resolve * (1 - act * .72))
    };
}

export function shouldRenderFrame(state) {
    return Boolean(state?.visible && !state.hidden && state.webgl && state.dirty);
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
