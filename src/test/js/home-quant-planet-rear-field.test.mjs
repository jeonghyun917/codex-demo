import assert from "node:assert/strict";
import test from "node:test";
import * as THREE from "../../main/resources/static/js/vendor/three.module.js";
import {
    createQuantPlanetRearField
} from "../../main/resources/static/js/home-quant-planet-rear-field.js";
import {
    createRearFieldLayout
} from "../../main/resources/static/js/home-quant-planet-rear-field-layout.js";
import {
    createTrackingThree
} from "./support/three-test-double.mjs";

const REAR_FAMILY_LAYOUTS = Object.freeze({
    RearRockChips: ["shard"],
    RearArchitecturalScraps: ["satellite"],
    RearRingRemnants: ["beacon", "ring"],
    RearAtmosphericClouds: ["panel"],
    RearPaperFlecks: ["signal"]
});

test("builds varied instanced rear families behind the planet", () => {
    const field = createQuantPlanetRearField(THREE, {
        profile: { name: "high" }
    });
    const names = [
        "RearRockChips",
        "RearArchitecturalScraps",
        "RearRingRemnants",
        "RearAtmosphericClouds",
        "RearPaperFlecks"
    ];
    for (const name of names) {
        assert.ok(field.group.getObjectByName(name));
    }
    field.update({ worldAngle: Math.PI, elapsedMs: 16000 });
    assert.ok(field.getDebugState().every((item) => item.behindSafePlane));
    field.dispose();
});

test("renders authored placements as asymmetric wedges and merged cloud blobs",
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
    const rocks = field.group.getObjectByName("RearRockChips");
    rocks.geometry.computeBoundingBox();
    assert.notEqual(
        rocks.geometry.boundingBox.max.x,
        -rocks.geometry.boundingBox.min.x,
        "rock chips must be asymmetric wedges, not regular cones"
    );
    const clouds = field.group.getObjectByName("RearAtmosphericClouds");
    assert.ok(
        clouds.geometry.attributes.position.count >= 180,
        "atmospheric fields must merge several low-poly lobes"
    );
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

test("disposes every rear-field resource exactly once", () => {
    const field = createQuantPlanetRearField(THREE, {
        profile: { name: "balanced" }
    });
    const resources = new Set();
    field.group.traverse((object) => {
        if (!object.isInstancedMesh) {
            return;
        }
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

    field.dispose();
    field.dispose();

    assert.equal(resources.size, 10);
    assert.ok([...disposeCounts.values()].every((count) => count === 1));
});

test("rolls back every allocated resource after partial construction failure", () => {
    const fault = createTrackingThree(THREE, {
        failDodecahedronGeometry: true
    });

    assert.throws(
        () => createQuantPlanetRearField(fault.THREE, {
            profile: { name: "high" }
        }),
        /injected rear-field construction failure/
    );
    assert.ok(fault.allocations.length > 0);
    assert.ok(
        fault.allocations.every((resource) => resource.disposeCount === 1)
    );
});
