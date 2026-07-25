import assert from "node:assert/strict";
import test from "node:test";
import {
    advanceRingAngle,
    clampUnit,
    createCourierLoopPose,
    createDomMotion,
    createIntroState,
    createNavigationRingState,
    createOrbitPose,
    createPageLifecycle,
    normalizePointer,
    resolveLocalTestOverrides,
    selectPlanetQualityProfile,
    shouldRenderFrame,
    wrapIndex
} from "../../main/resources/static/js/home-quant-planet-motion.js";
import { createScrollController } from "../../main/resources/static/js/home-quant-planet-scroll.js";

test("selects bounded planet quality profiles", () => {
    assert.equal(selectPlanetQualityProfile({
        reduceMotion: true, webgl: true, mobile: false, cores: 16, dpr: 3
    }).name, "static");
    assert.deepEqual(selectPlanetQualityProfile({
        reduceMotion: false, webgl: true, mobile: true, cores: 8, dpr: 3
    }), {
        name: "balanced", webgl: true, pixelRatio: 1.25,
        pointerEnabled: false, shadows: false, outlineLevel: 1,
        foliageCount: 18, cloudCount: 5, planetScale: 0.82
    });
    assert.equal(selectPlanetQualityProfile({
        reduceMotion: false, webgl: true, mobile: false, cores: 16, dpr: 3
    }).pixelRatio, 1.5);
});

test("progresses the intro only after readiness and exits on timeout", () => {
    assert.deepEqual(
        createIntroState({
            elapsed: 300, ready: false, readyElapsed: 0,
            timedOut: false, reduceMotion: false
        }),
        {
            phase: "mark", loaderOpacity: 1, markProgress: 0.08,
            splitProgress: 0, revealProgress: 0, worldScale: 0.88,
            interactionReady: false
        }
    );
    assert.equal(createIntroState({
        elapsed: 1700, ready: true, readyElapsed: 850,
        timedOut: false, reduceMotion: false
    }).phase, "ready");
    assert.equal(createIntroState({
        elapsed: 4000, ready: false, readyElapsed: 0,
        timedOut: true, reduceMotion: false
    }).interactionReady, true);
});

test("reduced motion resolves directly to a stable ready composition", () => {
    assert.deepEqual(createIntroState({
        elapsed: 20, ready: true, readyElapsed: 0,
        timedOut: false, reduceMotion: true
    }), {
        phase: "ready", loaderOpacity: 0, markProgress: 1,
        splitProgress: 0, revealProgress: 1, worldScale: 1,
        interactionReady: true
    });
});

test("keeps fullscreen orbit bounded under extreme input", () => {
    const pose = createOrbitPose({
        time: 28000, dragAngle: 100,
        pointer: { x: 8, y: -8 }, reduceMotion: false
    });
    assert.ok(pose.cameraDistance >= 8.2 && pose.cameraDistance <= 9.6);
    assert.ok(Math.abs(pose.cameraYaw) <= Math.PI * 2);
    assert.ok(Math.abs(pose.cameraPitch) <= .34);
    assert.ok(Math.abs(pose.planetPitch) <= .18);
    assert.ok(Number.isFinite(pose.planetYaw));
});

test("wraps six product links and brings the selected item forward", () => {
    assert.equal(wrapIndex(-1, 6), 5);
    assert.equal(wrapIndex(6, 6), 0);
    const state = createNavigationRingState({
        rotationAngle: 0, dragAngle: 0, selectedIndex: 2,
        snapToSelected: true, itemCount: 6
    });
    assert.equal(state.items.length, 6);
    assert.equal(state.frontIndex, 2);
    assert.ok(state.items[2].depth > state.items[1].depth);
    assert.ok(state.items.every((item) =>
        Number.isFinite(item.angle)
        && item.opacity >= .35 && item.opacity <= 1
        && item.scale >= .72 && item.scale <= 1.12
    ));
});

