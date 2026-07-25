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
    let pointer = { x: 0, y: 0 };
    let startX = 0;
    let startAngle = 0;

    function snapshot() {
        return {
            dragAngle,
            selectedIndex,
            dragging,
            focused,
            hovered,
            pointer: { ...pointer },
            paused: dragging || focused || hovered,
            snapToSelected: focused || hovered
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
        if (event?.button !== 0 || isProductLink(event.target)) {
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
        if (!dragging || event?.pointerId !== activePointerId) {
            return;
        }
        releasePointer(activePointerId);
        activePointerId = null;
        dragging = false;
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
        connected = false;
        listeners.splice(0).forEach((remove) => remove());
    }

    return {
        connect,
        destroy,
        getState: snapshot
    };
}
