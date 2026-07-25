import assert from "node:assert/strict";
import test from "node:test";
import {
    createOrbitInputController
} from "../../main/resources/static/js/home-quant-planet-input.js";

function createFakeTarget() {
    const listeners = new Map();
    return {
        listeners,
        captureCalls: [],
        releaseCalls: [],
        addEventListener(type, listener) {
            const group = listeners.get(type) || new Set();
            group.add(listener);
            listeners.set(type, group);
        },
        removeEventListener(type, listener) {
            listeners.get(type)?.delete(listener);
        },
        emit(type, event = {}) {
            [...(listeners.get(type) || [])]
                .forEach((listener) => listener(event));
        },
        listenerCount() {
            return [...listeners.values()]
                .reduce((sum, group) => sum + group.size, 0);
        },
        getBoundingClientRect() {
            return { left: 0, top: 0, width: 400, height: 300 };
        },
        setPointerCapture(pointerId) {
            this.captureCalls.push(pointerId);
        },
        releasePointerCapture(pointerId) {
            this.releaseCalls.push(pointerId);
        }
    };
}

function createFocusableTarget() {
    const target = createFakeTarget();
    target.focusCalls = 0;
    target.focus = () => {
        target.focusCalls += 1;
    };
    return target;
}

function createController() {
    const surface = createFakeTarget();
    const links = Array.from({ length: 6 }, createFocusableTarget);
    const states = [];
    const controller = createOrbitInputController({
        surface,
        links,
        onChange(state) {
            states.push(state);
        }
    });
    return { controller, links, states, surface };
}

test("captures primary-pointer drag without registering wheel behavior", () => {
    const { controller, states, surface } = createController();
    controller.connect();

    assert.equal(surface.listeners.has("wheel"), false);
    surface.emit("pointerdown", {
        pointerId: 4,
        clientX: 100,
        button: 0,
        target: surface,
        preventDefault() {}
    });
    surface.emit("pointermove", {
        pointerId: 4,
        clientX: 3000,
        clientY: 150,
        preventDefault() {}
    });
    surface.emit("pointerup", { pointerId: 4 });

    assert.deepEqual(surface.captureCalls, [4]);
    assert.deepEqual(surface.releaseCalls, [4]);
    assert.ok(states.some((state) => state.dragging));
    assert.equal(controller.getState().dragging, false);
    assert.equal(controller.getState().dragAngle, Math.PI * 4);
});

test("updates bounded pointer parallax without changing the orbit outside a drag", () => {
    const { controller, surface } = createController();
    controller.connect();

    surface.emit("pointermove", {
        pointerId: 7,
        clientX: 500,
        clientY: -30,
        preventDefault() {}
    });
    assert.deepEqual(controller.getState().pointer, { x: 1, y: -1 });
    assert.equal(controller.getState().dragAngle, 0);

    surface.emit("pointerleave");
    assert.deepEqual(controller.getState().pointer, { x: 0, y: 0 });
});

test("leaves native product-link clicks out of drag capture", () => {
    const { controller, links, surface } = createController();
    controller.connect();

    surface.emit("pointerdown", {
        pointerId: 8,
        clientX: 120,
        button: 0,
        target: links[2],
        preventDefault() {
            assert.fail("native link activation must not be prevented");
        }
    });

    assert.equal(controller.getState().dragging, false);
    assert.deepEqual(surface.captureCalls, []);
});

test("wraps arrow-key focus through all six product links", () => {
    const { controller, links } = createController();
    controller.connect();

    links[5].emit("keydown", {
        key: "ArrowRight",
        preventDefault() {}
    });
    links[0].emit("keydown", {
        key: "ArrowLeft",
        preventDefault() {}
    });

    assert.equal(links[0].focusCalls, 1);
    assert.equal(links[5].focusCalls, 1);
});

test("pauses and snaps while link focus or hover remains inside the orbit", () => {
    const { controller, links } = createController();
    controller.connect();

    links[2].emit("focus");
    assert.equal(controller.getState().selectedIndex, 2);
    assert.equal(controller.getState().paused, true);
    assert.equal(controller.getState().snapToSelected, true);

    links[2].emit("blur", { relatedTarget: links[4] });
    assert.equal(controller.getState().paused, true);
    links[4].emit("blur", { relatedTarget: null });
    assert.equal(controller.getState().paused, false);

    links[3].emit("pointerenter");
    assert.equal(controller.getState().selectedIndex, 3);
    assert.equal(controller.getState().paused, true);
    links[3].emit("pointerleave", { relatedTarget: links[1] });
    assert.equal(controller.getState().paused, true);
    links[1].emit("pointerleave", { relatedTarget: null });
    assert.equal(controller.getState().paused, false);
});

test("destroy removes every listener and releases an active pointer", () => {
    const { controller, links, surface } = createController();
    controller.connect();
    controller.connect();
    surface.emit("pointerdown", {
        pointerId: 9,
        clientX: 100,
        button: 0,
        target: surface,
        preventDefault() {}
    });

    controller.destroy();

    assert.equal(surface.listenerCount(), 0);
    assert.ok(links.every((link) => link.listenerCount() === 0));
    assert.deepEqual(surface.releaseCalls, [9]);
    assert.equal(controller.getState().dragging, false);
});

test("capture API failures cannot strand drag state or listeners", () => {
    const { controller, links, surface } = createController();
    surface.setPointerCapture = () => {
        throw new Error("capture unavailable");
    };
    surface.releasePointerCapture = () => {
        throw new Error("capture already released");
    };
    controller.connect();

    assert.doesNotThrow(() => surface.emit("pointerdown", {
        pointerId: 10,
        clientX: 100,
        button: 0,
        target: surface,
        preventDefault() {}
    }));
    assert.equal(controller.getState().dragging, true);
    assert.doesNotThrow(() => controller.destroy());
    assert.equal(controller.getState().dragging, false);
    assert.equal(surface.listenerCount(), 0);
    assert.ok(links.every((link) => link.listenerCount() === 0));
});
