import assert from "node:assert/strict";
import test from "node:test";

import * as THREE from "../../main/resources/static/js/vendor/three.module.js";

const natureModule = await import(
    "../../main/resources/static/js/contact-nature-core.js"
).catch(() => null);

test("wind field is deterministic, layered and spatially varied", () => {
    assert.ok(natureModule, "nature core module must exist");

    const first = natureModule.sampleWind(4.25, 1.2, -3.4, 917);
    const repeated = natureModule.sampleWind(4.25, 1.2, -3.4, 917);
    const neighboring = natureModule.sampleWind(4.25, 3.8, -1.1, 917);

    assert.deepEqual(first, repeated);
    assert.notDeepEqual(first, neighboring);
    assert.ok(first.strength >= 0 && first.strength <= 1);
    assert.ok(Number.isFinite(first.x));
    assert.ok(Number.isFinite(first.z));
    assert.ok(Number.isFinite(first.gust));
});

test("responsive profiles preserve composition while bounding render density", () => {
    assert.ok(natureModule, "nature core module must exist");

    const mobile = natureModule.selectNatureProfile(375, 812, 3);
    const desktop = natureModule.selectNatureProfile(1440, 900, 2);

    assert.ok(mobile.grassCount >= 1400);
    assert.ok(desktop.grassCount > mobile.grassCount);
    assert.ok(desktop.flowerCount > mobile.flowerCount);
    assert.ok(mobile.pixelRatio <= 1.25);
    assert.ok(desktop.pixelRatio <= 1.5);
});

test("living meadow exposes detailed procedural layers without textures", () => {
    assert.ok(natureModule, "nature core module must exist");
    const world = natureModule.createLivingMeadowWorld(THREE, {
        seed: 917,
        grassCount: 96,
        flowerCount: 18,
        pollenCount: 32,
        dewCount: 40
    });

    assert.equal(world.root.userData.kind, "living-meadow-world");
    assert.equal(world.sky.material.type, "ShaderMaterial");
    assert.equal(world.terrain.material.type, "ShaderMaterial");
    assert.equal(world.grass.material.type, "ShaderMaterial");
    assert.equal(world.grass.geometry.getAttribute("aOffset").count, 96);
    assert.ok(world.fineGrass.geometry.getAttribute("aOffset").count >= 24);
    assert.equal(world.seedHeads.isInstancedMesh, true);
    assert.equal(world.flowerCenters.isInstancedMesh, true);
    assert.ok(world.flowers.children.length >= 3);
    assert.equal(world.dew.type, "Points");
    assert.equal(world.pollen.type, "Points");
    assert.ok(world.mist.children.length >= 3);
    assert.equal(world.textures.length, 0);

    world.dispose();
    assert.equal(world.root.children.length, 0);
});

test("meadow animation updates every wind-sensitive layer deterministically", () => {
    assert.ok(natureModule, "nature core module must exist");
    const world = natureModule.createLivingMeadowWorld(THREE, {
        seed: 917,
        grassCount: 24,
        flowerCount: 8,
        pollenCount: 12,
        dewCount: 16
    });

    world.update({ elapsed: 7.5, pointerX: 0.3, pointerY: -0.2, reducedMotion: false });
    const first = {
        grass: world.grass.material.uniforms.uTime.value,
        flower: world.flowerMaterial.uniforms.uTime.value,
        pollenX: world.pollen.position.x,
        mistX: world.mist.children[0].position.x
    };
    world.update({ elapsed: 7.5, pointerX: 0.3, pointerY: -0.2, reducedMotion: false });

    assert.equal(world.grass.material.uniforms.uTime.value, first.grass);
    assert.equal(world.flowerMaterial.uniforms.uTime.value, first.flower);
    assert.equal(world.pollen.position.x, first.pollenX);
    assert.equal(world.mist.children[0].position.x, first.mistX);

    world.dispose();
});
