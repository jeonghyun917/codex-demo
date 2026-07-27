import test from "node:test";
import assert from "node:assert/strict";
import {
    REAR_FIELD_PERIODS_MS,
    createRearFieldLayout,
    sampleRearFieldTransform
} from "../../main/resources/static/js/home-quant-planet-rear-field-layout.js";

test("authors diverse rear-field profiles", () => {
    const high = createRearFieldLayout({ name: "high" });
    const balanced = createRearFieldLayout({ name: "balanced" });
    const low = createRearFieldLayout({ name: "low" });
    assert.ok(high.length >= 36 && high.length <= 48);
    assert.ok(balanced.length >= 24 && balanced.length <= 34);
    assert.ok(low.length >= 14 && low.length <= 22);
    assert.ok(new Set(high.map((item) => item.family)).size >= 4);
    assert.ok(new Set(high.map((item) => item.sizeTier)).size >= 5);
    const visibleAreas = high.map((item) => item.scale[0] * item.scale[1]);
    assert.ok(Math.max(...visibleAreas) / Math.min(...visibleAreas) >= 6);
});

test("keeps every sampled object behind its safe plane", () => {
    const placements = createRearFieldLayout({ name: "high" });
    for (let step = 0; step <= 128; step += 1) {
        const worldAngle = Math.PI * 2 * step / 128;
        for (const placement of placements) {
            const sample = sampleRearFieldTransform(placement, {
                worldAngle,
                elapsedMs: step * 500,
                reduceMotion: false
            });
            assert.ok(sample.position[2] <= placement.rearDepth);
            assert.ok(sample.opacity >= 0 && sample.opacity <= 1);
        }
    }
});

test("uses independent mid and far circulation periods", () => {
    assert.deepEqual(REAR_FIELD_PERIODS_MS, {
        mid: 48000,
        far: 64000
    });
});

test("reduced motion returns an invariant authored pose", () => {
    const placement = createRearFieldLayout({ name: "high" })[0];
    const first = sampleRearFieldTransform(placement, {
        worldAngle: 0,
        elapsedMs: 0,
        reduceMotion: true
    });
    const later = sampleRearFieldTransform(placement, {
        worldAngle: Math.PI,
        elapsedMs: 32000,
        reduceMotion: true
    });
    assert.deepEqual(later, first);
});
