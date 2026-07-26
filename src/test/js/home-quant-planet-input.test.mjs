import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";
import {
    createOrbitInputController
} from "../../main/resources/static/js/home-quant-planet-input.js";
import {
    createProductSelection,
    REFERENCE_TIMING,
    resolveSelectedProduct
} from "../../main/resources/static/js/home-quant-planet-composition.js";

function createFakeTarget() {
    const listeners = new Map();
    return {
        children: [],
        listeners,
        nodeType: 1,
        parentNode: null,
        captureCalls: [],
        releaseCalls: [],
        appendChild(child) {
            child.parentNode = this;
            this.children.push(child);
            return child;
        },
        contains(target) {
            if (!target?.nodeType) {
                throw new TypeError("contains expects a Node");
            }
            for (let node = target; node; node = node.parentNode) {
                if (node === this) {
                    return true;
                }
            }
            return false;
        },
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

function createFocusableTarget(href) {
    const target = createFakeTarget();
    target.href = href;
    target.focusCalls = 0;
    target.focus = () => {
        target.focusCalls += 1;
        target.emit("focus");
    };
    return target;
}

function dispatchBubbling(target, type, event = {}) {
    const path = [];
    for (let node = target; node; node = node.parentNode) {
        path.push(node);
    }
    let defaultPrevented = false;
    let propagationStopped = false;
    const dispatched = {
        ...event,
        target,
        composedPath: () => [...path, { kind: "window" }],
        preventDefault() {
            defaultPrevented = true;
            event.preventDefault?.();
        },
        stopPropagation() {
            propagationStopped = true;
            event.stopPropagation?.();
        }
    };
    for (const currentTarget of path) {
        dispatched.currentTarget = currentTarget;
        currentTarget.emit(type, dispatched);
        if (propagationStopped) {
            break;
        }
    }
    return {
        get defaultPrevented() {
            return defaultPrevented;
        },
        get propagationStopped() {
            return propagationStopped;
        }
    };
}

function createTimerHarness() {
    const tasks = new Map();
    let nextId = 0;
    let time = 0;
    return {
        clearTimeoutFn(id) {
            tasks.delete(id);
        },
        setTimeoutFn(callback, delay) {
            const id = ++nextId;
            tasks.set(id, {
                callback,
                dueAt: time + Math.max(0, Number(delay) || 0)
            });
            return id;
        },
        advance(milliseconds) {
            time += milliseconds;
            [...tasks.entries()]
                .filter(([, task]) => task.dueAt <= time)
                .sort((left, right) => left[1].dueAt - right[1].dueAt)
                .forEach(([id, task]) => {
                    if (!tasks.delete(id)) {
                        return;
                    }
                    task.callback();
                });
        },
        pendingCount() {
            return tasks.size;
        }
    };
}

function createController(options = {}) {
    const surface = createFakeTarget();
    const terminalTarget = options.terminalTarget || createFakeTarget();
    const links = Array.from({ length: 6 }, (_, index) =>
        surface.appendChild(createFocusableTarget(
            resolveSelectedProduct(index).route
        )));
    const commits = [];
    const states = [];
    const timers = options.timers || createTimerHarness();
    const controller = createOrbitInputController({
        surface,
        terminalTarget,
        links,
        clearTimeoutFn: timers.clearTimeoutFn,
        onCommit(selection) {
            commits.push(selection);
        },
        onChange(state) {
            states.push(state);
        },
        setTimeoutFn: timers.setTimeoutFn,
        ...options
    });
    return {
        commits,
        controller,
        links,
        states,
        surface,
        terminalTarget,
        timers
    };
}

test("captures primary-pointer drag without registering wheel behavior", () => {
    const { controller, states, surface } = createController();
    controller.connect();

    assert.equal(surface.listeners.has("wheel"), false);
    surface.emit("pointerdown", {
        pointerId: 4,
        isPrimary: true,
        clientX: 100,
        button: 0,
        target: surface,
        preventDefault() {}
    });
    assert.deepEqual(surface.captureCalls, []);
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
        isPrimary: true,
        clientX: 500,
        clientY: -30,
        preventDefault() {}
    });
    assert.deepEqual(controller.getState().pointer, { x: 1, y: -1 });
    assert.equal(controller.getState().dragAngle, 0);

    surface.emit("pointerleave");
    assert.deepEqual(controller.getState().pointer, { x: 0, y: 0 });
});

