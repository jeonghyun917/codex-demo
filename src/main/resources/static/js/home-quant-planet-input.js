import {
    normalizePointer
} from "./home-quant-planet-motion.js";

export function createHeroInputController({
    surface,
    windowTarget = globalThis,
    onStateChange
} = {}) {
    const listeners = [];
    let activeTouchId = null;
    let destroyed = false;
    let pointer = { x: 0, y: 0 };

    function snapshot() {
        return {
            pointer: { ...pointer },
            touching: activeTouchId !== null
        };
    }

    function notify() {
        onStateChange?.(snapshot());
    }

    function listen(target, type, listener) {
        target?.addEventListener?.(type, listener);
        listeners.push(() => target?.removeEventListener?.(type, listener));
    }

    function updatePosition(event) {
        pointer = normalizePointer(
            event.clientX,
            event.clientY,
            surface?.getBoundingClientRect?.()
        );
        notify();
    }

    function handlePointerDown(event) {
        if (destroyed || event?.isPrimary !== true) {
            return;
        }
        if (event.pointerType === "touch") {
            activeTouchId = event.pointerId;
        }
        updatePosition(event);
    }

    function handlePointerMove(event) {
        if (destroyed || event?.isPrimary !== true) {
            return;
        }
        if (activeTouchId !== null && event.pointerId !== activeTouchId) {
            return;
        }
        updatePosition(event);
    }

    function finishPointer(event) {
        if (destroyed || event?.pointerId !== activeTouchId) {
            return;
        }
        activeTouchId = null;
        pointer = { x: 0, y: 0 };
        notify();
    }

    function handlePointerLeave(event) {
        if (
            destroyed
            || event?.isPrimary !== true
            || event?.pointerType === "touch"
        ) {
            return;
        }
        pointer = { x: 0, y: 0 };
        notify();
    }

    function destroy() {
        if (destroyed) {
            return;
        }
        destroyed = true;
        activeTouchId = null;
        pointer = { x: 0, y: 0 };
        listeners.splice(0).forEach((remove) => remove());
    }

    listen(surface, "pointerdown", handlePointerDown);
    listen(surface, "pointermove", handlePointerMove);
    listen(surface, "pointerup", finishPointer);
    listen(surface, "pointercancel", finishPointer);
    listen(surface, "pointerleave", handlePointerLeave);
    listen(windowTarget, "pointerup", finishPointer);
    listen(windowTarget, "pointercancel", finishPointer);
    notify();

    return {
        destroy,
        getState: snapshot
    };
}
