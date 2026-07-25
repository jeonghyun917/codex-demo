import assert from "node:assert/strict";
import test from "node:test";
import {
    clampUnit,
    createCourierPose,
    createDomMotion,
    createPageLifecycle,
    createPlanetPose,
    normalizePointer,
    normalizeScrollProgress,
    resolvePlanetChapter,
    resolveLocalTestOverrides,
    selectPlanetQualityProfile,
    shouldRenderFrame
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

test("uses overlapping authored chapters without gaps", () => {
    assert.equal(resolvePlanetChapter(0), "observe");
    assert.equal(resolvePlanetChapter(0.33), "resolve");
    assert.equal(resolvePlanetChapter(0.69), "act");
    assert.equal(resolvePlanetChapter(1), "act");
});

test("keeps camera and courier inside the authored world envelope", () => {
    for (const progress of [0, .15, .32, .5, .7, .86, 1]) {
        const pose = createPlanetPose(progress, { x: 1, y: -1 });
        assert.ok(pose.cameraDistance >= 7.1 && pose.cameraDistance <= 10.8);
        assert.ok(Math.abs(pose.cameraYaw) <= 1.45);
        assert.ok(Math.abs(pose.cameraPitch) <= .62);
        const courier = createCourierPose(progress, 1250);
        assert.ok(Number.isFinite(courier.routeAngle));
        assert.ok(courier.walkPhase >= 0 && courier.walkPhase <= Math.PI * 2);
    }
});

test("normalizes pointer and journey progress into finite unit ranges", () => {
    assert.deepEqual(
        normalizePointer(50, 25, { left: 0, top: 0, width: 100, height: 100 }),
        { x: 0, y: -0.5 }
    );
    assert.equal(normalizeScrollProgress(0, 3000, 1000), 0);
    assert.equal(normalizeScrollProgress(-1000, 3000, 1000), 0.5);
    assert.equal(normalizeScrollProgress(-2000, 3000, 1000), 1);
    assert.equal(clampUnit(Number.NaN), 0);
});

test("accepts deterministic fallback overrides only on loopback", () => {
    assert.deepEqual(resolveLocalTestOverrides({
        hostname: "127.0.0.1", search: "?render=static&motion=reduce"
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
