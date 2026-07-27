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

test("authors deterministic nonrepeating families bands sizes and self-spins",
        () => {
    const first = createRearFieldLayout({ name: "high" });
    const second = createRearFieldLayout({ name: "high" });
    assert.deepEqual(second, first);
    assert.ok(first.every((placement) =>
        placement.spinPeriodMs >= 8000
        && placement.spinPeriodMs <= 18000));

    const hasShortPeriod = (values) => {
        for (let period = 1; period <= 8; period += 1) {
            if (values.every((value, index) =>
                index < period || value === values[index % period])) {
                return true;
            }
        }
        return false;
    };
    assert.equal(hasShortPeriod(first.map((item) => item.family)), false);
    assert.equal(hasShortPeriod(first.map((item) => item.band)), false);
    assert.equal(hasShortPeriod(first.map((item) => item.sizeTier)), false);

    const placement = first[0];
    const start = sampleRearFieldTransform(placement, {
        worldAngle: 0,
        elapsedMs: 0,
        reduceMotion: false
    });
    const oneSpin = sampleRearFieldTransform(placement, {
        worldAngle: 0,
        elapsedMs: placement.spinPeriodMs,
        reduceMotion: false
    });
    assert.ok(Math.abs(
        oneSpin.rotation[2] - start.rotation[2] - Math.PI * 2
    ) <= 1e-9);
});

test("keeps the reduced-motion authored field outside the ENTER quiet zone",
        () => {
    const placements = createRearFieldLayout({ name: "high" });
    const quietZoneOccupants = placements.filter((placement) => {
        const transform = sampleRearFieldTransform(placement, {
            worldAngle: Math.PI,
            elapsedMs: 32000,
            reduceMotion: true
        });
        return Math.abs(transform.position[0]) < 1.6
            && transform.position[1] < -1.2;
    });

    assert.deepEqual(
        quietZoneOccupants.map((placement) => placement.id),
        []
    );
});

test("fades smoothly around the current-pose ENTER quiet zone", () => {
    const placement = {
        band: "mid",
        phase: 0,
        radius: 0,
        verticalRatio: 0,
        rearDepth: -5,
        depthBreath: 0,
        rotation: [0, 0, 0],
        spinPeriodMs: 10000
    };
    const opacityAtY = (verticalOffset) => sampleRearFieldTransform({
        ...placement,
        verticalOffset
    }, {
        elapsedMs: 0,
        reduceMotion: false
    }).opacity;

    assert.equal(opacityAtY(-1.3), 0);
    assert.ok(opacityAtY(-1) > 0 && opacityAtY(-1) < 1);
    assert.equal(opacityAtY(-.6), 1);
});
