import assert from "node:assert/strict";
import test from "node:test";
import {
    createQuantPlanetRuntime,
    resolveQuantPlanetCapabilities
} from "../../main/resources/static/js/home-quant-planet-runtime.js";

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
    dragAngle: 0,
    selectedIndex: 0,
    dragging: false,
    focused: false,
    hovered: false,
    pressed: false,
    pointer: { x: 0, y: 0 },
    paused: false,
    snapToSelected: false
};

function createStyle() {
    const properties = new Map();
    return {
        opacity: "",
        setProperty(name, value) {
            properties.set(name, String(value));
        },
        getPropertyValue(name) {
            return properties.get(name) || "";
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

function createFrameQueue() {
    const frames = new Map();
    let nextId = 0;
    return {
        frames,
        requestFrame(callback) {
            const id = ++nextId;
            frames.set(id, callback);
            return id;
        },
        cancelFrame(id) {
            frames.delete(id);
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
        dragAngles: [],
        pointerValues: [],
        renderCalls: [],
        resizeCalls: 0,
        visibleValues: [],
        dispose() {
            this.disposeCalls += 1;
        },
        getDiagnostics() {
            return { drawCalls: 12, triangles: 3456 };
        },
        render(time) {
            this.renderCalls.push(time);
            return true;
        },
        resize() {
            this.resizeCalls += 1;
        },
        setDragAngle(value) {
            this.dragAngles.push(value);
        },
        setPointer(value) {
            this.pointerValues.push(value);
        },
        setVisible(value) {
            this.visibleValues.push(value);
        }
    };
}

function createInputHarness() {
    let onChange = null;
    return {
        connectCalls: 0,
        destroyCalls: 0,
        createInput({ onChange: callback }) {
            onChange = callback;
            return {
                connect: () => {
                    this.connectCalls += 1;
                    onChange({ ...DEFAULT_INPUT_STATE });
                },
                destroy: () => {
                    this.destroyCalls += 1;
                }
            };
        },
        emit(state) {
            onChange({
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

function createRuntimeHarness({
    profile = HIGH_PROFILE,
    motionDisabled = profile.name === "static",
    useRealInput = false
} = {}) {
    const root = {
        classList: createClassList(),
        dataset: {},
        style: createStyle()
    };
    const loader = { style: createStyle() };
    const introMark = { style: createStyle() };
    const productLinks = Array.from({ length: 6 }, (_, index) =>
        createEventTarget({
            href: `/route-${index}`,
            style: createStyle(),
            focus() {}
        }));
    const experience = createEventTarget({
        getBoundingClientRect() {
            return { left: 0, top: 0, width: 400, height: 300 };
        },
        releasePointerCapture() {},
        setPointerCapture() {}
    });
    const canvas = createEventTarget({ dataset: {} });
    const documentTarget = createEventTarget({ hidden: false });
    const windowTarget = createEventTarget({
        innerHeight: 900,
        innerWidth: 1440
    });
    const frameQueue = createFrameQueue();
    const input = createInputHarness();
    const sceneDeferred = createDeferred();
    let now = 0;
    let sceneConstructionCalls = 0;
    const runtimeOptions = {
        root,
        experience,
        canvas,
        loader,
        introMark,
        productLinks,
        profile,
        motionDisabled,
        isLoopback: true,
        createScene() {
            sceneConstructionCalls += 1;
            return sceneDeferred.promise;
        },
        requestFrame: frameQueue.requestFrame,
        cancelFrame: frameQueue.cancelFrame,
        now: () => now,
        documentTarget,
        windowTarget
    };
    if (!useRealInput) {
        runtimeOptions.createInput = input.createInput.bind(input);
    }
    const runtime = createQuantPlanetRuntime(runtimeOptions);
    return {
        canvas,
        documentTarget,
        experience,
        frameQueue,
        input,
        introMark,
        loader,
        productLinks,
        root,
        runtime,
        sceneConstructionCalls: () => sceneConstructionCalls,
        sceneDeferred,
        setNow(value) {
            now = value;
        },
        windowTarget
    };
}

const flushPromises = () => new Promise((resolve) => setImmediate(resolve));

test("dismisses the intro at 4000ms when the scene resolves just before it", async () => {
    const harness = createRuntimeHarness();
    const scene = createScene();
    harness.setNow(3999);
    harness.sceneDeferred.resolve(scene);
    await flushPromises();

    assert.equal(harness.root.classList.contains("planet-webgl-ready"), true);
    harness.frameQueue.runNext(4000);

    assert.equal(harness.root.dataset.introPhase, "ready");
    assert.equal(harness.loader.style.opacity, "0");
    assert.equal(harness.root.classList.contains("planet-intro-ready"), true);
});

test("dismisses the intro when a late scene wins the promise race before a frame", async () => {
    const harness = createRuntimeHarness();
    const scene = createScene();
    harness.setNow(4100);
    harness.sceneDeferred.resolve(scene);
    await flushPromises();
    harness.frameQueue.runNext(4100);

    assert.equal(harness.root.dataset.introPhase, "ready");
    assert.equal(harness.loader.style.opacity, "0");
});

test("retires WebGL on context loss without destroying native input", async () => {
    const harness = createRuntimeHarness();
    const scene = createScene();
    harness.setNow(100);
    harness.sceneDeferred.resolve(scene);
    await flushPromises();
    let prevented = false;

    harness.canvas.emit("webglcontextlost", {
        preventDefault() {
            prevented = true;
        }
    });

    assert.equal(prevented, true);
    assert.equal(scene.disposeCalls, 1);
    assert.equal(harness.frameQueue.frames.size, 0);
    assert.equal(harness.root.classList.contains("planet-webgl-failed"), true);
    assert.equal(harness.root.classList.contains("planet-intro-ready"), true);
    assert.equal(harness.loader.style.opacity, "0");
    assert.equal(harness.input.destroyCalls, 0);
    assert.equal(harness.windowTarget.listenerCount("pageshow"), 0);
    assert.equal(harness.windowTarget.listenerCount("pagehide"), 1);
});

test("falls back cleanly when scene construction rejects", async () => {
    const harness = createRuntimeHarness();
    harness.sceneDeferred.reject(new Error("renderer unavailable"));
    await flushPromises();

    assert.equal(harness.frameQueue.frames.size, 0);
    assert.equal(harness.root.classList.contains("planet-webgl-failed"), true);
    assert.equal(harness.root.classList.contains("planet-intro-ready"), true);
    assert.equal(harness.input.destroyCalls, 0);
    assert.equal(harness.windowTarget.listenerCount("pageshow"), 0);
});

test("pauses and resumes one loop from BFCache without replaying the intro", () => {
    const harness = createRuntimeHarness();
    assert.equal(harness.frameQueue.frames.size, 1);

    harness.windowTarget.emit("pagehide", { persisted: true });
    assert.equal(harness.frameQueue.frames.size, 0);
    harness.windowTarget.emit("pageshow", { persisted: true });
    assert.equal(harness.frameQueue.frames.size, 1);
    harness.frameQueue.runNext(4000);
    assert.equal(harness.root.dataset.introPhase, "ready");

    harness.windowTarget.emit("pagehide", { persisted: true });
    harness.windowTarget.emit("pageshow", { persisted: true });
    harness.frameQueue.runNext(5000);
    assert.equal(harness.root.dataset.introPhase, "ready");
    assert.equal(harness.frameQueue.frames.size, 1);
});

test("maps missing Motion to a connected six-route static fallback", () => {
    const capabilities = resolveQuantPlanetCapabilities({
        forceReduced: false,
        forceStatic: false,
        detectedReducedMotion: false,
        detectedWebgl: true,
        motionAvailable: false,
        mobile: false,
        cores: 16,
        dpr: 2
    });
    assert.equal(capabilities.profile.name, "static");
    assert.equal(capabilities.motionDisabled, true);

    const harness = createRuntimeHarness({
        profile: capabilities.profile,
        motionDisabled: capabilities.motionDisabled
    });
    assert.deepEqual(
        harness.productLinks.map((link) => link.href),
        [
            "/route-0",
            "/route-1",
            "/route-2",
            "/route-3",
            "/route-4",
            "/route-5"
        ]
    );
    assert.equal(harness.input.connectCalls, 1);
    assert.equal(harness.sceneConstructionCalls(), 0);
    assert.equal(harness.frameQueue.frames.size, 0);
    assert.equal(harness.root.classList.contains("planet-intro-ready"), true);
});

test("keeps reduced-motion orbit stable until explicit drag repositions it", () => {
    const harness = createRuntimeHarness({
        profile: STATIC_PROFILE,
        motionDisabled: true,
        useRealInput: true
    });
    const before = harness.productLinks[0].style
        .getPropertyValue("--orbit-x");

    harness.experience.emit("pointerdown", {
        pointerId: 21,
        isPrimary: true,
        clientX: 100,
        button: 0,
        target: harness.experience,
        preventDefault() {}
    });
    harness.experience.emit("pointermove", {
        pointerId: 21,
        isPrimary: true,
        clientX: 200,
        clientY: 150,
        preventDefault() {}
    });

    const after = harness.productLinks[0].style
        .getPropertyValue("--orbit-x");
    assert.notEqual(after, before);
    assert.equal(harness.frameQueue.frames.size, 0);
});