test("advances one 28-second revolution only while unpaused", () => {
    assert.equal(advanceRingAngle(1, 1000, true, false), 1);
    assert.equal(advanceRingAngle(1, 1000, false, true), 1);
    assert.ok(Math.abs(
        advanceRingAngle(0, 100, false, false) - Math.PI * 2 / 280
    ) < 1e-9);
});

test("loops the courier through all three landmarks with authored holds", () => {
    const observe = createCourierLoopPose(23000, false);
    const resolve = createCourierLoopPose(7000, false);
    const act = createCourierLoopPose(15000, false);
    assert.ok(observe.landmarkActivity.observe > .9);
    assert.ok(resolve.landmarkActivity.resolve > .9);
    assert.ok(act.landmarkActivity.act > .9);
    assert.ok(act.idleBlend > .8);
    assert.ok([observe, resolve, act].every((pose) =>
        pose.routeProgress >= 0 && pose.routeProgress <= 1
        && pose.walkPhase >= 0 && pose.walkPhase <= Math.PI * 2
    ));
});

test("normalizes pointer into a finite unit range", () => {
    assert.deepEqual(
        normalizePointer(50, 25, { left: 0, top: 0, width: 100, height: 100 }),
        { x: 0, y: -0.5 }
    );
    assert.equal(clampUnit(Number.NaN), 0);
});

test("accepts deterministic fallback overrides only on loopback", () => {
    assert.deepEqual(resolveLocalTestOverrides({
        hostname: "127.0.0.1", search: "?render=static&motion=reduce"
    }), { forceStatic: true, forceReduced: true });
    assert.deepEqual(resolveLocalTestOverrides({
        hostname: "[::1]", search: "?render=static&motion=reduce"
    }), { forceStatic: true, forceReduced: true });
    assert.deepEqual(resolveLocalTestOverrides({
        hostname: "kingyurina.example", search: "?render=static&motion=reduce"
    }), { forceStatic: false, forceReduced: false });
});

test("renders only when the planet can contribute a visible frame", () => {
    assert.equal(shouldRenderFrame({
        visible: true, hidden: false, webgl: true, reducedMotion: false, dirty: true
    }), true);
    assert.equal(shouldRenderFrame({
        visible: false, hidden: false, webgl: true, reducedMotion: false, dirty: true
    }), false);
    assert.equal(shouldRenderFrame({
        visible: true, hidden: false, webgl: true, reducedMotion: true, dirty: false
    }), false);
    assert.equal(shouldRenderFrame({
        visible: true, hidden: false, webgl: true, dirty: false, ambient: true
    }), true);
});

test("does not construct Lenis when reduced motion is active", () => {
    let constructed = 0;
    class FakeLenis {
        constructor() { constructed += 1; }
    }
    const controller = createScrollController({
        LenisClass: FakeLenis,
        reduceMotion: true,
        onScroll() {}
    });
    assert.equal(controller.isEnabled, false);
    assert.equal(constructed, 0);
});

test("forwards Lenis scroll events and owns the Lenis lifecycle", () => {
    let options;
    let listener;
    const calls = [];
    class FakeLenis {
        constructor(value) { options = value; }
        on(name, callback) { assert.equal(name, "scroll"); listener = callback; }
        raf(time) { calls.push(["raf", time]); }
        stop() { calls.push(["stop"]); }
        start() { calls.push(["start"]); }
        destroy() { calls.push(["destroy"]); }
    }
    let received;
    const controller = createScrollController({
        LenisClass: FakeLenis,
        reduceMotion: false,
        onScroll(value) { received = value; }
    });
    assert.equal(controller.isEnabled, true);
    assert.deepEqual(options, {
        autoRaf: false, anchors: true, lerp: 0.085, smoothWheel: true,
        syncTouch: false, wheelMultiplier: 0.9
    });
    listener({ progress: 0.4 });
    assert.deepEqual(received, { progress: 0.4 });
    controller.raf(42);
    controller.stop();
    controller.start();
    controller.destroy();
    assert.deepEqual(calls, [["raf", 42], ["stop"], ["start"], ["destroy"]]);
});

