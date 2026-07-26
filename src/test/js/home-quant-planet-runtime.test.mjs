import assert from "node:assert/strict";
import test from "node:test";
import {
    createQuantPlanetRuntime,
    resolveQuantPlanetCapabilities
} from "../../main/resources/static/js/home-quant-planet-runtime.js";
import {
    REFERENCE_HERO
} from "../../main/resources/static/js/home-quant-planet-composition.js";

const HIGH_PROFILE = {
    name: "high",
    webgl: true,
    pixelRatio: 1.5,
    pointerEnabled: true,
    shadows: true,
    outlineLevel: 2,
    foliageCount: 34,
    cloudCount: 8,
    planetScale: 1
};

const STATIC_PROFILE = {
    name: "static",
    webgl: false,
    pixelRatio: 1,
    pointerEnabled: false,
    shadows: false,
    outlineLevel: 0,
    foliageCount: 0,
    cloudCount: 0,
    planetScale: 1
};

const DEFAULT_INPUT_STATE = {
    pointer: { x: 0, y: 0 },
    dragAngle: 0,
    dragging: false,
    settling: false
};

function createStyle() {
    const properties = new Map();
    return {
        setProperty(name, value) {
            properties.set(name, String(value));
        },
        getPropertyValue(name) {
            return properties.get(name) || "";
        },
        entries() {
            return [...properties.entries()];
        }
    };
}

function createClassList() {
    const values = new Set();
    return {
        add(...names) {
            names.forEach((name) => values.add(name));
        },
        remove(...names) {
            names.forEach((name) => values.delete(name));
        },
        contains(name) {
            return values.has(name);
        }
    };
}

function createEventTarget(extra = {}) {
    const listeners = new Map();
    return {
        ...extra,
        classList: extra.classList || createClassList(),
        dataset: extra.dataset || {},
        style: extra.style || createStyle(),
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
            [...(listeners.get(type) || [])]
                .forEach((listener) => listener(event));
        },
        listenerCount(type) {
            return listeners.get(type)?.size || 0;
        }
    };
}

function createDeferred() {
    let resolve;
    let reject;
    const promise = new Promise((resolvePromise, rejectPromise) => {
        resolve = resolvePromise;
        reject = rejectPromise;
    });
    return { promise, reject, resolve };
}

function createTimerHarness() {
    const tasks = new Map();
    let nextId = 0;
    let time = 0;
    const clearedIds = [];
    return {
        clearedIds,
        clearTimeout(id) {
            if (tasks.delete(id)) {
                clearedIds.push(id);
            }
        },
        setTimeout(callback, delay) {
            const id = ++nextId;
            tasks.set(id, {
                callback,
                dueAt: time + Math.max(0, Number(delay) || 0)
            });
            return id;
        },
        pendingCount() {
            return tasks.size;
        },
        advance(milliseconds) {
            time += milliseconds;
            [...tasks.entries()]
                .filter(([, task]) => task.dueAt <= time)
                .sort((left, right) => left[1].dueAt - right[1].dueAt)
                .forEach(([id, task]) => {
                    if (tasks.delete(id)) {
                        task.callback();
                    }
                });
        }
    };
}

function createFrameQueue() {
    const frames = new Map();
    const cancelledIds = [];
    let nextId = 0;
    return {
        cancelledIds,
        frames,
        requestFrame(callback) {
            const id = ++nextId;
            frames.set(id, callback);
            return id;
        },
        cancelFrame(id) {
            if (frames.delete(id)) {
                cancelledIds.push(id);
            }
        },
        runNext(time) {
            const next = frames.entries().next().value;
            assert.ok(next, "expected one queued animation frame");
            frames.delete(next[0]);
            next[1](time);
        }
    };
}

function createScene() {
    return {
        disposeCalls: 0,
        poses: [],
        resizeCalls: 0,
        visibleValues: [],
        dispose() {
            this.disposeCalls += 1;
        },
        getDiagnostics() {
            return { drawCalls: 43, triangles: 7155 };
        },
        render(pose) {
            this.poses.push(structuredClone(pose));
            return true;
        },
        resize() {
            this.resizeCalls += 1;
        },
        setVisible(value) {
            this.visibleValues.push(value);
        }
    };
}

