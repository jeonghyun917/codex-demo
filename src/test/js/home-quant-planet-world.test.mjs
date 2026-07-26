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

function collectMeshPoints(root) {
    const points = [];
    const instanceMatrix = new THREE.Matrix4();
    const worldMatrix = new THREE.Matrix4();
    root.updateMatrixWorld(true);
    root.traverse((object) => {
        if (!object.isMesh || object.visible === false) {
            return;
        }
        const positions = object.geometry.attributes.position;
        const appendPoints = (matrix) => {
            for (let index = 0; index < positions.count; index += 1) {
                points.push(new THREE.Vector3()
                    .fromBufferAttribute(positions, index)
                    .applyMatrix4(matrix));
            }
        };
        if (object.isInstancedMesh) {
            for (let index = 0; index < object.count; index += 1) {
                object.getMatrixAt(index, instanceMatrix);
                worldMatrix.multiplyMatrices(object.matrixWorld, instanceMatrix);
                appendPoints(worldMatrix);
            }
            return;
        }
        appendPoints(object.matrixWorld);
    });
    return points;
}

function collectRenderedCenters(root) {
    const points = [];
    const instanceMatrix = new THREE.Matrix4();
    const worldMatrix = new THREE.Matrix4();
    const center = new THREE.Vector3();
    root.updateMatrixWorld(true);
    root.traverse((object) => {
        if (!object.isMesh || object.visible === false) {
            return;
        }
        object.geometry.computeBoundingBox();
        object.geometry.boundingBox.getCenter(center);
        if (object.isInstancedMesh) {
            for (let index = 0; index < object.count; index += 1) {
                object.getMatrixAt(index, instanceMatrix);
                worldMatrix.multiplyMatrices(
                    object.matrixWorld,
                    instanceMatrix
                );
                points.push(center.clone().applyMatrix4(worldMatrix));
            }
            return;
        }
        points.push(center.clone().applyMatrix4(object.matrixWorld));
    });
    return points;
}

function authoredLandmarkOccupancy(root) {
    root.updateMatrixWorld(true);
    const landmarkCenters = [];
    const representatives = [];
    const anchorPosition = new THREE.Vector3();
    root.traverse((object) => {
        if (!object.userData?.landmarkAnchor) {
            return;
        }
        const centers = collectRenderedCenters(object);
        landmarkCenters.push(...centers);
        object.getWorldPosition(anchorPosition);
        const closest = centers.toSorted(
            (left, right) => left.distanceToSquared(anchorPosition)
                - right.distanceToSquared(anchorPosition)
        )[0];
        if (closest) {
            representatives.push(closest);
        }
    });
    if (landmarkCenters.length === 0) {
        return {
            landmarkCount: 0,
            widthToHeight: 0,
            occupiedQuadrants: 0,
            occupiedAzimuthSectors: 0
        };
    }
    const points = landmarkCenters;
    const bounds = new THREE.Box3().setFromPoints(points);
    const center = bounds.getCenter(new THREE.Vector3());
    const width = bounds.max.x - bounds.min.x;
    const height = bounds.max.y - bounds.min.y;
    const quadrants = new Set();
    points.forEach((point) => {
        if (Math.abs(point.x - center.x) < 1e-6
                || Math.abs(point.y - center.y) < 1e-6) {
            return;
        }
        quadrants.add(`${point.x < center.x ? "left" : "right"}-${
            point.y < center.y ? "lower" : "upper"
        }`);
    });
    return {
        landmarkCount: representatives.length,
        widthToHeight: width / height,
        occupiedQuadrants: quadrants.size,
        occupiedAzimuthSectors: new Set(representatives.map((point) => {
            const angle = Math.atan2(point.z, point.x);
            return (Math.round(angle / (Math.PI * 2) * 8) + 8) % 8;
        })).size
    };
}

test("builds every authored reference silhouette group", (t) => {
    const world = createWorld(t);

    for (const name of [
        "ReferencePlanetCore",
        "ReferenceWaterBand",
        "ReferenceTemplePeak",
        "ReferenceIndustrialCluster",
        "ReferenceUtilityCluster",
        "ReferenceLowerCanopy",
        "ReferenceFloatingDebris"
    ]) {
        assert.ok(world.group.getObjectByName(name), `Missing ${name}`);
    }
});

