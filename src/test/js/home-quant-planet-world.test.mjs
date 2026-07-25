import assert from "node:assert/strict";
import test from "node:test";
import * as THREE from "../../main/resources/static/js/vendor/three.module.js";
import {
    createQuantPlanetWorld
} from "../../main/resources/static/js/home-quant-planet-world.js";
import {
    createCourierLoopPose
} from "../../main/resources/static/js/home-quant-planet-motion.js";

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

function update(world, courierPose, time = 1000) {
    world.update({
        orbitPose: {
            planetPitch: -.08,
            planetYaw: .42,
            planetRoll: .02
        },
        courierPose,
        time
    });
}

function withLandmarkActivity(landmarkActivity) {
    return {
        ...createCourierLoopPose(0, false),
        landmarkActivity
    };
}

test("pauses Observe calibration outside the Observe chapter", (t) => {
    const world = createWorld(t);
    const dish = world.group.getObjectByName("ObserveDirectionalDish");

    update(world, withLandmarkActivity({ observe: 0, resolve: 1, act: 0 }));
    assert.equal(dish.rotation.y, 0);

    update(world, withLandmarkActivity({ observe: 1, resolve: 0, act: 0 }));
    assert.ok(dish.rotation.y > 0);
});

test("gives Decision Gate an Act-linked portal response", (t) => {
    const world = createWorld(t);
    const portal = world.group.getObjectByName("QuantProductPortal");

    update(world, withLandmarkActivity({ observe: 0, resolve: 1, act: 0 }));
    const inactiveScale = portal.scale.x;
    update(world, withLandmarkActivity({ observe: 0, resolve: 0, act: 1 }));

    assert.ok(portal.scale.x > inactiveScale);
});

test("illuminates only the route segment near the active chapter", (t) => {
    const world = createWorld(t);
    const routeLights = world.group.getObjectByName("InstancedRouteLamps");

    update(world, withLandmarkActivity({ observe: 1, resolve: 0, act: 0 }));
    assert.ok(routeLights.instanceColor, "route lights require per-instance color");

    const observeColor = new THREE.Color();
    const resolveColor = new THREE.Color();
    routeLights.getColorAt(0, observeColor);
    routeLights.getColorAt(5, resolveColor);
    assert.ok(observeColor.r > resolveColor.r);

    update(world, withLandmarkActivity({ observe: 0, resolve: 0, act: 1 }));
    const previousObserveRed = observeColor.r;
    const actColor = new THREE.Color();
    routeLights.getColorAt(0, observeColor);
    routeLights.getColorAt(9, actColor);
    assert.ok(observeColor.r < previousObserveRed);
    assert.ok(actColor.r > observeColor.r);
});

test("uses route activity to brighten lamps independently of landmarks", (t) => {
    const world = createWorld(t);
    const routeLights = world.group.getObjectByName("InstancedRouteLamps");
    const courierPose = {
        ...createCourierLoopPose(0, false),
        landmarkActivity: { observe: 0, resolve: 0, act: 0 }
    };
    const inactiveColor = new THREE.Color();
    const activeColor = new THREE.Color();

    update(world, { ...courierPose, routeActivity: 0 });
    routeLights.getColorAt(5, inactiveColor);
    update(world, { ...courierPose, routeActivity: 1 });
    routeLights.getColorAt(5, activeColor);

    assert.ok(activeColor.r > inactiveColor.r);
});

test("moves the courier continuously and lights its nearest landmark", (t) => {
    const world = createWorld(t);
    const courier = world.group.getObjectByName("QuantCourier");
    update(world, createCourierLoopPose(7000, false), 7000);
    const resolvePosition = courier.position.clone();
    const portal = world.group.getObjectByName("QuantProductPortal");
    const inactivePortalScale = portal.scale.x;
    update(world, createCourierLoopPose(15000, false), 15000);
    assert.ok(courier.position.distanceTo(resolvePosition) > 1);
    assert.ok(portal.scale.x > inactivePortalScale);
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

test("rolls back every tracked resource when world construction throws", () => {
    const allocations = [];
    const trackDisposable = (Base) => class extends Base {
        constructor(...args) {
            super(...args);
            const dispose = this.dispose.bind(this);
            this.disposeCount = 0;
            this.dispose = () => {
                this.disposeCount += 1;
                dispose();
            };
            allocations.push(this);
        }
    };
    class ThrowingMesh extends THREE.Mesh {
        constructor(...args) {
            super(...args);
            throw new Error("injected world construction failure");
        }
    }
    const FaultThree = {
        ...THREE,
        DataTexture: trackDisposable(THREE.DataTexture),
        MeshToonMaterial: trackDisposable(THREE.MeshToonMaterial),
        MeshBasicMaterial: trackDisposable(THREE.MeshBasicMaterial),
        BoxGeometry: trackDisposable(THREE.BoxGeometry),
        CylinderGeometry: trackDisposable(THREE.CylinderGeometry),
        IcosahedronGeometry: trackDisposable(THREE.IcosahedronGeometry),
        ConeGeometry: trackDisposable(THREE.ConeGeometry),
        TorusGeometry: trackDisposable(THREE.TorusGeometry),
        SphereGeometry: trackDisposable(THREE.SphereGeometry),
        CapsuleGeometry: trackDisposable(THREE.CapsuleGeometry),
        Mesh: ThrowingMesh
    };

    assert.throws(
        () => createQuantPlanetWorld(FaultThree, { profile }),
        /injected world construction failure/
    );
    assert.ok(allocations.length > 0);
    assert.ok(allocations.every((resource) => resource.disposeCount === 1));
});