function createInputHarness() {
    let onStateChange = null;
    return {
        createCalls: 0,
        destroyCalls: 0,
        surface: null,
        createInput({ surface, onStateChange: callback }) {
            this.createCalls += 1;
            this.surface = surface;
            onStateChange = callback;
            callback({ ...DEFAULT_INPUT_STATE });
            return {
                destroy: () => {
                    this.destroyCalls += 1;
                },
                getState: () => ({ ...DEFAULT_INPUT_STATE }),
                setBaseAngle() {}
            };
        },
        emit(state) {
            assert.equal(typeof onStateChange, "function");
            onStateChange({
                ...DEFAULT_INPUT_STATE,
                ...state,
                pointer: {
                    ...DEFAULT_INPUT_STATE.pointer,
                    ...state.pointer
                }
            });
        }
    };
}

function createMotionHarness() {
    const animations = [];
    return {
        animations,
        Motion: {
            animate(element, keyframes, options) {
                const animation = {
                    cancelCalls: 0,
                    cancel() {
                        this.cancelCalls += 1;
                    }
                };
                animations.push({
                    animation,
                    element,
                    keyframes,
                    options
                });
                return animation;
            }
        }
    };
}

function createRuntimeHarness({
    profile = HIGH_PROFILE,
    motionDisabled = profile.name === "static",
    timers = createTimerHarness(),
    motion = createMotionHarness()
} = {}) {
    const root = createEventTarget();
    const experience = createEventTarget();
    const loader = createEventTarget();
    const sceneElement = createEventTarget();
    const canvas = createEventTarget({ dataset: {} });
    const fallback = createEventTarget();
    const title = createEventTarget({
        src: "/img/home-messenger-reference-title.png"
    });
    const action = createEventTarget({
        href: "/quant",
        textContent: "ENTER"
    });
    const documentTarget = createEventTarget({ hidden: false });
    const windowTarget = createEventTarget({
        clearTimeout: timers.clearTimeout,
        innerHeight: 900,
        innerWidth: 1440,
        Motion: motion.Motion,
        setTimeout: timers.setTimeout
    });
    const frameQueue = createFrameQueue();
    const input = createInputHarness();
    const sceneDeferred = createDeferred();
    let now = 0;
    let sceneConstructionCalls = 0;
    const runtime = createQuantPlanetRuntime({
        root,
        experience,
        loader,
        sceneElement,
        canvas,
        fallback,
        title,
        action,
        profile,
        motionDisabled,
        isLoopback: true,
        createScene() {
            sceneConstructionCalls += 1;
            return sceneDeferred.promise;
        },
        createInput: input.createInput.bind(input),
        requestFrame: frameQueue.requestFrame,
        cancelFrame: frameQueue.cancelFrame,
        now: () => now,
        documentTarget,
        windowTarget
    });
    return {
        action,
        canvas,
        documentTarget,
        experience,
        fallback,
        frameQueue,
        input,
        loader,
        motion,
        root,
        runtime,
        sceneConstructionCalls: () => sceneConstructionCalls,
        sceneDeferred,
        sceneElement,
        setNow(value) {
            now = value;
        },
        timers,
        title,
        windowTarget
    };
}

const flushPromises = () => new Promise((resolve) => setImmediate(resolve));

function assertCompleteFallback(harness) {
    assert.equal(harness.root.dataset.introPhase, "ready");
    assert.equal(
        harness.experience.style.getPropertyValue("--intro-world-progress"),
        "1"
    );
    assert.equal(
        harness.experience.style.getPropertyValue("--intro-title-progress"),
        "1"
    );
    assert.equal(
        harness.experience.style.getPropertyValue("--intro-action-progress"),
        "1"
    );
    assert.deepEqual(harness.root.style.entries(), []);
    assert.equal(harness.root.classList.contains("planet-intro-ready"), true);
    assert.equal(
        harness.title.src,
        "/img/home-messenger-reference-title.png"
    );
    assert.equal(harness.action.href, "/quant");
    assert.equal(harness.action.textContent, "ENTER");
}