test("declares direct touch ownership on the interaction surface", async () => {
    const cssUrl = new URL(
        "../../main/resources/static/css/home-quant-planet.css",
        import.meta.url
    );
    const css = await readFile(cssUrl, "utf8");
    const interactionRule = css.match(/\.planet-experience\s*\{([^}]*)\}/);

    assert.ok(interactionRule, "planet interaction surface rule must exist");
    assert.match(interactionRule[1], /touch-action\s*:\s*none\s*;/);
});

test("preserves native anchor and nested-title clicks without pointer capture", () => {
    const { controller, links, surface } = createController();
    controller.connect();
    const nestedLine = links[2].appendChild(createFakeTarget());
    const clickTargets = [links[2], nestedLine];

    clickTargets.forEach((target, index) => {
        const pointerId = 8 + index;
        const down = dispatchBubbling(target, "pointerdown", {
            pointerId,
            isPrimary: true,
            clientX: 120,
            button: 0
        });
        assert.equal(down.defaultPrevented, false);
        assert.deepEqual(surface.captureCalls, []);

        dispatchBubbling(target, "pointerup", {
            pointerId,
            isPrimary: true
        });
        const click = dispatchBubbling(target, "click", { detail: 1 });
        assert.equal(click.defaultPrevented, false);
        assert.equal(links[2].href, "/stocks/heatmap?index=SP500");
    });

    assert.equal(controller.getState().dragging, false);
    assert.deepEqual(surface.releaseCalls, []);
});

test("arrow keys wrap, promote, and focus the adjacent product title", () => {
    const { commits, controller, links } = createController();
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
    assert.equal(controller.getState().selectedIndex, 5);
    assert.deepEqual(
        commits.map((selection) => selection.selectedIndex),
        [0, 5]
    );
});

test("commits a horizontal primary drag to the nearest wrapped sector", () => {
    const { commits, controller, surface } = createController();
    controller.connect();

    surface.emit("pointerdown", {
        pointerId: 31,
        isPrimary: true,
        clientX: 100,
        button: 0,
        target: surface,
        preventDefault() {}
    });
    surface.emit("pointermove", {
        pointerId: 31,
        isPrimary: true,
        clientX: 300,
        clientY: 150,
        preventDefault() {}
    });
    surface.emit("pointerup", { pointerId: 31, isPrimary: true });

    assert.equal(commits.length, 1);
    assert.equal(commits[0].selectedIndex, 5);
    assert.ok(Math.abs(commits[0].dragAngle - Math.PI / 3) < 1e-9);
    assert.equal(controller.getState().selectedIndex, 5);
    assert.equal(controller.getState().dragAngle, commits[0].dragAngle);
});

test("snaps against the displayed base ring phase instead of local drag alone", () => {
    const baseAngle = .8;
    const { commits, controller, surface } = createController({
        getBaseAngle: () => baseAngle
    });
    controller.connect();

    surface.emit("pointerdown", {
        pointerId: 36,
        isPrimary: true,
        clientX: 100,
        button: 0,
        target: surface,
        preventDefault() {}
    });
    surface.emit("pointermove", {
        pointerId: 36,
        isPrimary: true,
        clientX: 110,
        clientY: 150,
        preventDefault() {}
    });
    surface.emit("pointerup", { pointerId: 36, isPrimary: true });

    assert.equal(commits.at(-1).selectedIndex, 5);
    assert.ok(Math.abs(
        baseAngle + commits.at(-1).dragAngle - Math.PI / 3
    ) < 1e-9);
});

