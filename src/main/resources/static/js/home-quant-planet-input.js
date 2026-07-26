import {
    normalizePointer,
    wrapIndex
} from "./home-quant-planet-motion.js";

const MAX_DRAG_ANGLE = Math.PI * 4;
const clampDragAngle = (value) =>
    Math.min(Math.max(Number(value) || 0, -MAX_DRAG_ANGLE), MAX_DRAG_ANGLE);

export function createOrbitInputController({
    surface,
    links = [],
    onChange
} = {}) {
    const productLinks = [...links];
    const listeners = [];
    let connected = false;
    let activePointerId = null;
    let dragAngle = 0;
    let selectedIndex = 0;
    let dragging = false;
    let focused = false;
    let hovered = false;
    let pressed = false;
    let pointer = { x: 0, y: 0 };
    let pressedPointerId = null;
    let startX = 0;
    let startAngle = 0;

    function snapshot() {
        return {
            dragAngle,
            selectedIndex,
            dragging,
            focused,
            hovered,
            pressed,
            pointer: { ...pointer },
            paused: dragging || focused || hovered || pressed,
            snapToSelected: focused || hovered || pressed
        };
    }

    function notify() {
        onChange?.(snapshot());
    }

    function listen(target, type, listener) {
        target?.addEventListener?.(type, listener);
        listeners.push(() => target?.removeEventListener?.(type, listener));
    }

    function isProductLink(target) {
        return productLinks.includes(target);
    }

    function capturePointer(pointerId) {
        try {
            surface?.setPointerCapture?.(pointerId);
        } catch {
            // Pointer capture can disappear during browser gesture cancellation.
        }
    }

    function releasePointer(pointerId) {
        try {
            surface?.releasePointerCapture?.(pointerId);
        } catch {
            // The browser may already have released capture before cleanup.
        }
    }

    function handlePointerDown(event) {
        if (
            event?.isPrimary !== true
            || event.button !== 0
            || activePointerId !== null
            || isProductLink(event.target)
        ) {
            return;
        }
        activePointerId = event.pointerId;
        startX = Number(event.clientX) || 0;
        startAngle = dragAngle;
        dragging = true;
        capturePointer(activePointerId);
        event.preventDefault?.();
        notify();
    }

    function handlePointerMove(event) {
        pointer = normalizePointer(
            event?.clientX,
            event?.clientY,
            surface?.getBoundingClientRect?.()
        );
        if (dragging && event?.pointerId === activePointerId) {
            dragAngle = clampDragAngle(
                startAngle + ((Number(event.clientX) || 0) - startX) * .006
            );
            event.preventDefault?.();
        }
        notify();
    }

    function finishPointer(event) {
        finishPress(event);
        if (!dragging || event?.pointerId !== activePointerId) {
            return;
        }
        releasePointer(activePointerId);
        activePointerId = null;
        dragging = false;
        notify();
    }

    function handlePress(index, event) {
        if (
            event?.isPrimary !== true
            || event.button !== 0
            || pressedPointerId !== null
            || activePointerId !== null
        ) {
            return;
        }
        selectedIndex = index;
        pressed = true;
        pressedPointerId = event.pointerId;
        notify();
    }

    function finishPress(event) {
        if (!pressed || event?.pointerId !== pressedPointerId) {
            return;
        }
        pressed = false;
        pressedPointerId = null;
        notify();
    }

    function handleSurfaceLeave() {
        if (dragging) {
            return;
        }
        pointer = { x: 0, y: 0 };
        notify();
    }

    function handleFocus(index) {
        selectedIndex = index;
        focused = true;
        notify();
    }

    function handleBlur(event) {
        focused = isProductLink(event?.relatedTarget);
        notify();
    }

    function handlePointerEnter(index) {
        selectedIndex = index;
        hovered = true;
        notify();
    }

    function handlePointerLeave(event) {
        hovered = isProductLink(event?.relatedTarget);
        notify();
    }

    function handleKeyDown(index, event) {
        if (event?.key !== "ArrowLeft" && event?.key !== "ArrowRight") {
            return;
        }
        event.preventDefault?.();
        const direction = event.key === "ArrowRight" ? 1 : -1;
        productLinks[wrapIndex(index + direction, productLinks.length)]?.focus?.();
    }

    function connect() {
        if (connected || !surface || productLinks.length === 0) {
            return;
        }
        connected = true;
        listen(surface, "pointerdown", handlePointerDown);
        listen(surface, "pointermove", handlePointerMove);
        listen(surface, "pointerup", finishPointer);
        listen(surface, "pointercancel", finishPointer);
        listen(surface, "pointerleave", handleSurfaceLeave);
        productLinks.forEach((link, index) => {
            listen(link, "focus", () => handleFocus(index));
            listen(link, "blur", handleBlur);
            listen(link, "pointerenter", () => handlePointerEnter(index));
            listen(link, "pointerleave", handlePointerLeave);
            listen(link, "pointerdown", (event) => handlePress(index, event));
            listen(link, "pointerup", finishPress);
            listen(link, "pointercancel", finishPress);
            listen(link, "keydown", (event) => handleKeyDown(index, event));
        });
        notify();
    }

    function destroy() {
        if (activePointerId !== null) {
            releasePointer(activePointerId);
        }
        activePointerId = null;
        dragging = false;
        pressed = false;
        pressedPointerId = null;
        connected = false;
        listeners.splice(0).forEach((remove) => remove());
    }

    return {
        connect,
        destroy,
        getState: snapshot
    };
}