test("creates one scene and leaves the runtime as the only RAF owner", async () => {
    const harness = createRuntimeHarness();
    assert.equal(harness.sceneConstructionCalls(), 1);
    assert.equal(harness.frameQueue.frames.size, 1);

    const scene = createScene();
    harness.sceneDeferred.resolve(scene);
    await flushPromises();

    assert.equal(harness.sceneConstructionCalls(), 1);
    assert.equal(harness.frameQueue.frames.size, 1);
    harness.frameQueue.runNext(16);
    assert.equal(scene.poses.length, 1);
    assert.equal(harness.frameQueue.frames.size, 1);
});

test("reveals loader then world then title then action then ready", async () => {
    const harness = createRuntimeHarness();
    harness.sceneDeferred.resolve(createScene());
    await flushPromises();

    assert.equal(harness.root.dataset.introPhase, "loader");
    harness.frameQueue.runNext(650);
    assert.equal(harness.root.dataset.introPhase, "world");
    assert.ok(Number(harness.experience.style.getPropertyValue(
        "--intro-world-progress"
    )) > 0);
    assert.equal(harness.experience.style.getPropertyValue(
        "--intro-title-progress"
    ), "0");

    harness.frameQueue.runNext(1200);
    assert.equal(harness.root.dataset.introPhase, "title");
    assert.ok(Number(harness.experience.style.getPropertyValue(
        "--intro-title-progress"
    )) > 0);
    assert.equal(harness.experience.style.getPropertyValue(
        "--intro-action-progress"
    ), "0");

    harness.frameQueue.runNext(1700);
    assert.equal(harness.root.dataset.introPhase, "action");
    assert.ok(Number(harness.experience.style.getPropertyValue(
        "--intro-action-progress"
    )) > 0);

    harness.frameQueue.runNext(2200);
    assertCompleteFallback(harness);
});

test("forces the final composition at an independent 3999ms deadline", () => {
    const timers = createTimerHarness();
    const harness = createRuntimeHarness({ timers });

    timers.advance(3998);
    assert.notEqual(harness.root.dataset.introPhase, "ready");
    assert.equal(harness.frameQueue.frames.size, 1);

    timers.advance(1);
    assertCompleteFallback(harness);
    assert.equal(timers.pendingCount(), 0);
    assert.equal(harness.frameQueue.frames.size, 1);
});

test("forwards pointer and drag state through the Task 3 pose unchanged", async () => {
    const harness = createRuntimeHarness();
    const scene = createScene();
    harness.sceneDeferred.resolve(scene);
    await flushPromises();
    harness.frameQueue.runNext(0);

    harness.input.emit({
        pointer: { x: .65, y: -.4 },
        dragAngle: .72,
        dragging: true
    });
    harness.frameQueue.runNext(100);

    const forwarded = scene.poses.at(-1);
    assert.deepEqual(forwarded.pointer, { x: .65, y: -.4 });
    assert.equal(forwarded.dragAngle, .72);
    assert.ok(Math.abs(
        forwarded.worldAngle
        - Math.PI * 2 * 100 / REFERENCE_HERO.worldRevolutionMs
    ) < 1e-9);
    assert.equal(forwarded.elapsedMs, 100);
    assert.equal(forwarded.reduceMotion, false);
});

test("never mutates reference title state during input or frames", async () => {
    const harness = createRuntimeHarness();
    const scene = createScene();
    const before = {
        dataset: { ...harness.title.dataset },
        style: harness.title.style.entries()
    };
    harness.sceneDeferred.resolve(scene);
    await flushPromises();
    harness.input.emit({
        pointer: { x: 1, y: 1 },
        dragAngle: -1.2,
        settling: true
    });
    harness.frameQueue.runNext(400);

    assert.deepEqual(harness.title.dataset, before.dataset);
    assert.deepEqual(harness.title.style.entries(), before.style);
    assert.equal("selectedProduct" in harness.experience.dataset, false);
    assert.deepEqual(
        harness.experience.style.entries().map(([name]) => name).sort(),
        [
            "--intro-action-progress",
            "--intro-title-progress",
            "--intro-world-progress"
        ]
    );
    assert.deepEqual(harness.root.style.entries(), []);
});

