import assert from "node:assert/strict";
import test from "node:test";

import {
    clampUnit,
    createDomMotion,
    createScenePose,
    normalizePointer,
    normalizeScrollProgress,
    resolveLocalTestOverrides,
    selectHomeQualityProfile,
    shouldRenderFrame
} from "../../main/resources/static/js/home-observatory-motion.js";
import { createScrollController } from "../../main/resources/static/js/home-observatory-scroll.js";

test("normalizes pointer and story progress into finite unit ranges", () => {
    assert.deepEqual(
        normalizePointer(50, 25, { left: 0, top: 0, width: 100, height: 100 }),
        { x: 0, y: -0.5 }
    );
    assert.equal(normalizeScrollProgress(0, 3000, 1000), 0);
    assert.equal(normalizeScrollProgress(-1000, 3000, 1000), 0.5);
    assert.equal(normalizeScrollProgress(-2000, 3000, 1000), 1);
    assert.equal(clampUnit(Number.NaN), 0);
});

test("selects accessible bounded quality profiles", () => {
    assert.deepEqual(selectHomeQualityProfile({
        reduceMotion: true, mobile: false, cores: 16, dpr: 3, webgl: true
    }), {
        name: "static", pixelRatio: 1, pointerEnabled: false,
        webgl: false, shadows: false, segments: 0, coreScale: 1
    });
    const compactProfile = selectHomeQualityProfile({
        reduceMotion: false, mobile: true, cores: 8, dpr: 3, webgl: true
    });
    assert.equal(compactProfile.name, "low");
    assert.equal(compactProfile.coreScale, 2);
    const tabletProfile = selectHomeQualityProfile({
        reduceMotion: false, mobile: false, tablet: true, cores: 8, dpr: 2, webgl: true
    });
    assert.equal(tabletProfile.name, "high");
    assert.equal(tabletProfile.coreScale, 1.7);
    assert.deepEqual(selectHomeQualityProfile({
        reduceMotion: false, mobile: false, cores: 16, dpr: 3, webgl: true
    }), {
        name: "high", pixelRatio: 1.5, pointerEnabled: true,
        webgl: true, shadows: true, segments: 96, coreScale: 1
    });
});

test("accepts deterministic fallback overrides only on loopback", () => {
    assert.deepEqual(resolveLocalTestOverrides({
        hostname: "127.0.0.1", search: "?render=static&motion=reduce"
    }), { forceStatic: true, forceReduced: true });
    assert.deepEqual(resolveLocalTestOverrides({
        hostname: "kingyurina.example", search: "?render=static&motion=reduce"
    }), { forceStatic: false, forceReduced: false });
});

test("maps scroll into the three approved mechanical states", () => {
    assert.deepEqual(createScenePose(0, { x: 0, y: 0 }), {
        cameraZ: 8.8, cameraY: 0.3, housingOpen: 0,
        irisRotation: 0, metricOpacity: 0, productOpacity: 0
    });
    assert.equal(createScenePose(0.52, { x: 0, y: 0 }).housingOpen, 1);
    assert.equal(createScenePose(0.52, { x: 0, y: 0 }).metricOpacity, 1);
    assert.equal(createScenePose(1, { x: 0, y: 0 }).productOpacity, 1);
});

test("keeps intermediate scene poses within the physical instrument envelope", () => {
    const pose = createScenePose(0.35, { x: 1, y: -1 });
    assert.ok(pose.cameraZ >= 6.4 && pose.cameraZ <= 8.8);
    assert.ok(pose.housingOpen >= 0 && pose.housingOpen <= 1);
    assert.ok(pose.metricOpacity >= 0 && pose.metricOpacity <= 1);
});

test("slows camera travel through the hold phase", () => {
    const cameraRate = (start, end) => Math.abs(
        createScenePose(end).cameraZ - createScenePose(start).cameraZ
    ) / (end - start);
    const resolveRate = cameraRate(0.28, 0.42);
    const holdRate = cameraRate(0.56, 0.64);
    const actRate = cameraRate(0.76, 0.9);

    assert.ok(holdRate < resolveRate * 0.5);
    assert.ok(holdRate < actRate * 0.5);
    for (const boundary of [0.18, 0.52, 0.68]) {
        const atBoundary = createScenePose(boundary).cameraZ;
        assert.ok(Math.abs(createScenePose(boundary - 0.000001).cameraZ - atBoundary) < 0.0001);
        assert.ok(Math.abs(createScenePose(boundary + 0.000001).cameraZ - atBoundary) < 0.0001);
    }
});

test("renders only when the scene can contribute a visible frame", () => {
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
    reduced.setChapter("actuation");
    assert.equal(animations, 0);
});
