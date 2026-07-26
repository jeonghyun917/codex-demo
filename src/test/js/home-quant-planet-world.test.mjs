import assert from "node:assert/strict";
import test from "node:test";
import * as THREE from "../../main/resources/static/js/vendor/three.module.js";
import {
    createQuantPlanetWorld
} from "../../main/resources/static/js/home-quant-planet-world.js";
import {
    FOREST_BELTS
} from "../../main/resources/static/js/home-quant-planet-world-layout.js";

const profile = {
    name: "high",
    outlineLevel: 2,
    foliageCount: 34,
    cloudCount: 8,
    shadows: true
};

const balancedProfile = {
    name: "balanced",
    webgl: true,
    pixelRatio: 1.25,
    pointerEnabled: true,
    shadows: false,
    outlineLevel: 1,
    foliageCount: 18,
    cloudCount: 5,
    planetScale: .82
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

function authoredDistrictOccupancy(root) {
    root.updateMatrixWorld(true);
    const representatives = [];
    root.traverse((object) => {
        if (!object.userData?.landmarkAnchor) {
            return;
        }
        representatives.push(object.getWorldPosition(new THREE.Vector3()));
    });
    if (representatives.length === 0) {
        return {
            districtCount: 0,
            occupiedAzimuthSectors: 0
        };
    }
    return {
        districtCount: representatives.length,
        occupiedAzimuthSectors: new Set(representatives.map((point) => {
            const angle = Math.atan2(point.z, point.x);
            return (Math.round(angle / (Math.PI * 2) * 8) + 8) % 8;
        })).size
    };
}

function sectorForSurfacePosition(position) {
    const yaw = (Math.atan2(position.z, position.x) * 180 / Math.PI + 360)
        % 360;
    return Math.floor((yaw + 22.5) / 45) % 8;
}

function instanceTransforms(mesh) {
    const matrix = new THREE.Matrix4();
    const position = new THREE.Vector3();
    const quaternion = new THREE.Quaternion();
    const scale = new THREE.Vector3();
    return Array.from({ length: mesh.count }, (_, index) => {
        mesh.getMatrixAt(index, matrix);
        matrix.decompose(position, quaternion, scale);
        return {
            index,
            position: position.clone(),
            scale: scale.clone(),
            matrix: matrix.clone()
        };
    });
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

test("each authored district maps to dense rendered surface geometry", (t) => {
    const world = createWorld(t);
    const sectors = new Map(
        world.getSurfaceDensity().sectors.map(
            (sector) => [sector.districtId, sector]
        )
    );
    const districts = [];
    world.group.traverse((object) => {
        if (!object.userData?.landmarkAnchor) {
            return;
        }
        districts.push(object);
    });

    assert.equal(districts.length, 8);
    districts.forEach((district) => {
        const sector = sectors.get(district.userData.districtId);
        assert.ok(
            sector?.total >= 20,
            `${district.name} must map to dense rendered geometry`
        );
    });
});

test("villages share one rendered building instance family", (t) => {
    const world = createWorld(t);
    [
        "ReferenceMountainVillage",
        "ReferenceForestHamlet",
        "ReferenceBacksideVillage",
        "ReferenceWaterVillage"
    ].forEach((name) => {
        const village = world.group.getObjectByName(name);
        assert.ok(village, `Missing ${name}`);
        assert.ok(village.userData.villageCluster);
    });
    const buildings = world.group.getObjectByName(
        "ReferenceVillageBuildingFamily"
    );
    assert.equal(buildings?.isInstancedMesh, true);
    assert.ok(buildings.count >= 72);
});

test("exposes large village bodies and terrain shelves in every sector", (t) => {
    const world = createWorld(t);
    const buildings = world.group.getObjectByName(
        "ReferenceVillageBuildingFamily"
    );
    const outlines = world.group.getObjectByName(
        "ReferenceVillageOutlineFamily"
    );
    const terrain = world.group.getObjectByName("ReferenceTerrainPatches");

    assert.equal(buildings?.isInstancedMesh, true);
    assert.equal(outlines?.isInstancedMesh, true);
    assert.equal(terrain?.isInstancedMesh, true);

    const buildingBodies = instanceTransforms(buildings).filter(
        ({ index }) => index % 3 === 0
    );
    const outlineBodies = instanceTransforms(outlines).filter(
        ({ index }) => index % 3 === 0
    );
    assert.deepEqual(
        outlineBodies.map(({ matrix }) => matrix.elements),
        buildingBodies.map(({ matrix }) => matrix.elements),
        "the graphite outline family must use the building body matrices"
    );

    for (let sector = 0; sector < 8; sector += 1) {
        const visibleVillageBodies = buildingBodies.filter(({ position, scale }) =>
            sectorForSurfacePosition(position) === sector
                && scale.x >= .48
                && scale.y >= .50
                && position.length() >= 3.55
        );
        const visibleTerrain = instanceTransforms(terrain).filter(
            ({ position, scale }) => {
                const broadAxes = [scale.x, scale.y, scale.z]
                    .map(Math.abs)
                    .toSorted((left, right) => right - left);
                return sectorForSurfacePosition(position) === sector
                    && position.length() >= 3.48
                    && broadAxes[0] >= .48
                    && broadAxes[1] >= .38;
            }
        );
        assert.ok(
            visibleVillageBodies.length >= 3,
            `sector ${sector} must expose three large village bodies`
        );
        assert.ok(
            visibleTerrain.length >= 6,
            `sector ${sector} must expose six broad terrain shelves`
        );
    }
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

test("keeps semantic districts distributed through eight yaw samples", (t) => {
    const world = createWorld(t);
    for (let index = 0; index < 8; index += 1) {
        world.update({
            worldAngle: index * Math.PI / 4,
            elapsedMs: index * 1000,
            reduceMotion: false
        });
        const coverage = authoredDistrictOccupancy(world.group);
        assert.equal(coverage.districtCount, 8);
        assert.equal(coverage.occupiedAzimuthSectors, 8);
    }
});

test("derives dense front-only coverage from rendered surface placements", (t) => {
    const world = createWorld(t);
    assert.equal(typeof world.getSurfaceDensity, "function");
    const density = world.getSurfaceDensity();
    const categories = [
        "terrain",
        "structure",
        "foliageRock",
        "utilitySilhouette"
    ];
    const minimums = {
        terrain: 6,
        structure: 3,
        foliageRock: 6,
        utilitySilhouette: 5
    };
    const renderedDetailIds = new Set();
    world.group.traverse((object) => {
        object.userData?.surfaceDetailIds?.forEach((id) => {
            renderedDetailIds.add(id);
        });
    });

    assert.ok(Object.isFrozen(density));
    assert.ok(Object.isFrozen(density.detailIds));
    assert.deepEqual(
        [...renderedDetailIds].toSorted(),
        [...density.detailIds].toSorted(),
        "the diagnostic must describe the same placements used by meshes"
    );
    assert.equal(density.totalDetails, density.detailIds.length);
    assert.equal(new Set(density.detailIds).size, density.totalDetails);
    assert.equal(density.sectors.length, 8);
    density.sectors.forEach((sector, index) => {
        assert.ok(Object.isFrozen(sector));
        assert.ok(Object.isFrozen(sector.counts));
        assert.equal(sector.index, index);
        assert.equal(
            sector.total,
            categories.reduce(
                (total, category) => total + sector.counts[category],
                0
            )
        );
        assert.ok(sector.total >= 20, `sector ${index} is sparse`);
        categories.forEach((category) => {
            assert.ok(
                sector.counts[category] >= minimums[category],
                `sector ${index} ${category} collapsed`
            );
        });
    });

    for (const band of ["upper", "middle", "lower"]) {
        assert.ok(
            density.latitudeBands[band] > 0,
            `${band} latitude band is empty`
        );
    }
    assert.ok(density.maximumAzimuthGapDegrees <= 18);

    assert.equal(density.boundarySamples.length, 8);
    density.boundarySamples.forEach((sample, index) => {
        assert.equal(sample.yawDegrees, index * 45 + 22.5);
        categories.forEach((category) => {
            assert.ok(
                sample.counts[category] >= minimums[category],
                `boundary ${sample.yawDegrees} ${category} collapsed`
            );
        });
    });

    assert.equal(density.frontViews.length, 8);
    density.frontViews.forEach((view, index) => {
        assert.equal(view.yawDegrees, index * 45);
        assert.equal(
            view.total,
            categories.reduce(
                (total, category) => total + view.counts[category],
                0
            )
        );
        assert.ok(view.total >= 70, `yaw ${view.yawDegrees} is sparse`);
        assert.ok(
            view.total < density.totalDetails,
            `yaw ${view.yawDegrees} counted rear geometry as foreground`
        );
        assert.ok(view.visibleCategoryCount >= 4);
        assert.deepEqual(
            [...view.projectedQuadrants].toSorted(),
            [
                "left-lower",
                "left-upper",
                "right-lower",
                "right-upper"
            ]
        );
    });
    for (let index = 0; index < 4; index += 1) {
        assert.equal(
            density.frontViews[index].total
                + density.frontViews[index + 4].total,
            density.totalDetails,
            `opposite yaw ${index} double-counted rear placements`
        );
    }
});

test("balanced profile preserves foliage density in every yaw sector", (t) => {
    const world = createQuantPlanetWorld(THREE, {
        profile: balancedProfile
    });
    t.after(() => world.dispose());
    const density = world.getSurfaceDensity();

    density.sectors.forEach((sector) => {
        assert.ok(
            sector.counts.foliageRock >= 6,
            `balanced sector ${sector.index} has only `
                + `${sector.counts.foliageRock} foliage/rock details`
        );
        assert.ok(sector.total >= 20);
    });
    density.boundarySamples.forEach((sample) => {
        assert.ok(
            sample.counts.foliageRock >= 6,
            `balanced boundary ${sample.yawDegrees} has only `
                + `${sample.counts.foliageRock} foliage/rock details`
        );
    });
    density.frontViews.forEach((view) => {
        assert.ok(view.total >= 70);
        assert.equal(view.visibleCategoryCount, 4);
        assert.equal(view.projectedQuadrants.length, 4);
    });

    const stats = world.getRenderStats();
    assert.ok(stats.drawCalls + stats.shadowCalls <= 75);
    assert.ok(stats.triangles <= 140000);
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

test("maps an injected panorama onto the core with one owned texture disposal", () => {
    const texture = new THREE.Texture();
    let disposeCalls = 0;
    texture.dispose = () => {
        disposeCalls += 1;
    };
    const world = createQuantPlanetWorld(THREE, {
        profile,
        surfaceTexture: texture
    });
    const body = world.group.getObjectByName("ReferencePlanetBody");

    assert.equal(body.material.isMeshBasicMaterial, true);
    assert.equal(body.material.map, texture);
    assert.equal(texture.colorSpace, THREE.SRGBColorSpace);
    assert.equal(texture.wrapS, THREE.RepeatWrapping);
    assert.equal(texture.wrapT, THREE.ClampToEdgeWrapping);
    assert.equal(body.material.color.getHex(), 0xffffff);
    assert.equal(body.material.toneMapped, false);

    world.dispose();
    world.dispose();
    assert.equal(disposeCalls, 1);
});

test("keeps panorama overlays thin and canopy crowns below their authored scale", () => {
    const texture = new THREE.Texture();
    const world = createQuantPlanetWorld(THREE, {
        profile,
        surfaceTexture: texture
    });
    const outlines = world.group.getObjectByName(
        "ReferenceVillageOutlineFamily"
    );
    const crowns = world.group.getObjectByName("ReferenceCanopyCrowns");
    const authoredCrownPlacements = FOREST_BELTS.flatMap(
        (belt) => belt.placements
    );

    assert.equal(outlines.material.transparent, true);
    assert.ok(outlines.material.opacity <= .35);
    assert.ok(outlines.geometry.parameters.width <= 1.04);
    assert.equal(crowns.count, authoredCrownPlacements.length);
    instanceTransforms(crowns).forEach(({ scale }, index) => {
        const authoredScale = authoredCrownPlacements[index].scale;
        ["x", "y", "z"].forEach((axis, axisIndex) => {
            assert.ok(
                scale[axis] <= authoredScale[axisIndex] * .75 + 1e-7,
                `crown ${index} ${axis} exceeds the visible-surface cap`
            );
        });
    });

    world.dispose();
});

test("uses the local panorama URL only in browser-capable worlds", () => {
    const texture = new THREE.Texture();
    const failedTexture = new THREE.Texture();
    const urls = [];
    const browserWorld = createQuantPlanetWorld(THREE, {
        profile,
        documentTarget: {},
        textureLoader: {
            load(url) {
                urls.push(url);
                return texture;
            }
        }
    });
    const fallbackWorld = createQuantPlanetWorld(THREE, {
        profile,
        documentTarget: null
    });
    const failedWorld = createQuantPlanetWorld(THREE, {
        profile,
        documentTarget: {},
        textureLoader: {
            load(url, onLoad, onProgress, onError) {
                onError(new Error("texture load failed"));
                return failedTexture;
            }
        }
    });

    assert.deepEqual(urls, ["/images/quant-planet-panorama-v2.png"]);
    assert.equal(
        browserWorld.group.getObjectByName("ReferencePlanetBody").material.map,
        texture
    );
    for (const world of [fallbackWorld, failedWorld]) {
        const body = world.group.getObjectByName("ReferencePlanetBody");
        assert.equal(body.material.map, null);
        assert.equal(body.material.color.getHex(), 0xa8bf78);
        world.dispose();
    }
    browserWorld.dispose();
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
