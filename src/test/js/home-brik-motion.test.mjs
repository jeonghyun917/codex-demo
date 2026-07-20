import assert from "node:assert/strict";
import test from "node:test";

import {
    clamp,
    normalizePointer,
    selectHomeMotionProfile,
    staggerDelay
} from "../../main/resources/static/js/home-brik-motion.js";

test("clamp returns finite values inside the requested range", () => {
    assert.equal(clamp(3, 0, 2), 2);
    assert.equal(clamp(Number.NaN, 0, 2), 0);
    assert.equal(clamp(Number.POSITIVE_INFINITY, 0, 2), 0);
});

test("normalizePointer maps pointer coordinates into a clamped unit range", () => {
    assert.deepEqual(normalizePointer(
        50, 25, { left: 0, top: 0, width: 100, height: 100 }
    ), { x: 0, y: -0.5 });
    assert.deepEqual(normalizePointer(
        -20, 150, { left: 0, top: 0, width: 100, height: 100 }
    ), { x: -1, y: 1 });
});

test("normalizePointer returns finite neutral values for invalid dimensions", () => {
    assert.deepEqual(normalizePointer(
        50, 25, { left: 0, top: 0, width: 0, height: Number.NaN }
    ), { x: 0, y: 0 });
});

test("selectHomeMotionProfile disables animation for reduced motion", () => {
    assert.deepEqual(selectHomeMotionProfile({
        reduceMotion: true, mobile: false, cores: 16, devicePixelRatio: 3
    }), {
        name: "reduced", pixelRatio: 1, pointerEnabled: false, animate: false
    });
});

test("selectHomeMotionProfile lowers detail on mobile and low-core devices", () => {
    assert.equal(selectHomeMotionProfile({
        reduceMotion: false, mobile: true, cores: 8, devicePixelRatio: 3
    }).name, "low");
    assert.equal(selectHomeMotionProfile({
        reduceMotion: false, mobile: false, cores: 4, devicePixelRatio: 3
    }).name, "low");
});

test("selectHomeMotionProfile caps high-detail pixel ratio", () => {
    assert.deepEqual(selectHomeMotionProfile({
        reduceMotion: false, mobile: false, cores: 16, devicePixelRatio: 3
    }), {
        name: "high", pixelRatio: 1.5, pointerEnabled: true, animate: true
    });
});

test("staggerDelay uses the default step and prevents negative delays", () => {
    assert.equal(staggerDelay(4), 192);
    assert.equal(staggerDelay(-2), 0);
});
