import {
    normalizePointer
} from "./home-quant-planet-motion.js";
import {
    REFERENCE_HERO
} from "./home-quant-planet-composition.js";

export function createHeroInputController({
    surface,
    windowTarget = globalThis,
    onStateChange
} = {}) {
    const listeners = [];
    const setTimeoutFn = windowTarget?.setTimeout?.bind?.(windowTarget);
    const clearTimeoutFn = windowTarget?.clearTimeout?.bind?.(windowTarget);
    let activePointerId = null;
    let destroyed = false;
    let dragAngle = 0;
    let dragging = false;
    let pointer = { x: 0, y: 0 };
    let pointerCaptured = false;
    let settleTimer = null;
    let settling = false;
    let startAngle = 0;
    let startX = 0;

    function snapshot() {
        return {
            pointer: { ...pointer },
            dragAngle,
            dragging,
            settling
        };
    }

    function notify() {
        onStateChange?.(snapshot());
    }

    function listen(target, type, listener) {
        target?.addEventListener?.(type, listener);
        listeners.push(() => target?.removeEventListener?.(type, listener));
    }

    function clearSettle() {
        if (settleTimer !== null) {
            clearTimeoutFn?.(settleTimer);
            settleTimer = null;
        }
        settling = false;
    }

    function beginSettle() {
        clearSettle();
        if (typeof setTimeoutFn !== "function") {
            return;
        }
        settling = true;
        settleTimer = setTimeoutFn(() => {
            settleTimer = null;
            if (destroyed) {
                return;
            }
            settling = false;
            notify();
        }, REFERENCE_HERO.settleMs);
    }

    function capturePointer(pointerId) {
        try {
            surface?.setPointerCapture?.(pointerId);
            return true;
        } catch {
            return false;
        }
    }

    function releasePointer(pointerId) {
        try {
            surface?.releasePointerCapture?.(pointerId);
        } catch {
            // Capture may already be gone after browser gesture cancellation.
        }
    }

    function handlePointerDown(event) {
        if (
            destroyed
            || activePointerId !== null
            || event?.isPrimary !== true
            || event?.button !== 0
        ) {
            return;
        }

        clearSettle();
        activePointerId = event.pointerId;
        startAngle = dragAngle;
        startX = Number.isFinite(event.clientX) ? event.clientX : 0;
        dragging = true;
        event.preventDefault?.();
        pointerCaptured = capturePointer(activePointerId);
        notify();
    }

    function handlePointerMove(event) {
        if (destroyed) {
            return;
        }
        if (activePointerId !== null) {
            if (event?.pointerId !== activePointerId) {
                return;
            }
            const clientX = Number.isFinite(event.clientX)
                ? event.clientX
                : startX;
            dragAngle = startAngle
                + (clientX - startX) * REFERENCE_HERO.dragRadiansPerPixel;
            event.preventDefault?.();
            notify();
            return;
        }
        if (event?.isPrimary !== true) {
            return;
        }
        pointer = normalizePointer(
            event.clientX,
            event.clientY,
            surface?.getBoundingClientRect?.()
        );
        notify();
    }

    function finishPointer(event) {
        if (
            destroyed
            || activePointerId === null
            || event?.pointerId !== activePointerId
        ) {
            return;
        }

        const pointerId = activePointerId;
        const shouldReleaseCapture = pointerCaptured
            && event.type !== "lostpointercapture";
        activePointerId = null;
        dragging = false;
        pointerCaptured = false;
        if (shouldReleaseCapture) {
            releasePointer(pointerId);
        }
        if (event.type === "pointerup") {
            beginSettle();
        } else {
            clearSettle();
        }
        notify();
    }

    function handlePointerLeave(event) {
        if (destroyed || dragging || event?.isPrimary !== true) {
            return;
        }
        pointer = { x: 0, y: 0 };
        notify();
    }

    function setBaseAngle(value) {
        if (destroyed || dragging) {
            return;
        }
        const nextAngle = Number(value);
        if (!Number.isFinite(nextAngle) || nextAngle === dragAngle) {
            return;
        }
        clearSettle();
        dragAngle = nextAngle;
        notify();
    }

    function destroy() {
        if (destroyed) {
            return;
        }
        destroyed = true;
        clearSettle();
        if (activePointerId !== null && pointerCaptured) {
            releasePointer(activePointerId);
        }
        activePointerId = null;
        dragging = false;
        pointer = { x: 0, y: 0 };
        pointerCaptured = false;
        listeners.splice(0).forEach((remove) => remove());
    }

    listen(surface, "pointerdown", handlePointerDown);
    listen(surface, "pointermove", handlePointerMove);
    listen(surface, "pointerup", finishPointer);
    listen(surface, "pointercancel", finishPointer);
    listen(surface, "lostpointercapture", finishPointer);
    listen(surface, "pointerleave", handlePointerLeave);
    listen(windowTarget, "pointerup", finishPointer);
    listen(windowTarget, "pointercancel", finishPointer);
    notify();

    return {
        getState: snapshot,
        setBaseAngle,
        destroy
    };
}