test("each authored district owns rendered landmark geometry", (t) => {
    const world = createWorld(t);
    const districts = [];
    world.group.traverse((object) => {
        if (!object.userData?.landmarkAnchor) {
            return;
        }
        districts.push(object);
    });

    assert.equal(districts.length, 8);
    districts.forEach((district) => {
        assert.ok(
            collectRenderedCenters(district).length > 0,
            `${district.name} must own rendered landmark geometry`
        );
    });
});

test("each village cluster contains its own instanced buildings", (t) => {
    const world = createWorld(t);
    [
        "ReferenceMountainVillage",
        "ReferenceForestHamlet",
        "ReferenceBacksideVillage",
        "ReferenceWaterVillage"
    ].forEach((name) => {
        const village = world.group.getObjectByName(name);
        assert.ok(village, `Missing ${name}`);
        const meshes = [];
        village.traverse((object) => {
            if (object.isMesh) {
                meshes.push(object);
            }
        });
        assert.ok(meshes.length > 0, `${name} must contain rendered geometry`);
        assert.ok(
            meshes.every((mesh) => mesh.isInstancedMesh && mesh.count >= 3),
            `${name} must instance its authored buildings`
        );
    });
});

test("uses only the world angle for deterministic primary yaw", (t) => {
    const world = createWorld(t);
    const firstPose = {
        worldAngle: .75,
        dragAngle: -.25,
        pointer: { x: -1, y: .75 },
        elapsedMs: 4200,
        reduceMotion: false
    };
    world.update(firstPose);
    world.group.updateMatrix();
    const firstMatrix = world.group.matrix.clone();
    world.update(firstPose);
    world.group.updateMatrix();

    assert.equal(world.group.rotation.x, 0);
    assert.equal(world.group.rotation.y, .75);
    assert.equal(world.group.rotation.z, 0);
    assert.deepEqual(world.group.matrix.elements, firstMatrix.elements);

    world.update({
        ...firstPose,
        worldAngle: .25,
        dragAngle: .25,
        pointer: { x: 1, y: -1 }
    });
    world.group.updateMatrix();
    assert.equal(world.group.rotation.y, .25);
    assert.notDeepEqual(world.group.matrix.elements, firstMatrix.elements);
});

test("planet yaw is unaffected by pointer-shaped extra input", (t) => {
    const world = createWorld(t);
    world.update({
        worldAngle: .7,
        pointer: { x: -1, y: 1 },
        elapsedMs: 1000,
        reduceMotion: false
    });
    const first = world.group.quaternion.clone();
    world.update({
        worldAngle: .7,
        pointer: { x: 1, y: -1 },
        elapsedMs: 1100,
        reduceMotion: false
    });

    assert.deepEqual(world.group.quaternion.toArray(), first.toArray());
});

test("keeps rendered landmark content occupied through eight yaw samples", (t) => {
    const world = createWorld(t);
    for (let index = 0; index < 8; index += 1) {
        world.update({
            worldAngle: index * Math.PI / 4,
            elapsedMs: index * 1000,
            reduceMotion: false
        });
        const coverage = authoredLandmarkOccupancy(world.group);
        assert.equal(coverage.landmarkCount, 8);
        assert.equal(coverage.occupiedAzimuthSectors, 8);
        assert.ok(coverage.widthToHeight >= .72);
        assert.ok(coverage.widthToHeight <= 1.28);
        assert.ok(coverage.occupiedQuadrants >= 4);
    }
});

test("animates water and utility details deterministically unless motion is reduced", (t) => {
    const world = createWorld(t);
    const water = world.group.getObjectByName("ReferenceWaterSurface");
    const rotor = world.group.getObjectByName("ReferenceUtilityRotor");
    assert.ok(water, "Missing ReferenceWaterSurface");
    assert.ok(rotor, "Missing ReferenceUtilityRotor");

    world.update({
        worldAngle: 0,
        dragAngle: 0,
        pointer: { x: 0, y: 0 },
        elapsedMs: 4000,
        reduceMotion: false
    });
    assert.equal(water.material.uniforms.time.value, 4);
    assert.equal(rotor.rotation.z, 1.4);

    world.update({
        worldAngle: 0,
        dragAngle: 0,
        pointer: { x: 1, y: -1 },
        elapsedMs: 9000,
        reduceMotion: true
    });
    assert.equal(water.material.uniforms.time.value, 0);
    assert.equal(rotor.rotation.z, 0);
});

