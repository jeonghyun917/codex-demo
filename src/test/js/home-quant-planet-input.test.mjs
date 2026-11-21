import assert from "node:assert/strict";
import test from "node:test";
import {
    createHeroInputController
} from "../../main/resources/static/js/home-quant-planet-input.js";

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

function createController(options = {}) {
    const surface = createFakeTarget();
    const windowTarget = createFakeTarget();
    const states = [];
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
        ["destroy", "getState"]
    );
    assert.ok(surface.listenerCount() > 0);
    assert.ok(windowTarget.listenerCount() > 0);
    assert.deepEqual(states, [{
        pointer: { x: 0, y: 0 },
        touching: false
    }]);
});

test("primary pointer movement changes only pointer parallax", () => {
    const { controller, surface } = createController();

    surface.emit("pointermove", primaryPointer(7, 500, -30));

    assert.deepEqual(controller.getState(), {
        pointer: { x: 1, y: -1 },
        touching: false
    });

    surface.emit("pointerleave", primaryPointer(7, 500, -30));
    assert.deepEqual(controller.getState().pointer, { x: 0, y: 0 });
});

test("secondary pointer leave preserves primary parallax", () => {
    const { controller, surface } = createController();
    surface.emit("pointermove", primaryPointer(9, 350, 75));

    surface.emit("pointerleave", {
        ...primaryPointer(10, 0, 300),
        isPrimary: false
    });

    assert.deepEqual(controller.getState(), {
        pointer: { x: .75, y: -.5 },
        touching: false
    });
});

test("mouse down and drag report pointer position without a drag angle", () => {
    const { controller, surface } = createController();
    surface.emit("pointerdown", {
        ...primaryPointer(7, 100, 75),
        button: 0,
        pointerType: "mouse"
    });
    surface.emit("pointermove", {
        ...primaryPointer(7, 400, 225),
        pointerType: "mouse"
    });

    const state = controller.getState();
    assert.deepEqual(state, {
        pointer: { x: 1, y: .5 },
        touching: false
    });
    assert.deepEqual(surface.captureCalls, []);
});

test("primary touch resets glyph target on release", () => {
    const { controller, surface } = createController();
    surface.emit("pointerdown", {
        ...primaryPointer(8, 300, 75),
        button: 0,
        pointerType: "touch"
    });
    assert.equal(controller.getState().touching, true);
    surface.emit("pointerup", {
        ...primaryPointer(8, 300, 75),
        pointerType: "touch"
    });
    assert.deepEqual(controller.getState(), {
        pointer: { x: 0, y: 0 },
        touching: false
    });
});

test("secondary pointers never change pointer target", () => {
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
        touching: false
    });

    surface.emit("pointerdown", {
        ...primaryPointer(42, 100, 150),
        pointerType: "touch"
    });
    surface.emit("pointermove", {
        ...primaryPointer(41, 350),
        isPrimary: false,
        preventDefault() {
            secondaryPrevented = true;
        }
    });
    surface.emit("pointerup", {
        ...primaryPointer(41, 350),
        isPrimary: false
    });

    assert.equal(controller.getState().touching, true);
    assert.deepEqual(controller.getState().pointer, { x: -.5, y: 0 });
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

test("destroy removes listeners and active pointer ownership once", () => {
    const { controller, surface, windowTarget } = createController();
    surface.emit("pointerdown", {
        ...primaryPointer(61, 100, 150),
        pointerType: "touch"
    });

    controller.destroy();
    controller.destroy();

    assert.equal(surface.listenerCount(), 0);
    assert.equal(windowTarget.listenerCount(), 0);
    assert.deepEqual(controller.getState(), {
        pointer: { x: 0, y: 0 },
        touching: false
    });

    surface.emit("pointermove", primaryPointer(62, 400, 0));
    assert.deepEqual(controller.getState().pointer, { x: 0, y: 0 });
});