test("keeps content visible without Motion and avoids animations in reduced motion", () => {
    const element = { style: {} };
    const root = {
        querySelectorAll() { return [element]; },
        setAttribute() {}
    };
    const missing = createDomMotion({ MotionAPI: null, root, reduceMotion: false });
    missing.reveal(element);
    assert.equal(element.style.opacity, "1");
    assert.equal(element.style.transform, "none");

    let animations = 0;
    const reduced = createDomMotion({
        MotionAPI: { animate() { animations += 1; } }, root, reduceMotion: true
    });
    reduced.reveal(element);
    reduced.setChapter("act");
    assert.equal(animations, 0);
});

test("reveals elements using the homepage marker and changes the planet chapter", () => {
    const element = { style: { opacity: "0", transform: "translateY(16px)" } };
    let selector = "";
    let attribute;
    const root = {
        querySelectorAll(value) {
            selector = value;
            return value === "[data-home-reveal]" ? [element] : [];
        },
        setAttribute(name, value) { attribute = [name, value]; }
    };

    const motion = createDomMotion({ MotionAPI: null, root, reduceMotion: false });
    motion.setChapter("resolve");

    assert.equal(selector, "[data-home-reveal]");
    assert.equal(element.style.opacity, "1");
    assert.equal(element.style.transform, "none");
    assert.deepEqual(attribute, ["data-planet-chapter", "resolve"]);
});

test("pauses and resumes one animation loop across the back-forward cache", () => {
    let nextFrameId = 0;
    const queuedFrames = new Map();
    const calls = { frame: 0, pause: 0, resume: 0, destroy: 0 };
    const lifecycle = createPageLifecycle({
        requestFrame(callback) {
            const id = ++nextFrameId;
            queuedFrames.set(id, callback);
            return id;
        },
        cancelFrame(id) {
            queuedFrames.delete(id);
        },
        onFrame() {
            calls.frame += 1;
        },
        onPause() {
            calls.pause += 1;
        },
        onResume() {
            calls.resume += 1;
        },
        onDestroy() {
            calls.destroy += 1;
        }
    });

    lifecycle.start();
    assert.equal(queuedFrames.size, 1);

    lifecycle.handlePageHide({ persisted: true });
    lifecycle.handlePageHide({ persisted: true });
    assert.deepEqual(calls, { frame: 0, pause: 1, resume: 0, destroy: 0 });
    assert.equal(queuedFrames.size, 0);

    lifecycle.handlePageShow({ persisted: true });
    lifecycle.handlePageShow({ persisted: true });
    assert.deepEqual(calls, { frame: 0, pause: 1, resume: 1, destroy: 0 });
    assert.equal(queuedFrames.size, 1);

    const [frameId, frameCallback] = queuedFrames.entries().next().value;
    queuedFrames.delete(frameId);
    frameCallback(16);
    assert.equal(calls.frame, 1);
    assert.equal(queuedFrames.size, 1);

    lifecycle.handlePageHide({ persisted: false });
    lifecycle.destroy();
    lifecycle.handlePageShow({ persisted: true });
    assert.deepEqual(calls, { frame: 1, pause: 1, resume: 1, destroy: 1 });
    assert.equal(queuedFrames.size, 0);
});

test("does not schedule an animation frame when a static lifecycle resumes from BFCache", () => {
    const queuedFrames = new Map();
    let nextFrameId = 0;
    const calls = { pause: 0, resume: 0, destroy: 0 };
    const lifecycle = createPageLifecycle({
        requestFrame(callback) {
            const id = ++nextFrameId;
            queuedFrames.set(id, callback);
            return id;
        },
        cancelFrame(id) {
            queuedFrames.delete(id);
        },
        onPause() { calls.pause += 1; },
        onResume() { calls.resume += 1; },
        onDestroy() { calls.destroy += 1; }
    });

    lifecycle.handlePageHide({ persisted: true });
    lifecycle.handlePageShow({ persisted: true });

    assert.deepEqual(calls, { pause: 1, resume: 1, destroy: 0 });
    assert.equal(queuedFrames.size, 0);

    lifecycle.handlePageHide({ persisted: false });
    assert.equal(calls.destroy, 1);
});
