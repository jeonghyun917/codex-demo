import assert from "node:assert/strict";
import test from "node:test";
import * as THREE from "../../main/resources/static/js/vendor/three.module.js";
import {
    createQuantPlanetWorld
} from "../../main/resources/static/js/home-quant-planet-world.js";

const profile = {
    name: "high",
    outlineLevel: 2,
    foliageCount: 34,
    cloudCount: 8,
    shadows: true
};

function createWorld(t) {
    const world = createQuantPlanetWorld(THREE, { profile });
    t.after(() => world.dispose());
    return world;
}

function update(world, landmarkActivity, time = 1000) {
    world.update({
        planetPose: {
            planetPitch: 0,
            planetYaw: 0,
            planetRoll: 0,
            routeActivity: 1,
            landmarkActivity
        },
        courierPose: {
            routeAngle: 0,
            elevation: .18,
            heading: 0,
            walkPhase: 0,
            idleBlend: 0
        },
        time
    });
}

test("pauses Observe calibration outside the Observe chapter", (t) => {
    const world = createWorld(t);
    const dish = world.group.getObjectByName("ObserveDirectionalDish");

    update(world, { observe: 0, resolve: 1, act: 0 });
    assert.equal(dish.rotation.y, 0);

    update(world, { observe: 1, resolve: 0, act: 0 });
    assert.ok(dish.rotation.y > 0);
});

test("gives Decision Gate an Act-linked portal response", (t) => {
    const world = createWorld(t);
    const portal = world.group.getObjectByName("QuantProductPortal");

    update(world, { observe: 0, resolve: 1, act: 0 });
    const inactiveScale = portal.scale.x;
    update(world, { observe: 0, resolve: 0, act: 1 });

    assert.ok(portal.scale.x > inactiveScale);
});

test("illuminates only the route segment near the active chapter", (t) => {
    const world = createWorld(t);
    const routeLights = world.group.getObjectByName("InstancedRouteLamps");

    update(world, { observe: 1, resolve: 0, act: 0 });
    assert.ok(routeLights.instanceColor, "route lights require per-instance color");

    const observeColor = new THREE.Color();
    const resolveColor = new THREE.Color();
    routeLights.getColorAt(0, observeColor);
    routeLights.getColorAt(5, resolveColor);
    assert.ok(observeColor.r > resolveColor.r);

    update(world, { observe: 0, resolve: 0, act: 1 });
    const previousObserveRed = observeColor.r;
    const actColor = new THREE.Color();
    routeLights.getColorAt(0, observeColor);
    routeLights.getColorAt(9, actColor);
    assert.ok(observeColor.r < previousObserveRed);
    assert.ok(actColor.r > observeColor.r);
});

test("stays within the high-profile world render budget", (t) => {
    const world = createWorld(t);
    let drawCalls = 0;
    let shadowCalls = 0;
    let triangles = 0;

    world.group.traverse((object) => {
        if (!object.isMesh) {
            return;
        }
        drawCalls += 1;
        shadowCalls += object.castShadow ? 1 : 0;
        const instances = object.isInstancedMesh ? object.count : 1;
        const primitives = object.geometry.index
            ? object.geometry.index.count
            : object.geometry.attributes.position.count;
        triangles += primitives / 3 * instances;
    });

    assert.ok(drawCalls + shadowCalls <= 75);
    assert.ok(triangles <= 140000);
});

test("disposes every attached world resource exactly once", () => {
    const world = createQuantPlanetWorld(THREE, { profile });
    const resources = new Set();
    world.group.traverse((object) => {
        if (!object.isMesh) {
            return;
        }
        resources.add(object.geometry);
        const objectMaterials = Array.isArray(object.material)
            ? object.material
            : [object.material];
        objectMaterials.forEach((value) => {
            resources.add(value);
            if (value.gradientMap) {
                resources.add(value.gradientMap);
            }
        });
    });

    const disposeCounts = new Map();
    resources.forEach((resource) => {
        const original = resource.dispose.bind(resource);
        disposeCounts.set(resource, 0);
        resource.dispose = () => {
            disposeCounts.set(resource, disposeCounts.get(resource) + 1);
            original();
        };
    });

    world.dispose();
    world.dispose();

    assert.ok([...disposeCounts.values()].every((count) => count === 1));
});
