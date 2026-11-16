import assert from "node:assert/strict";
import test from "node:test";

import * as THREE from "../../main/resources/static/js/vendor/three.module.js";

const proceduralModule = await import(
    "../../main/resources/static/js/contact-procedural-core.js"
).catch(() => null);

test("procedural core builds an asset-free shader world with orbit layers", () => {
    assert.ok(proceduralModule, "procedural core module must exist");

    const world = proceduralModule.createProceduralCoreWorld(THREE, {
        particleCount: 180,
        seed: 917
    });

    assert.equal(world.root.userData.kind, "procedural-core-world");
    assert.equal(world.core.material.type, "ShaderMaterial");
    assert.equal(world.particles.geometry.getAttribute("position").count, 180);
    assert.ok(world.rings.children.length >= 4);
    assert.equal(world.textures.length, 0);

    world.update({ elapsed: 1.25, pointerX: 0.4, pointerY: -0.2 });
    assert.equal(world.core.material.uniforms.uTime.value, 1.25);
    assert.equal(world.core.material.uniforms.uPointer.value.x, 0.4);

    world.dispose();
    assert.equal(world.root.children.length, 0);
});

test("orbit rotation is deterministic for the same animation time", () => {
    assert.ok(proceduralModule, "procedural core module must exist");
    const world = proceduralModule.createProceduralCoreWorld(THREE, {
        particleCount: 12,
        seed: 917
    });

    world.update({ elapsed: 2.5 });
    const firstRotation = world.rings.children[0].rotation.z;
    world.update({ elapsed: 2.5 });

    assert.equal(world.rings.children[0].rotation.z, firstRotation);
    world.dispose();
});

test("procedural core exposes a layered signal anatomy instead of a single glass sphere", () => {
    assert.ok(proceduralModule, "procedural core module must exist");
    const world = proceduralModule.createProceduralCoreWorld(THREE, {
        particleCount: 48,
        seed: 917
    });

    assert.equal(world.backdrop.material.type, "ShaderMaterial");
    assert.ok(world.filaments.children.length >= 12);
    assert.ok(world.signalArcs.children.length >= 8);
    assert.equal(world.surfaceNetwork.type, "LineSegments");
    assert.ok(world.microNodes.children.length >= 18);
    assert.equal(world.textures.length, 0, "the experience must remain asset-free");

    world.dispose();
});

test("deep-space and signal layers evolve deterministically with animation time", () => {
    assert.ok(proceduralModule, "procedural core module must exist");
    const world = proceduralModule.createProceduralCoreWorld(THREE, {
        particleCount: 24,
        seed: 917
    });

    world.update({ elapsed: 4.2, pointerX: -0.3, pointerY: 0.25 });
    const backdropTime = world.backdrop.material.uniforms.uTime.value;
    const filamentRotation = world.filaments.rotation.y;
    const signalRotation = world.signalArcs.rotation.z;

    world.update({ elapsed: 4.2, pointerX: -0.3, pointerY: 0.25 });
    assert.equal(world.backdrop.material.uniforms.uTime.value, backdropTime);
    assert.equal(world.filaments.rotation.y, filamentRotation);
    assert.equal(world.signalArcs.rotation.z, signalRotation);

    world.dispose();
});

test("precision pass adds internal chambers and instanced telemetry without external assets", () => {
    assert.ok(proceduralModule, "procedural core module must exist");
    const world = proceduralModule.createProceduralCoreWorld(THREE, {
        particleCount: 32,
        seed: 917
    });

    assert.ok(world.chambers.children.length >= 4);
    assert.equal(world.telemetryTicks.type, "Mesh");
    assert.equal(world.telemetryTicks.isInstancedMesh, true);
    assert.ok(world.telemetryTicks.count >= 96);
    assert.equal(world.scanVolume.material.type, "ShaderMaterial");
    assert.equal(world.constellationNetwork.type, "LineSegments");
    assert.equal(world.textures.length, 0);

    world.dispose();
});

test("precision layers update deterministically from elapsed time and pointer", () => {
    assert.ok(proceduralModule, "procedural core module must exist");
    const world = proceduralModule.createProceduralCoreWorld(THREE, {
        particleCount: 16,
        seed: 917
    });

    world.update({ elapsed: 7.25, pointerX: 0.2, pointerY: -0.4 });
    const chamberRotation = world.chambers.rotation.y;
    const telemetryRotation = world.telemetryTicks.rotation.z;
    const constellationRotation = world.constellationNetwork.rotation.y;
    const scanTime = world.scanVolume.material.uniforms.uTime.value;
    const scanPointerY = world.scanVolume.material.uniforms.uPointer.value.y;

    world.update({ elapsed: 7.25, pointerX: 0.2, pointerY: -0.4 });
    assert.equal(world.chambers.rotation.y, chamberRotation);
    assert.equal(world.telemetryTicks.rotation.z, telemetryRotation);
    assert.equal(world.constellationNetwork.rotation.y, constellationRotation);
    assert.equal(world.scanVolume.material.uniforms.uTime.value, scanTime);
    assert.equal(world.scanVolume.material.uniforms.uPointer.value.y, scanPointerY);

    world.dispose();
});
