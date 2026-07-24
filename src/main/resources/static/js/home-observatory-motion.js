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
    const loopback = ["127.0.0.1", "localhost", "::1"].includes(hostname);
    if (!loopback) {
        return { forceStatic: false, forceReduced: false };
    }

    const params = new URLSearchParams(search || "");
    return {
        forceStatic: params.get("render") === "static",
        forceReduced: params.get("motion") === "reduce"
    };
}

export function selectHomeQualityProfile(environment = {}) {
    if (environment.reduceMotion || !environment.webgl) {
        return {
            name: "static", pixelRatio: 1, pointerEnabled: false,
            webgl: false, shadows: false, segments: 0, coreScale: 1
        };
    }

    const coreScale = environment.mobile ? 2 : environment.tablet ? 1.7 : 1;
    if (environment.mobile || Number(environment.cores) <= 4) {
        return {
            name: "low", pixelRatio: 1, pointerEnabled: false,
            webgl: true, shadows: false, segments: 48, coreScale
        };
    }

    const devicePixelRatio = Number.isFinite(environment.dpr) ? environment.dpr : 1;
    return {
        name: "high", pixelRatio: Math.min(Math.max(devicePixelRatio, 1), 1.5),
        pointerEnabled: true, webgl: true, shadows: true, segments: 96, coreScale
    };
}

const resolveRange = (value, start, end) =>
    clampUnit((value - start) / Math.max(end - start, Number.EPSILON));

const smoothStep = (value) => {
    const t = clampUnit(value);
    return t * t * (3 - 2 * t);
};

function resolveCameraTravel(progress) {
    if (progress <= 0.18) {
        return 0.3 * smoothStep(resolveRange(progress, 0, 0.18));
    }
    if (progress <= 0.52) {
        return 0.3 + 0.9 * smoothStep(resolveRange(progress, 0.18, 0.52));
    }
    if (progress <= 0.68) {
        return 1.2 + 0.12 * smoothStep(resolveRange(progress, 0.52, 0.68));
    }
    return 1.32 + 1.08 * smoothStep(resolveRange(progress, 0.68, 1));
}

export function createScenePose(progress, pointer = { x: 0, y: 0 }) {
    const p = clampUnit(progress);
    const resolve = resolveRange(p, 0.18, 0.52);
    const act = resolveRange(p, 0.68, 1);
    return {
        cameraZ: 8.8 - resolveCameraTravel(p),
        cameraY: 0.3 - p * 0.22 + Math.max(-1, Math.min(1, pointer.y || 0)) * 0.08,
        housingOpen: resolve,
        irisRotation: resolve * Math.PI * 0.42,
        metricOpacity: clampUnit(resolve * (1 - act)),
        productOpacity: act
    };
}

export function shouldRenderFrame(state) {
    return Boolean(state?.visible && !state.hidden && state.webgl && state.dirty);
}

function makeVisible(element) {
    if (!element?.style) {
        return;
    }
    element.style.opacity = "1";
    element.style.transform = "none";
}

function revealAll(root) {
    root?.querySelectorAll?.("[data-motion-reveal]")?.forEach(makeVisible);
}

export function createDomMotion({ MotionAPI, root, reduceMotion } = {}) {
    const animations = [];
    const supportsMotion = Boolean(MotionAPI?.animate) && !reduceMotion;

    revealAll(root);

    if (!supportsMotion) {
        return {
            reveal: makeVisible,
            setChapter(name) { root?.setAttribute?.("data-observatory-chapter", name || ""); },
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
            root?.setAttribute?.("data-observatory-chapter", name || "");
        },
        destroy() {
            animations.forEach((animation) => animation?.cancel?.());
            animations.length = 0;
        }
    };
}
