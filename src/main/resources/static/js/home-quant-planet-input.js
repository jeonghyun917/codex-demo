import {
    normalizePointer,
    wrapIndex
} from "./home-quant-planet-motion.js";
import {
    createProductSelection,
    REFERENCE_TIMING,
    resolveAdjacentProduct
} from "./home-quant-planet-composition.js";

const MAX_DRAG_ANGLE = Math.PI * 4;
const CLICK_SUPPRESSION_DISTANCE = 8;
const DRAG_RADIANS_PER_PIXEL = .006;
const TAU = Math.PI * 2;
const clampDragAngle = (value) =>
    Math.min(Math.max(Number(value) || 0, -MAX_DRAG_ANGLE), MAX_DRAG_ANGLE);

export function createOrbitInputController({
    surface,
    links = [],
    onChange,
    onCommit,
    setTimeoutFn = globalThis.setTimeout?.bind(globalThis),
    clearTimeoutFn = globalThis.clearTimeout?.bind(globalThis)
} = {}) {
    const productLinks = [...links];
    const listeners = [];
    let connected = false;
    let activePointerId = null;
    let clickResetTimer = null;
    let dragAngle = 0;
    let dragDistance = 0;
    let gestureTarget = null;
    let idleResumeTimer = null;
    let idleResumePending = false;
    let selectedIndex = 0;
    let dragging = false;
    let focused = false;
    let hovered = false;
    let pressed = false;
    let pointer = { x: 0, y: 0 };
    let pressedPointerId = null;
    let startX = 0;
    let startAngle = 0;
    let suppressedClickTarget = null;

    function isInteracting() {
        return dragging || focused || hovered || pressed;
    }

    function snapshot() {
        return {
            dragAngle,
            selectedIndex,
            dragging,
            focused,
            hovered,
            pressed,
            pointer: { ...pointer },
            paused: isInteracting() || idleResumePending,
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

    function clearTimer(timerId) {
        if (timerId !== null) {
            clearTimeoutFn?.(timerId);
        }
    }

    function cancelIdleResume() {
        clearTimer(idleResumeTimer);
        idleResumeTimer = null;
        idleResumePending = false;
    }

    function scheduleIdleResume() {
        clearTimer(idleResumeTimer);
        idleResumePending = true;
        if (!setTimeoutFn) {
            idleResumePending = false;
            return;
        }
        idleResumeTimer = setTimeoutFn(() => {
            idleResumeTimer = null;
            idleResumePending = false;
            notify();
        }, REFERENCE_TIMING.idleResumeMs);
    }

    function updateIdleResume(wasInteracting) {
        if (isInteracting()) {
            cancelIdleResume();
        } else if (wasInteracting) {
            scheduleIdleResume();
        }
    }

    function nearestAngleForIndex(index) {
        const count = Math.max(1, productLinks.length);
        const step = TAU / count;
        const target = -wrapIndex(index, count) * step;
        let nearest = target
            + Math.round((dragAngle - target) / TAU) * TAU;
        while (nearest > MAX_DRAG_ANGLE) {
            nearest -= TAU;
        }
        while (nearest < -MAX_DRAG_ANGLE) {
            nearest += TAU;
        }
        return nearest;
    }

    function promoteSelection(index) {
        selectedIndex = wrapIndex(index, productLinks.length);
        dragAngle = nearestAngleForIndex(selectedIndex);
    }

    function emitCommit() {
        onCommit?.({ selectedIndex, dragAngle });
    }

    function routeOf(link) {
        return link?.getAttribute?.("href") || link?.href || "";
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
        ) {
            return;
        }
        const wasInteracting = isInteracting();
        activePointerId = event.pointerId;
        startX = Number(event.clientX) || 0;
        startAngle = dragAngle;
        dragDistance = 0;
        gestureTarget = isProductLink(event.target) ? event.target : null;
        dragging = true;
        capturePointer(activePointerId);
        if (!gestureTarget) {
            event.preventDefault?.();
        }
        updateIdleResume(wasInteracting);
        notify();
    }

    function handlePointerMove(event) {
        pointer = normalizePointer(
            event?.clientX,
            event?.clientY,
            surface?.getBoundingClientRect?.()
        );
        if (dragging && event?.pointerId === activePointerId) {
            const distance = (Number(event.clientX) || 0) - startX;
            dragDistance = Math.max(dragDistance, Math.abs(distance));
            dragAngle = clampDragAngle(
                startAngle + distance * DRAG_RADIANS_PER_PIXEL
            );
            if (!gestureTarget || dragDistance >= CLICK_SUPPRESSION_DISTANCE) {
                event.preventDefault?.();
            }
        }
        notify();
    }

    function finishPointer(event) {
        finishPress(event);
        if (!dragging || event?.pointerId !== activePointerId) {
            return;
        }
        const wasInteracting = isInteracting();
        const pointerId = activePointerId;
        const suppressClick = dragDistance >= CLICK_SUPPRESSION_DISTANCE;
        const selection = createProductSelection({
            dragAngle,
            selectedIndex: 0,
            snap: true,
            count: productLinks.length
        });
        const step = TAU / Math.max(1, productLinks.length);
        selectedIndex = selection.selectedIndex;
        dragAngle = clampDragAngle(Math.round(dragAngle / step) * step);
        if (suppressClick && gestureTarget) {
            suppressedClickTarget = gestureTarget;
            clearTimer(clickResetTimer);
            clickResetTimer = setTimeoutFn?.(() => {
                clickResetTimer = null;
                suppressedClickTarget = null;
            }, 0) ?? null;
        }
        try {
            emitCommit();
        } finally {
            releasePointer(pointerId);
            activePointerId = null;
            dragging = false;
            dragDistance = 0;
            gestureTarget = null;
            updateIdleResume(wasInteracting);
            notify();
        }
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
        const wasInteracting = isInteracting();
        promoteSelection(index);
        pressed = true;
        pressedPointerId = event.pointerId;
        updateIdleResume(wasInteracting);
        notify();
    }

    function finishPress(event) {
        if (!pressed || event?.pointerId !== pressedPointerId) {
            return;
        }
        const wasInteracting = isInteracting();
        pressed = false;
        pressedPointerId = null;
        updateIdleResume(wasInteracting);
        notify();
    }

    function handleClick(link, event) {
        if (suppressedClickTarget !== link || event?.detail === 0) {
            return;
        }
        suppressedClickTarget = null;
        clearTimer(clickResetTimer);
        clickResetTimer = null;
        event.preventDefault?.();
        event.stopPropagation?.();
    }

    function handleSurfaceLeave() {
        if (dragging) {
            return;
        }
        pointer = { x: 0, y: 0 };
        notify();
    }

    function handleFocus(index) {
        const wasInteracting = isInteracting();
        promoteSelection(index);
        focused = true;
        updateIdleResume(wasInteracting);
        notify();
    }

    function handleBlur(event) {
        const wasInteracting = isInteracting();
        focused = isProductLink(event?.relatedTarget);
        updateIdleResume(wasInteracting);
        notify();
    }

    function handlePointerEnter(index) {
        const wasInteracting = isInteracting();
        promoteSelection(index);
        hovered = true;
        updateIdleResume(wasInteracting);
        notify();
    }

    function handlePointerLeave(event) {
        const wasInteracting = isInteracting();
        hovered = isProductLink(event?.relatedTarget);
        updateIdleResume(wasInteracting);
        notify();
    }

    function handleKeyDown(index, event) {
        if (event?.key !== "ArrowLeft" && event?.key !== "ArrowRight") {
            return;
        }
        event.preventDefault?.();
        const direction = event.key === "ArrowRight" ? 1 : -1;
        const adjacent = resolveAdjacentProduct(index, direction);
        const routeIndex = productLinks.findIndex(
            (link) => routeOf(link) === adjacent.route
        );
        const adjacentIndex = routeIndex >= 0
            ? routeIndex
            : wrapIndex(index + direction, productLinks.length);
        promoteSelection(adjacentIndex);
        emitCommit();
        productLinks[adjacentIndex]?.focus?.();
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
            listen(link, "click", (event) => handleClick(link, event));
        });
        notify();
    }

    function destroy() {
        if (activePointerId !== null) {
            releasePointer(activePointerId);
        }
        clearTimer(clickResetTimer);
        clearTimer(idleResumeTimer);
        activePointerId = null;
        clickResetTimer = null;
        dragDistance = 0;
        dragging = false;
        gestureTarget = null;
        idleResumePending = false;
        idleResumeTimer = null;
        pressed = false;
        pressedPointerId = null;
        suppressedClickTarget = null;
        connected = false;
        listeners.splice(0).forEach((remove) => remove());
    }

    return {
        connect,
        destroy,
        getState: snapshot
    };
}