test("exposes a complete reference silhouette envelope", (t) => {
    const world = createWorld(t);
    const envelope = world.getFramingEnvelope();
    assert.ok(envelope.min, "Missing envelope.min");
    assert.ok(envelope.max, "Missing envelope.max");

    assert.ok(envelope.min.x <= -4.8);
    assert.ok(envelope.max.x >= 4.4);
    assert.ok(envelope.max.y >= 5.2);
    assert.ok(envelope.min.y <= -4.9);
});

test("fits the public envelope tightly to actual mesh and instance bounds", (t) => {
    const world = createWorld(t);
    const envelope = world.getFramingEnvelope();
    assert.ok(envelope.min, "Missing envelope.min");
    assert.ok(envelope.max, "Missing envelope.max");
    const center = new THREE.Vector3(
        envelope.center.x,
        envelope.center.y,
        envelope.center.z
    );
    const points = collectMeshPoints(world.group);
    const actualRadius = Math.max(
        ...points.map((point) => point.distanceTo(center))
    );
    const actualMin = new THREE.Box3().setFromPoints(points).min;
    const actualMax = new THREE.Box3().setFromPoints(points).max;

    assert.ok(actualRadius <= envelope.radius + 1e-6);
    assert.ok(envelope.radius - actualRadius <= .02);
    assert.deepEqual(envelope.min, {
        x: actualMin.x,
        y: actualMin.y,
        z: actualMin.z
    });
    assert.deepEqual(envelope.max, {
        x: actualMax.x,
        y: actualMax.y,
        z: actualMax.z
    });

    envelope.center.y = 999;
    envelope.min.x = 999;
    assert.notEqual(world.getFramingEnvelope().center.y, 999);
    assert.notEqual(world.getFramingEnvelope().min.x, 999);
});

test("builds an irregular rounded reference core", (t) => {
    const world = createWorld(t);
    const land = world.group.getObjectByName("ReferencePlanetBody");
    assert.ok(land, "Missing ReferencePlanetBody");
    world.group.updateMatrixWorld(true);
    const center = land.localToWorld(new THREE.Vector3());
    const positions = land.geometry.attributes.position;
    const radii = Array.from({ length: positions.count }, (_, index) =>
        new THREE.Vector3().fromBufferAttribute(positions, index)
            .applyMatrix4(land.matrixWorld)
            .distanceTo(center));

    assert.ok(Math.max(...radii) / Math.min(...radii) <= 1.3);
});

test("instances repeated canopy, rock, roof, and debris geometry", (t) => {
    const world = createWorld(t);
    [
        "ReferenceCanopyCrowns",
        "ReferenceCoreRocks",
        "ReferenceTempleRoofPieces",
        "ReferenceDebrisPieces"
    ].forEach((name) => {
        const mesh = world.group.getObjectByName(name);
        assert.equal(mesh?.isInstancedMesh, true, `${name} must be instanced`);
        assert.ok(mesh.count > 0, `${name} must contain authored instances`);
    });
});

test("keeps canopy instance greens above the crushed-black floor", (t) => {
    const world = createWorld(t);
    const crowns = world.group.getObjectByName("ReferenceCanopyCrowns");
    const instanceColor = new THREE.Color();

    assert.equal(crowns.material.emissive.getHex(), 0x205f43);
    assert.equal(crowns.material.emissiveIntensity, .8);
    for (let index = 0; index < crowns.count; index += 1) {
        crowns.getColorAt(index, instanceColor);
        const shadedBase = crowns.material.color.clone()
            .multiply(instanceColor);
        assert.ok(
            shadedBase.g >= .1,
            `Canopy ${index} green floor ${shadedBase.g} is too dark`
        );
        assert.ok(shadedBase.g > shadedBase.r);
        assert.ok(shadedBase.g > shadedBase.b);
    }
});

test("stays within the high-profile world render budget", (t) => {
    const world = createWorld(t);
    assert.equal(typeof world.getRenderStats, "function");
    const stats = world.getRenderStats();

    assert.ok(stats.drawCalls + stats.shadowCalls <= 75);
    assert.ok(stats.triangles <= 140000);
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
        ShaderMaterial: trackDisposable(THREE.ShaderMaterial),
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
