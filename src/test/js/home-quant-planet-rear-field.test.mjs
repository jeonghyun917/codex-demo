import assert from "node:assert/strict";
import test from "node:test";
import * as THREE from "../../main/resources/static/js/vendor/three.module.js";
import {
    createQuantPlanetRearField
} from "../../main/resources/static/js/home-quant-planet-rear-field.js";
import {
    createRearFieldLayout,
    REAR_FIELD_PERIODS_MS
} from "../../main/resources/static/js/home-quant-planet-rear-field-layout.js";
import {
    createTrackingThree
} from "./support/three-test-double.mjs";

const REAR_FAMILY_LAYOUTS = Object.freeze({
    RearBroadHaze: ["haze"],
    RearAtmosphericClouds: ["cloud"],
    RearMistIslands: ["mist"]
});

test("builds only atmospheric rear families behind the planet", () => {
    const field = createQuantPlanetRearField(THREE, {
        profile: { name: "high" }
    });
    const names = [
        "RearBroadHaze",
        "RearAtmosphericClouds",
        "RearMistIslands"
    ];
    for (const name of names) {
        assert.ok(field.group.getObjectByName(name));
    }
    assert.equal(field.group.children.length, 3);
    assert.equal(field.group.getObjectByName("RearRockChips"), undefined);
    assert.equal(
        field.group.getObjectByName("RearArchitecturalScraps"),
        undefined
    );
    assert.equal(field.group.getObjectByName("RearRingRemnants"), undefined);
    assert.equal(field.group.getObjectByName("RearPaperFlecks"), undefined);
    field.update({ worldAngle: Math.PI, elapsedMs: 16000 });
    assert.ok(field.getDebugState().every((item) => item.behindSafePlane));
    field.dispose();
});

test("renders varied low-poly cloud silhouettes instead of debris",
        () => {
    const field = createQuantPlanetRearField(THREE, {
        profile: { name: "high" }
    });
    const debugState = field.getDebugState();
    const placements = createRearFieldLayout({ name: "high" });
    assert.equal(debugState.length, 42);
    for (const [name, layoutFamilies] of Object.entries(REAR_FAMILY_LAYOUTS)) {
        const mesh = field.group.getObjectByName(name);
        assert.equal(mesh.isInstancedMesh, true);
        assert.equal(
            mesh.count,
            placements.filter((item) => layoutFamilies.includes(item.family))
                .length,
            `${name} must retain one instance for every authored placement`
        );
    }
    for (const name of Object.keys(REAR_FAMILY_LAYOUTS)) {
        const clouds = field.group.getObjectByName(name);
        assert.ok(
            clouds.geometry.attributes.position.count >= 4,
            `${name} must render a complete atmospheric card`
        );
        assert.equal(clouds.material.map?.isDataTexture, true);
        assert.equal(clouds.material.map.image.width, 64);
        assert.equal(clouds.material.map.image.height, 64);
        assert.equal(clouds.material.transparent, true);
        assert.equal(clouds.material.depthWrite, false);
        clouds.geometry.computeBoundingBox();
        assert.ok(
            clouds.geometry.boundingBox.max.z
                - clouds.geometry.boundingBox.min.z <= .03,
            `${name} must read as a flat atmospheric silhouette`
        );
    }
    field.dispose();
});

test("reports bounded rear-field render work", () => {
    const field = createQuantPlanetRearField(THREE, {
        profile: { name: "high" }
    });
    const stats = field.getRenderStats();
    assert.ok(stats.drawCalls <= 5);
    assert.ok(stats.triangles <= 5000);
    field.dispose();
});

test("writes seam opacity into the instanced shader alpha path", () => {
    const field = createQuantPlanetRearField(THREE, {
        profile: { name: "high" }
    });
    const layout = createRearFieldLayout({ name: "high" });
    const clouds = field.group.getObjectByName("RearAtmosphericClouds");
    const cloudPlacement = layout.filter(
        (placement) => placement.family === "cloud"
    )[0];
    const opacityAttribute = clouds.geometry.getAttribute("instanceOpacity");
    assert.ok(opacityAttribute, "missing rendered per-instance opacity");
    assert.equal(clouds.material.transparent, true);

    const elapsedForPhase = (placement, targetPhase) => {
        const turn = Math.PI * 2;
        const phaseDelta = (
            (targetPhase - placement.phase) % turn + turn
        ) % turn;
        return phaseDelta / turn * REAR_FIELD_PERIODS_MS[placement.band];
    };
    const opacityAt = (targetPhase) => {
        field.update({
            worldAngle: 0,
            elapsedMs: elapsedForPhase(cloudPlacement, targetPhase),
            reduceMotion: false
        });
        return opacityAttribute.getX(0);
    };

    assert.ok(opacityAt(Math.PI / 2) <= 1e-6);
    const nearSeam = opacityAt(Math.PI / 2 - .08);
    assert.ok(nearSeam > 0 && nearSeam < .5);
    assert.ok(opacityAt(0) >= .999);
    assert.ok(opacityAt(Math.PI * 3 / 2) <= 1e-6);

    const createShader = () => ({
        vertexShader: [
            "#include <common>",
            "#include <begin_vertex>"
        ].join("\n"),
        fragmentShader: [
            "#include <common>",
            "#include <opaque_fragment>"
        ].join("\n")
    });
    const cloudShader = createShader();
    clouds.material.onBeforeCompile(cloudShader);
    assert.match(cloudShader.vertexShader, /attribute float instanceOpacity/);
    assert.match(
        cloudShader.fragmentShader,
        /diffuseColor\.a \*= vInstanceOpacity/
    );

    assert.ok(clouds.material.opacity >= .12);
    assert.ok(clouds.material.opacity <= .3);

    field.dispose();
});

