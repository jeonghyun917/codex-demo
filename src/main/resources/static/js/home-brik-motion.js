export function clamp(value, min, max) {
    const lower = Number.isFinite(min) ? min : 0;
    const upper = Number.isFinite(max) ? Math.max(max, lower) : lower;
    const finiteValue = Number.isFinite(value) ? value : lower;
    return Math.min(Math.max(finiteValue, lower), upper);
}

export function normalizePointer(clientX, clientY, rect) {
    const width = Number.isFinite(rect?.width) && rect.width > 0 ? rect.width : 0;
    const height = Number.isFinite(rect?.height) && rect.height > 0 ? rect.height : 0;
    const left = Number.isFinite(rect?.left) ? rect.left : 0;
    const top = Number.isFinite(rect?.top) ? rect.top : 0;

    return {
        x: width ? clamp(((clientX - left) / width) * 2 - 1, -1, 1) : 0,
        y: height ? clamp(((clientY - top) / height) * 2 - 1, -1, 1) : 0
    };
}

export function selectHomeMotionProfile(environment) {
    if (environment.reduceMotion) {
        return { name: "reduced", pixelRatio: 1, pointerEnabled: false, animate: false };
    }
    if (environment.mobile || environment.cores <= 4) {
        return { name: "low", pixelRatio: 1, pointerEnabled: false, animate: true };
    }
    return {
        name: "high",
        pixelRatio: clamp(environment.devicePixelRatio || 1, 1, 1.5),
        pointerEnabled: true,
        animate: true
    };
}

export function staggerDelay(index, step = 48) {
    const safeIndex = Number.isFinite(index) ? Math.max(index, 0) : 0;
    const safeStep = Number.isFinite(step) ? Math.max(step, 0) : 48;
    return safeIndex * safeStep;
}