test("keeps keyboard promotion sector-aligned at bounded drag extremes", () => {
    const { commits, controller, links, surface } = createController();
    controller.connect();
    surface.emit("pointerdown", {
        pointerId: 34,
        isPrimary: true,
        clientX: 100,
        button: 0,
        target: surface,
        preventDefault() {}
    });
    surface.emit("pointermove", {
        pointerId: 34,
        isPrimary: true,
        clientX: 3000,
        clientY: 150,
        preventDefault() {}
    });
    surface.emit("pointerup", { pointerId: 34, isPrimary: true });

    links[0].emit("keydown", {
        key: "ArrowLeft",
        preventDefault() {}
    });
    const commit = commits.at(-1);
    const snapped = createProductSelection({
        dragAngle: commit.dragAngle,
        selectedIndex: 0,
        snap: true,
        count: 6
    });

    assert.equal(commit.selectedIndex, 5);
    assert.equal(snapped.selectedIndex, commit.selectedIndex);
});

test("captures only after title drag threshold and suppresses its retargeted click", () => {
    const { commits, controller, links, surface } = createController();
    controller.connect();
    const nestedLine = links[2].appendChild(createFakeTarget());

    const down = dispatchBubbling(nestedLine, "pointerdown", {
        pointerId: 32,
        isPrimary: true,
        clientX: 100,
        button: 0
    });
    assert.equal(down.defaultPrevented, false);
    assert.deepEqual(surface.captureCalls, []);

    const belowThreshold = dispatchBubbling(nestedLine, "pointermove", {
        pointerId: 32,
        isPrimary: true,
        clientX: 104,
        clientY: 150,
    });
    assert.equal(belowThreshold.defaultPrevented, false);
    assert.deepEqual(surface.captureCalls, []);

    const drag = dispatchBubbling(nestedLine, "pointermove", {
        pointerId: 32,
        isPrimary: true,
        clientX: 300,
        clientY: 150
    });
    assert.equal(drag.defaultPrevented, true);
    assert.deepEqual(surface.captureCalls, [32]);

    surface.emit("pointerup", { pointerId: 32, isPrimary: true });
    const retargetedClick = dispatchBubbling(surface, "click", { detail: 1 });
    const nextClick = dispatchBubbling(links[3], "click", { detail: 1 });

    assert.equal(retargetedClick.defaultPrevented, true);
    assert.equal(retargetedClick.propagationStopped, true);
    assert.equal(nextClick.defaultPrevented, false);
    assert.equal(commits.length, 1);
});

test("ends pointer ownership outside the surface and accepts the next drag", () => {
    const {
        controller,
        links,
        surface,
        terminalTarget
    } = createController();
    controller.connect();
    const nestedLine = links[2].appendChild(createFakeTarget());

    dispatchBubbling(nestedLine, "pointerdown", {
        pointerId: 41,
        isPrimary: true,
        clientX: 100,
        button: 0
    });
    dispatchBubbling(nestedLine, "pointermove", {
        pointerId: 41,
        isPrimary: true,
        clientX: 106,
        clientY: 150
    });
    surface.emit("pointerleave", { pointerId: 41, isPrimary: true });
    assert.equal(controller.getState().dragging, true);
    assert.deepEqual(surface.captureCalls, []);

    terminalTarget.emit("pointerup", {
        type: "pointerup",
        pointerId: 41,
        isPrimary: true
    });
    assert.equal(controller.getState().dragging, false);
    assert.equal(controller.getState().pressed, false);

    surface.emit("pointerdown", {
        pointerId: 42,
        isPrimary: true,
        clientX: 100,
        button: 0,
        target: surface
    });
    terminalTarget.emit("pointercancel", {
        type: "pointercancel",
        pointerId: 42,
        isPrimary: true
    });
    assert.equal(controller.getState().dragging, false);

    surface.emit("pointerdown", {
        pointerId: 43,
        isPrimary: true,
        clientX: 100,
        button: 0,
        target: surface
    });
    surface.emit("pointermove", {
        pointerId: 43,
        isPrimary: true,
        clientX: 200,
        clientY: 150,
        preventDefault() {}
    });
    assert.deepEqual(surface.captureCalls, [43]);
    surface.emit("lostpointercapture", {
        type: "lostpointercapture",
        pointerId: 43,
        isPrimary: true
    });
    assert.equal(controller.getState().dragging, false);
    assert.deepEqual(surface.releaseCalls, []);

    surface.emit("pointerdown", {
        pointerId: 44,
        isPrimary: true,
        clientX: 100,
        button: 0,
        target: surface
    });
    assert.equal(controller.getState().dragging, true);
    terminalTarget.emit("pointercancel", {
        type: "pointercancel",
        pointerId: 44,
        isPrimary: true
    });
    assert.equal(controller.getState().dragging, false);
});

