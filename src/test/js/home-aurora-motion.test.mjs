import test from "node:test";
import assert from "node:assert/strict";
import {
    createSpring,
    normalizedScrollProgress,
    smoothstep,
    stepSpring
} from "../../main/resources/static/js/home-aurora-motion.js";

test("normalized scroll progress is clamped and reversible", () => {
    assert.equal(normalizedScrollProgress(0, 2800, 1000), 0);
    assert.equal(normalizedScrollProgress(-900, 2800, 1000), 0.5);
    assert.equal(normalizedScrollProgress(-1800, 2800, 1000), 1);
    assert.equal(normalizedScrollProgress(-2400, 2800, 1000), 1);
    assert.equal(normalizedScrollProgress(300, 2800, 1000), 0);
});

test("spring converges to its target without snapping", () => {
    const spring = createSpring(0);
    spring.target = 1;
    stepSpring(spring, 1 / 60);
    assert.ok(spring.value > 0);
    assert.ok(spring.value < 1);
    for (let index = 0; index < 240; index += 1) {
        stepSpring(spring, 1 / 60);
    }
    assert.ok(Math.abs(spring.value - 1) < 0.001);
    assert.ok(Math.abs(spring.velocity) < 0.001);
});

test("smoothstep remains bounded", () => {
    assert.equal(smoothstep(0.2, 0.8, 0), 0);
    assert.equal(smoothstep(0.2, 0.8, 1), 1);
    assert.ok(smoothstep(0.2, 0.8, 0.5) > 0);
});
