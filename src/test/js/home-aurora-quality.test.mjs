import test from "node:test";
import assert from "node:assert/strict";
import { selectAuroraQuality } from "../../main/resources/static/js/home-aurora-quality.js";

test("reduced motion always selects the static profile", () => {
    assert.deepEqual(selectAuroraQuality({
        reduceMotion: true, mobile: false, cores: 16, memory: 32, devicePixelRatio: 3
    }), {
        name: "reduced", pixelRatio: 1, shaderQuality: 0.35,
        pointerEnabled: false, ambientSpeed: 0
    });
});

test("mobile and constrained devices use the low profile", () => {
    const profile = selectAuroraQuality({
        reduceMotion: false, mobile: true, cores: 8, memory: 8, devicePixelRatio: 3
    });
    assert.equal(profile.name, "low");
    assert.equal(profile.pixelRatio, 1);
    assert.equal(profile.pointerEnabled, false);
});

test("medium and high profiles cap device pixel ratio", () => {
    const medium = selectAuroraQuality({
        reduceMotion: false, mobile: false, cores: 8, memory: 8, devicePixelRatio: 2
    });
    const high = selectAuroraQuality({
        reduceMotion: false, mobile: false, cores: 16, memory: 32, devicePixelRatio: 3
    });
    assert.equal(medium.pixelRatio, 1.4);
    assert.equal(high.pixelRatio, 1.75);
});