test("writes zero rendered alpha for every current-pose ENTER-zone occupant",
        () => {
    let occupantSamples = 0;
    for (const profileName of ["high", "balanced", "low"]) {
        const field = createQuantPlanetRearField(THREE, {
            profile: { name: profileName }
        });
        const layout = createRearFieldLayout({ name: profileName });
        for (const elapsedMs of [0, 12000, 24000, 36000, 48000, 64000]) {
            field.update({ elapsedMs, reduceMotion: false });
            const stateById = new Map(field.getDebugState().map(
                (item) => [item.id, item]
            ));
            for (
                const [meshName, layoutFamilies]
                of Object.entries(REAR_FAMILY_LAYOUTS)
            ) {
                const mesh = field.group.getObjectByName(meshName);
                const opacity = mesh.geometry.getAttribute(
                    "instanceOpacity"
                );
                layout.filter((placement) =>
                    layoutFamilies.includes(placement.family)
                ).forEach((placement, index) => {
                    const state = stateById.get(placement.id);
                    if (
                        Math.abs(state.position[0]) < 1.6
                        && state.position[1] < -1.2
                    ) {
                        occupantSamples += 1;
                        assert.ok(
                            opacity.getX(index) <= 1e-6,
                            `${profileName}/${elapsedMs}/${placement.id}`
                        );
                    }
                });
            }
        }
        field.dispose();
    }
    assert.ok(
        occupantSamples > 0,
        "test phases must exercise geometric quiet-zone occupants"
    );
});

test("reprofiles one max-capacity field from low through high", () => {
    const field = createQuantPlanetRearField(THREE, {
        profile: { name: "low" }
    });
    const resources = new Set();
    field.group.traverse((object) => {
        if (object.isInstancedMesh) {
            resources.add(object.geometry);
            resources.add(object.material);
        }
    });
    const assertProfile = (name, expectedCount) => {
        field.setProfile({ name });
        field.update({ elapsedMs: 24000, reduceMotion: false });
        assert.equal(field.getDebugState().length, expectedCount);
        assert.equal(
            field.group.children.reduce(
                (sum, mesh) => sum + mesh.count,
                0
            ),
            expectedCount
        );
        const stats = field.getRenderStats();
        assert.ok(stats.drawCalls <= 5);
        assert.ok(stats.triangles <= 5000);
    };

    assertProfile("high", 42);
    assertProfile("balanced", 30);
    assertProfile("low", 18);
    assertProfile("high", 42);
    const resourcesAfter = new Set();
    field.group.traverse((object) => {
        if (object.isInstancedMesh) {
            resourcesAfter.add(object.geometry);
            resourcesAfter.add(object.material);
        }
    });
    assert.deepEqual(resourcesAfter, resources);
    field.dispose();
});

test("disposes every rear-field resource exactly once", () => {
    const field = createQuantPlanetRearField(THREE, {
        profile: { name: "balanced" }
    });
    const resources = new Set();
    const meshes = [];
    field.group.traverse((object) => {
        if (!object.isInstancedMesh) {
            return;
        }
        meshes.push(object);
        resources.add(object.geometry);
        resources.add(object.material);
    });
    const disposeCounts = new Map();
    resources.forEach((resource) => {
        const dispose = resource.dispose.bind(resource);
        disposeCounts.set(resource, 0);
        resource.dispose = () => {
            disposeCounts.set(resource, disposeCounts.get(resource) + 1);
            dispose();
        };
    });
    const meshDisposeCounts = new Map();
    meshes.forEach((mesh) => {
        const dispose = mesh.dispose.bind(mesh);
        meshDisposeCounts.set(mesh, 0);
        mesh.dispose = () => {
            meshDisposeCounts.set(
                mesh,
                meshDisposeCounts.get(mesh) + 1
            );
            dispose();
        };
    });

    field.dispose();
    field.dispose();

    assert.equal(meshes.length, 3);
    assert.ok(
        [...meshDisposeCounts.values()].every((count) => count === 1)
    );
    assert.equal(resources.size, 6);
    assert.ok([...disposeCounts.values()].every((count) => count === 1));
});

test("rolls back every allocated resource after partial construction failure", () => {
    const fault = createTrackingThree(THREE, {
        failBufferGeometry: true
    });

    assert.throws(
        () => createQuantPlanetRearField(fault.THREE, {
            profile: { name: "high" }
        }),
        /injected rear-field construction failure/
    );
    const allocatedMeshes = fault.allocations.filter(
        (resource) => resource.isInstancedMesh
    );
    assert.ok(
        allocatedMeshes.every((mesh) => mesh.disposeCount === 1)
    );
    assert.ok(
        fault.allocations.every((resource) => resource.disposeCount === 1)
    );
});
