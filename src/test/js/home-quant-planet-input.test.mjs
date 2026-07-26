import assert from "node:assert/strict";
import test from "node:test";
import {
    createHeroInputController
} from "../../main/resources/static/js/home-quant-planet-input.js";
import {
    REFERENCE_HERO
} from "../../main/resources/static/js/home-quant-planet-composition.js";

function createFakeTarget() {
    const listeners = new Map();
    return {
        captureCalls: [],
        releaseCalls: [],
        listeners,
        addEventListener(type, listener) {
            const group = listeners.get(type) || new Set();
            group.add(listener);
            listeners.set(type, group);
        },
        removeEventListener(type, listener) {
            listeners.get(type)?.delete(listener);
        },
        emit(type, event = {}) {
            const dispatched = {
                type,
                ...event
            };
            [...(listeners.get(type) || [])]
                .forEach((listener) => listener(dispatched));
        },
        getBoundingClientRect() {
            return { left: 0, top: 0, width: 400, height: 300 };
        },
        listenerCount() {
            return [...listeners.values()]
                .reduce((sum, group) => sum + group.size, 0);
        },
        releasePointerCapture(pointerId) {
            this.releaseCalls.push(pointerId);
        },
        setPointerCapture(pointerId) {
            this.captureCalls.push(pointerId);
        }
    };
}

function createTimerHarness() {
    const tasks = new Map();
    let nextId = 0;
    let now = 0;
    return {
        advance(milliseconds) {
            now += milliseconds;
            [...tasks.entries()]
                .filter(([, task]) => task.dueAt <= now)
                .sort((left, right) => left[1].dueAt - right[1].dueAt)
                .forEach(([id, task]) => {
                    if (tasks.delete(id)) {
                        task.callback();
                    }
                });
        },
        clearTimeout(id) {
            tasks.delete(id);
        },
        pendingCount() {
            return tasks.size;
        },
        setTimeout(callback, delay) {
            const id = ++nextId;
            tasks.set(id, {
                callback,
                dueAt: now + Math.max(0, Number(delay) || 0)
            });
            return id;
        }
    };
}

function createController(options = {}) {
    const surface = createFakeTarget();
    const windowTarget = createFakeTarget();
    const timers = createTimerHarness();
    const states = [];
    windowTarget.setTimeout = timers.setTimeout;
    windowTarget.clearTimeout = timers.clearTimeout;
    const controller = createHeroInputController({
        surface,
        windowTarget,
        onStateChange(state) {
            states.push(state);
        },
        ...options
    });
    return {
        controller,
        states,
        surface,
        timers,
        windowTarget
    };
}

function primaryPointer(pointerId, clientX, clientY = 150) {
    return {
        pointerId,
        isPrimary: true,
        button: 0,
        clientX,
        clientY
    };
}

test("connects immediately with only the single-hero controller interface", () => {
    const { controller, states, surface, windowTarget } = createController();

    assert.deepEqual(
        Object.keys(controller).sort(),
        ["destroy", "getState", "setBaseAngle"]
    );
    assert.ok(surface.listenerCount() > 0);
    assert.ok(windowTarget.listenerCount() > 0);
    assert.deepEqual(states, [{
        pointer: { x: 0, y: 0 },
        dragAngle: 0,
        dragging: false,
        settling: false
    }]);
});

test("primary pointer movement changes only pointer parallax", () => {
    const { controller, surface } = createController();

    surface.emit("pointermove", primaryPointer(7, 500, -30));

    assert.deepEqual(controller.getState(), {
        pointer: { x: 1, y: -1 },
        dragAngle: 0,
        dragging: false,
        settling: false
    });

    surface.emit("pointerleave");
    assert.deepEqual(controller.getState().pointer, { x: 0, y: 0 });
});

test("horizontal primary drag accumulates only the exact reference angle", () => {
    const { controller, surface } = createController();
    let prevented = 0;
    controller.setBaseAngle(.25);
    surface.emit("pointermove", primaryPointer(8, 300, 75));
    const pointerBeforeDrag = controller.getState().pointer;

    surface.emit("pointerdown", {
        ...primaryPointer(8, 100),
        preventDefault() {
            prevented += 1;
        }
    });
    surface.emit("pointermove", {
        ...primaryPointer(8, 250, 10),
        preventDefault() {
            prevented += 1;
        }
    });

    const state = controller.getState();
    assert.ok(Math.abs(
        state.dragAngle
            - (.25 + 150 * REFERENCE_HERO.dragRadiansPerPixel)
    ) < 1e-12);
    assert.deepEqual(state.pointer, pointerBeforeDrag);
    assert.equal(state.dragging, true);
    assert.equal(state.settling, false);
    assert.equal(prevented, 2);
});