test("pauses and resumes BFCache state without replaying the intro", async () => {
    const harness = createRuntimeHarness();
    const scene = createScene();
    harness.sceneDeferred.resolve(scene);
    await flushPromises();
    harness.frameQueue.runNext(2200);
    assertCompleteFallback(harness);
    const worldAngle = scene.poses.at(-1).worldAngle;

    harness.windowTarget.emit("pagehide", { persisted: true });
    assert.equal(harness.frameQueue.frames.size, 0);
    assert.equal(scene.visibleValues.at(-1), false);

    harness.setNow(12200);
    harness.windowTarget.emit("pageshow", { persisted: true });
    assert.equal(harness.frameQueue.frames.size, 1);
    assert.equal(scene.visibleValues.at(-1), true);
    harness.frameQueue.runNext(12200);

    assertCompleteFallback(harness);
    assert.equal(scene.poses.at(-1).worldAngle, worldAngle);
    assert.equal(harness.frameQueue.frames.size, 1);
});

test("maps missing Motion, reduced motion, and static query to static mode", () => {
    const fixtures = [
        {
            motionAvailable: false,
            detectedReducedMotion: false,
            forceStatic: false
        },
        {
            motionAvailable: true,
            detectedReducedMotion: true,
            forceStatic: false
        },
        {
            motionAvailable: true,
            detectedReducedMotion: false,
            forceStatic: true
        }
    ];

    fixtures.forEach((fixture) => {
        const capabilities = resolveQuantPlanetCapabilities({
            ...fixture,
            detectedWebgl: true,
            cores: 16,
            dpr: 3
        });
        assert.equal(capabilities.profile.name, "static");
        assert.equal(capabilities.motionDisabled, true);

        const harness = createRuntimeHarness({
            profile: capabilities.profile,
            motionDisabled: capabilities.motionDisabled
        });
        assertCompleteFallback(harness);
        assert.equal(
            harness.root.classList.contains("planet-static-mode"),
            true
        );
        assert.equal(
            harness.root.classList.contains("planet-reduced-motion"),
            true
        );
        assert.equal(harness.sceneConstructionCalls(), 0);
        assert.equal(harness.input.createCalls, 0);
        assert.equal(harness.frameQueue.frames.size, 0);
    });
});

test("caps capable high-quality profiles at DPR 1.5", () => {
    const capabilities = resolveQuantPlanetCapabilities({
        detectedWebgl: true,
        motionAvailable: true,
        cores: 16,
        dpr: 3
    });

    assert.equal(capabilities.profile.name, "high");
    assert.equal(capabilities.profile.pixelRatio, 1.5);
    assert.equal(capabilities.staticMode, false);
});

test("shows the complete fallback when WebGL construction fails", async () => {
    const harness = createRuntimeHarness();
    harness.sceneDeferred.reject(new Error("renderer unavailable"));
    await flushPromises();

    assertCompleteFallback(harness);
    assert.equal(harness.root.classList.contains("planet-webgl-failed"), true);
    assert.equal(harness.frameQueue.frames.size, 0);
    assert.equal(harness.input.destroyCalls, 1);
});

test("shows the complete fallback and retires WebGL on context loss", async () => {
    const harness = createRuntimeHarness();
    const scene = createScene();
    harness.sceneDeferred.resolve(scene);
    await flushPromises();
    let prevented = false;

    harness.canvas.emit("webglcontextlost", {
        preventDefault() {
            prevented = true;
        }
    });

    assert.equal(prevented, true);
    assertCompleteFallback(harness);
    assert.equal(harness.root.classList.contains("planet-webgl-failed"), true);
    assert.equal(scene.disposeCalls, 1);
    assert.equal(harness.input.destroyCalls, 1);
    assert.equal(harness.frameQueue.frames.size, 0);
});

test("publishes bounded loopback diagnostics without wheel or scroll listeners", async () => {
    const harness = createRuntimeHarness();
    const scene = createScene();
    harness.sceneDeferred.resolve(scene);
    await flushPromises();

    harness.frameQueue.runNext(16);

    assert.equal(harness.canvas.dataset.renderMs, "0.00");
    assert.equal(harness.canvas.dataset.drawCalls, "43");
    assert.equal(harness.canvas.dataset.triangles, "7155");
    for (const target of [
        harness.canvas,
        harness.documentTarget,
        harness.windowTarget
    ]) {
        assert.equal(target.listenerCount("wheel"), 0);
        assert.equal(target.listenerCount("scroll"), 0);
    }
});