test("delays idle auto-rotation until the reference resume interval", () => {
    const timers = createTimerHarness();
    const { controller, links, surface } = createController({ timers });
    controller.connect();

    links[1].emit("pointerenter");
    assert.equal(controller.getState().paused, true);
    links[1].emit("pointerleave", { relatedTarget: null });
    assert.equal(controller.getState().paused, true);
    assert.equal(controller.getState().snapToSelected, true);
    assert.equal(timers.pendingCount(), 1);

    timers.advance(REFERENCE_TIMING.idleResumeMs - 1);
    assert.equal(controller.getState().paused, true);
    timers.advance(1);
    assert.equal(controller.getState().paused, false);
    assert.equal(controller.getState().snapToSelected, false);

    surface.emit("pointerdown", {
        pointerId: 33,
        isPrimary: true,
        clientX: 100,
        button: 0,
        target: surface,
        preventDefault() {}
    });
    assert.equal(controller.getState().paused, true);
    surface.emit("pointerup", { pointerId: 33, isPrimary: true });
    assert.equal(controller.getState().paused, true);
    timers.advance(REFERENCE_TIMING.idleResumeMs);
    assert.equal(controller.getState().paused, false);
});

test("pauses and snaps while link focus or hover remains inside the orbit", () => {
    const { controller, links, timers } = createController();
    controller.connect();

    links[2].emit("focus");
    assert.equal(controller.getState().selectedIndex, 2);
    assert.equal(controller.getState().paused, true);
    assert.equal(controller.getState().snapToSelected, true);

    links[2].emit("blur", { relatedTarget: links[4] });
    assert.equal(controller.getState().paused, true);
    links[4].emit("blur", { relatedTarget: null });
    assert.equal(controller.getState().paused, true);
    timers.advance(REFERENCE_TIMING.idleResumeMs);
    assert.equal(controller.getState().paused, false);

    links[3].emit("pointerenter");
    assert.equal(controller.getState().selectedIndex, 3);
    assert.equal(controller.getState().paused, true);
    links[3].emit("pointerleave", { relatedTarget: links[1] });
    assert.equal(controller.getState().paused, true);
    links[1].emit("pointerleave", { relatedTarget: null });
    assert.equal(controller.getState().paused, true);
    timers.advance(REFERENCE_TIMING.idleResumeMs);
    assert.equal(controller.getState().paused, false);
});

test("destroy removes every listener and releases an active pointer", () => {
    const { controller, links, surface, terminalTarget } = createController();
    controller.connect();
    controller.connect();
    surface.emit("pointerdown", {
        pointerId: 9,
        isPrimary: true,
        clientX: 100,
        button: 0,
        target: surface,
        preventDefault() {}
    });
    surface.emit("pointermove", {
        pointerId: 9,
        isPrimary: true,
        clientX: 200,
        clientY: 150,
        preventDefault() {}
    });

    controller.destroy();

    assert.equal(surface.listenerCount(), 0);
    assert.equal(terminalTarget.listenerCount(), 0);
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
        isPrimary: true,
        clientX: 100,
        button: 0,
        target: surface,
        preventDefault() {}
    }));
    assert.doesNotThrow(() => surface.emit("pointermove", {
        pointerId: 10,
        isPrimary: true,
        clientX: 200,
        clientY: 150,
        preventDefault() {}
    }));
    assert.equal(controller.getState().dragging, true);
    assert.doesNotThrow(() => controller.destroy());
    assert.equal(controller.getState().dragging, false);
    assert.equal(surface.listenerCount(), 0);
    assert.ok(links.every((link) => link.listenerCount() === 0));
});