test("release settles for 720ms without snapping the committed angle", () => {
    const { controller, surface, timers, windowTarget } = createController();
    surface.emit("pointerdown", {
        ...primaryPointer(21, 100),
        preventDefault() {}
    });
    surface.emit("pointermove", {
        ...primaryPointer(21, 250),
        preventDefault() {}
    });
    const committedAngle = 150 * REFERENCE_HERO.dragRadiansPerPixel;

    windowTarget.emit("pointerup", primaryPointer(21, 250));

    assert.deepEqual(controller.getState(), {
        pointer: { x: 0, y: 0 },
        dragAngle: committedAngle,
        dragging: false,
        settling: true
    });
    assert.notEqual(committedAngle, Math.PI / 3);
    timers.advance(REFERENCE_HERO.settleMs - 1);
    assert.equal(controller.getState().settling, true);
    assert.equal(controller.getState().dragAngle, committedAngle);
    timers.advance(1);
    assert.equal(controller.getState().settling, false);
    assert.equal(controller.getState().dragAngle, committedAngle);
    assert.deepEqual(surface.releaseCalls, [21]);
});

test("outside release, cancel, and lost capture clear only owner input", () => {
    const { controller, surface, timers, windowTarget } = createController();

    surface.emit("pointerdown", {
        ...primaryPointer(31, 100),
        preventDefault() {}
    });
    windowTarget.emit("pointerup", primaryPointer(99, 100));
    assert.equal(controller.getState().dragging, true);
    windowTarget.emit("pointerup", primaryPointer(31, 100));
    assert.equal(controller.getState().dragging, false);
    assert.equal(controller.getState().settling, true);
    timers.advance(REFERENCE_HERO.settleMs);

    surface.emit("pointerdown", {
        ...primaryPointer(32, 100),
        preventDefault() {}
    });
    windowTarget.emit("pointercancel", primaryPointer(32, 100));
    assert.equal(controller.getState().dragging, false);
    assert.equal(controller.getState().settling, false);

    surface.emit("pointerdown", {
        ...primaryPointer(33, 100),
        preventDefault() {}
    });
    surface.emit("lostpointercapture", primaryPointer(33, 100));
    assert.equal(controller.getState().dragging, false);
    assert.equal(controller.getState().settling, false);
    assert.deepEqual(surface.releaseCalls, [31, 32]);

    surface.emit("pointerdown", {
        ...primaryPointer(34, 100),
        preventDefault() {}
    });
    assert.equal(controller.getState().dragging, true);
});

test("secondary pointers never change pointer or drag angle", () => {
    const { controller, surface, windowTarget } = createController();
    let secondaryPrevented = false;

    surface.emit("pointermove", {
        ...primaryPointer(41, 400, 0),
        isPrimary: false,
        preventDefault() {
            secondaryPrevented = true;
        }
    });
    surface.emit("pointerdown", {
        ...primaryPointer(41, 100),
        isPrimary: false,
        preventDefault() {
            secondaryPrevented = true;
        }
    });
    assert.deepEqual(controller.getState(), {
        pointer: { x: 0, y: 0 },
        dragAngle: 0,
        dragging: false,
        settling: false
    });

    surface.emit("pointerdown", {
        ...primaryPointer(42, 100),
        preventDefault() {}
    });
    surface.emit("pointermove", {
        ...primaryPointer(41, 350),
        isPrimary: false,
        preventDefault() {
            secondaryPrevented = true;
        }
    });
    windowTarget.emit("pointerup", {
        ...primaryPointer(41, 350),
        isPrimary: false
    });

    assert.equal(controller.getState().dragging, true);
    assert.equal(controller.getState().dragAngle, 0);
    assert.deepEqual(controller.getState().pointer, { x: 0, y: 0 });
    assert.equal(secondaryPrevented, false);
});

test("registers no wheel, keyboard, route, or selection listeners", () => {
    const { surface, windowTarget } = createController();
    const forbidden = [
        "wheel",
        "keydown",
        "click",
        "focus",
        "blur",
        "pointerenter"
    ];

    forbidden.forEach((type) => {
        assert.equal(surface.listeners.has(type), false);
        assert.equal(windowTarget.listeners.has(type), false);
    });
});

test("capture failures cannot strand primary ownership", () => {
    const surface = createFakeTarget();
    const windowTarget = createFakeTarget();
    surface.setPointerCapture = () => {
        throw new Error("capture unavailable");
    };
    surface.releasePointerCapture = () => {
        throw new Error("capture already released");
    };
    const controller = createHeroInputController({
        surface,
        windowTarget
    });

    assert.doesNotThrow(() => surface.emit("pointerdown", {
        ...primaryPointer(51, 100),
        preventDefault() {}
    }));
    assert.equal(controller.getState().dragging, true);
    assert.doesNotThrow(() => windowTarget.emit(
        "pointercancel",
        primaryPointer(51, 100)
    ));
    assert.equal(controller.getState().dragging, false);
});

test("destroy removes listeners, timers, and active pointer ownership once", () => {
    const { controller, surface, timers, windowTarget } = createController();
    surface.emit("pointerdown", {
        ...primaryPointer(61, 100),
        preventDefault() {}
    });

    controller.destroy();
    controller.destroy();

    assert.equal(surface.listenerCount(), 0);
    assert.equal(windowTarget.listenerCount(), 0);
    assert.equal(timers.pendingCount(), 0);
    assert.deepEqual(surface.releaseCalls, [61]);
    assert.deepEqual(controller.getState(), {
        pointer: { x: 0, y: 0 },
        dragAngle: 0,
        dragging: false,
        settling: false
    });

    surface.emit("pointermove", primaryPointer(62, 400, 0));
    assert.deepEqual(controller.getState().pointer, { x: 0, y: 0 });
});