test("destroy cancels timer, Motion, input, scene, and RAF exactly once", async () => {
    const timers = createTimerHarness();
    const motion = createMotionHarness();
    const harness = createRuntimeHarness({ timers, motion });
    const scene = createScene();
    harness.sceneDeferred.resolve(scene);
    await flushPromises();

    assert.equal(timers.pendingCount(), 1);
    assert.equal(motion.animations.length, 1);
    assert.equal(harness.frameQueue.frames.size, 1);
    assert.equal(harness.input.surface, harness.canvas);

    harness.runtime.destroy();
    harness.runtime.destroy();

    assert.equal(timers.pendingCount(), 0);
    assert.equal(timers.clearedIds.length, 1);
    assert.equal(motion.animations[0].animation.cancelCalls, 1);
    assert.equal(harness.input.destroyCalls, 1);
    assert.equal(scene.disposeCalls, 1);
    assert.equal(harness.frameQueue.frames.size, 0);
    assert.equal(harness.frameQueue.cancelledIds.length, 1);
    assert.equal(harness.documentTarget.listenerCount("visibilitychange"), 0);
    assert.equal(harness.windowTarget.listenerCount("resize"), 0);
    assert.equal(harness.windowTarget.listenerCount("pagehide"), 0);
    assert.equal(harness.windowTarget.listenerCount("pageshow"), 0);
    assert.equal(harness.canvas.listenerCount("webglcontextlost"), 0);
});

test("disposes a scene that resolves after runtime destruction exactly once", async () => {
    const harness = createRuntimeHarness();
    const lateScene = createScene();
    harness.runtime.destroy();
    harness.sceneDeferred.resolve(lateScene);
    await flushPromises();

    assert.equal(lateScene.disposeCalls, 1);
    assert.equal(harness.frameQueue.frames.size, 0);
});

test("bootstrap fallback writes progress at the experience cascade boundary", async () => {
    const root = createEventTarget();
    const experience = createEventTarget();
    const elements = new Map([
        ["[data-home-experience]", experience],
        ["[data-intro-loader]", createEventTarget()],
        ["[data-home-scene]", createEventTarget()],
        ["[data-home-canvas]", createEventTarget()],
        ["[data-planet-fallback]", createEventTarget()],
        ["[data-reference-title]", createEventTarget()],
        ["[data-enter-quant]", null]
    ]);
    const documentTarget = {
        documentElement: root,
        querySelector(selector) {
            return elements.get(selector);
        },
        createElement() {
            return {
                getContext() {
                    return null;
                }
            };
        }
    };
    const windowTarget = {
        location: { hostname: "example.com", search: "" },
        matchMedia() {
            return { matches: false };
        },
        devicePixelRatio: 1
    };
    const descriptors = new Map(
        ["document", "window", "navigator"].map((name) => [
            name,
            Object.getOwnPropertyDescriptor(globalThis, name)
        ])
    );

    Object.defineProperty(globalThis, "document", {
        configurable: true,
        value: documentTarget
    });
    Object.defineProperty(globalThis, "window", {
        configurable: true,
        value: windowTarget
    });
    Object.defineProperty(globalThis, "navigator", {
        configurable: true,
        value: { hardwareConcurrency: 8 }
    });

    try {
        await import(
            "../../main/resources/static/js/home-quant-planet.js"
            + `?cascade=${Date.now()}`
        );
    } finally {
        descriptors.forEach((descriptor, name) => {
            if (descriptor) {
                Object.defineProperty(globalThis, name, descriptor);
            } else {
                Reflect.deleteProperty(globalThis, name);
            }
        });
    }

    assert.deepEqual(experience.style.entries().sort(), [
        ["--intro-action-progress", "1"],
        ["--intro-title-progress", "1"],
        ["--intro-world-progress", "1"]
    ]);
    assert.deepEqual(root.style.entries(), []);
    assert.equal(root.classList.contains("planet-intro-ready"), true);
    assert.equal(root.classList.contains("planet-webgl-failed"), true);
});