test("commit callback failures still release primary-pointer ownership", () => {
    const { controller, surface } = createController({
        onCommit() {
            throw new Error("commit failed");
        }
    });
    controller.connect();
    surface.emit("pointerdown", {
        pointerId: 35,
        isPrimary: true,
        clientX: 100,
        button: 0,
        target: surface,
        preventDefault() {}
    });
    surface.emit("pointermove", {
        pointerId: 35,
        isPrimary: true,
        clientX: 300,
        clientY: 150,
        preventDefault() {}
    });

    assert.throws(
        () => surface.emit("pointerup", {
            pointerId: 35,
            isPrimary: true
        }),
        /commit failed/
    );
    assert.deepEqual(surface.releaseCalls, [35]);
    assert.equal(controller.getState().dragging, false);
});

test("keeps the active primary drag when a non-primary pointer joins", () => {
    const { controller, surface } = createController();
    controller.connect();

    surface.emit("pointerdown", {
        pointerId: 11,
        isPrimary: true,
        clientX: 100,
        button: 0,
        target: surface,
        preventDefault() {}
    });
    surface.emit("pointerdown", {
        pointerId: 12,
        isPrimary: false,
        clientX: 350,
        button: 0,
        target: surface,
        preventDefault() {
            assert.fail("a non-primary pointer must be ignored");
        }
    });
    surface.emit("pointermove", {
        pointerId: 12,
        clientX: 400,
        clientY: 150,
        preventDefault() {
            assert.fail("a non-primary pointer must not drag");
        }
    });
    assert.equal(controller.getState().dragAngle, 0);
    assert.deepEqual(controller.getState().pointer, { x: 0, y: 0 });
    assert.equal(controller.getState().dragging, true);
    assert.deepEqual(surface.captureCalls, []);

    surface.emit("pointermove", {
        pointerId: 11,
        clientX: 200,
        clientY: 150,
        preventDefault() {}
    });
    assert.deepEqual(surface.captureCalls, [11]);
    surface.emit("pointerup", { pointerId: 12, isPrimary: false });
    assert.equal(controller.getState().dragging, true);
    assert.equal(controller.getState().dragAngle, .6);
    surface.emit("pointerup", { pointerId: 11, isPrimary: true });
    assert.equal(controller.getState().dragging, false);
});

test("ignores non-primary pointer movement when no drag owns input", () => {
    const { controller, states, surface } = createController();
    controller.connect();
    const initialStateCount = states.length;

    surface.emit("pointermove", {
        pointerId: 51,
        isPrimary: false,
        clientX: 400,
        clientY: 0,
        preventDefault() {
            assert.fail("secondary movement must remain native");
        }
    });

    assert.deepEqual(controller.getState().pointer, { x: 0, y: 0 });
    assert.equal(states.length, initialStateCount);
});

test("pauses for a native-link press without preventing activation", () => {
    const { controller, links, surface, timers } = createController();
    controller.connect();
    let prevented = false;

    links[2].emit("pointerdown", {
        pointerId: 13,
        isPrimary: true,
        button: 0,
        target: links[2],
        preventDefault() {
            prevented = true;
        }
    });

    assert.equal(controller.getState().pressed, true);
    assert.equal(controller.getState().selectedIndex, 2);
    assert.equal(controller.getState().paused, true);
    assert.equal(prevented, false);
    assert.deepEqual(surface.captureCalls, []);

    links[2].emit("pointerup", { pointerId: 13, isPrimary: true });
    assert.equal(controller.getState().pressed, false);
    assert.equal(controller.getState().paused, true);
    timers.advance(REFERENCE_TIMING.idleResumeMs);
    assert.equal(controller.getState().paused, false);

    links[4].emit("pointerdown", {
        pointerId: 14,
        isPrimary: true,
        button: 0,
        target: links[4]
    });
    links[4].emit("pointercancel", {
        pointerId: 14,
        isPrimary: true
    });
    assert.equal(controller.getState().pressed, false);
    timers.advance(REFERENCE_TIMING.idleResumeMs);
    assert.equal(controller.getState().paused, false);
});
