import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import * as THREE from "../../main/resources/static/js/vendor/three.module.js";
import {
    PRODUCT_SCENES,
    REFERENCE_TIMING,
    createProductSelection,
    fitPerspectiveSphere,
    resolveAdjacentProduct,
    resolveSelectedProduct
} from "../../main/resources/static/js/home-quant-planet-composition.js";
import {
    createQuantPlanetScene
} from "../../main/resources/static/js/home-quant-planet-scene.js";
import {
    createQuantPlanetWorld
} from "../../main/resources/static/js/home-quant-planet-world.js";

const localImports = (source) => Array.from(
    source.matchAll(/^import\s+[\s\S]*?\s+from\s+["']([^"']+)["'];?$/gm),
    ([, specifier]) => specifier
);

function collectMeshPoints(root, predicate = () => true) {
    const points = [];
    const instanceMatrix = new THREE.Matrix4();
    const worldMatrix = new THREE.Matrix4();
    root.updateMatrixWorld(true);
    root.traverse((object) => {
        if (!object.isMesh || object.visible === false || !predicate(object)) {
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

function createSceneHarness() {
    const frames = [];
    class InspectableCamera extends THREE.PerspectiveCamera {
        lookAt(...values) {
            const [first] = values;
            this.lookAtTarget = first?.isVector3
                ? first.clone()
                : new THREE.Vector3(...values);
            super.lookAt(...values);
        }
    }
    class TestRenderer {
        constructor() {
            this.shadowMap = {};
            this.info = { render: { calls: 0, triangles: 0 } };
        }
        setPixelRatio() {}
        setSize() {}
        render(scene, camera) {
            scene.updateMatrixWorld(true);
            camera.updateMatrixWorld(true);
            camera.matrixWorldInverse.copy(camera.matrixWorld).invert();
            const planet = scene.getObjectByName("QuantPlanet");
            const worldPoints = collectMeshPoints(planet);
            const primaryPoints = collectMeshPoints(
                planet,
                (object) => object.name === "FacetedLand"
                    || object.name === "FacetedLandOutline"
                    || object.name === "ShallowWaterShell"
            );
            frames.push({
                aspect: camera.aspect,
                near: camera.near,
                far: camera.far,
                position: camera.position.clone(),
                target: camera.lookAtTarget.clone(),
                worldScale: planet.scale.x,
                worldQuaternion: planet.quaternion.clone(),
                worldPoints,
                primaryProjected: primaryPoints.map((point) =>
                    point.clone().project(camera)),
                projected: worldPoints.map((point) =>
                    point.clone().project(camera)),
                depths: worldPoints.map((point) =>
                    -point.clone().applyMatrix4(camera.matrixWorldInverse).z)
            });
        }
        dispose() {}
    }
    return {
        frames,
        three: {
            ...THREE,
            PerspectiveCamera: InspectableCamera,
            WebGLRenderer: TestRenderer
        }
    };
}

const sceneProfile = (overrides = {}) => ({
    name: "high",
    webgl: true,
    outlineLevel: 2,
    foliageCount: 34,
    cloudCount: 8,
    shadows: false,
    pixelRatio: 1,
    planetScale: 1,
    ...overrides
});

async function createTestScene(t, {
    width,
    height,
    profile = sceneProfile()
}) {
    const harness = createSceneHarness();
    const canvas = { clientWidth: width, clientHeight: height };
    const controller = await createQuantPlanetScene({
        canvas,
        profile,
        three: harness.three
    });
    t.after(() => controller.dispose());
    controller.resize();
    return { ...harness, canvas, controller };
}

function projectedOccupancy(points, axis) {
    const values = points.map((point) => point[axis]);
    return (Math.max(...values) - Math.min(...values)) / 2;
}

test("keeps the composition dependency graph acyclic", async () => {
    const compositionUrl = new URL(
        "../../main/resources/static/js/home-quant-planet-composition.js",
        import.meta.url
    );
    const motionUrl = new URL(
        "../../main/resources/static/js/home-quant-planet-motion.js",
        import.meta.url
    );
    const [compositionSource, motionSource] = await Promise.all([
        readFile(compositionUrl, "utf8"),
        readFile(motionUrl, "utf8")
    ]);
    const nodeFor = (specifier) => ({
        "./home-quant-planet-composition.js": "composition",
        "./home-quant-planet-motion.js": "motion"
    })[specifier];
    const dependencies = {
        composition: localImports(compositionSource).map(nodeFor).filter(Boolean),
        motion: localImports(motionSource).map(nodeFor).filter(Boolean)
    };

    assert.deepEqual(dependencies, {
        composition: [],
        motion: ["composition"]
    });
});

test("defines the six original Quant product scenes and exact routes", () => {
    assert.deepEqual(PRODUCT_SCENES.map(({ route }) => route), [
        "/quant",
        "/stocks",
        "/stocks/heatmap?index=SP500",
        "/etfs",
        "/atelier",
        "/signals/backtest"
    ]);
    assert.equal(
        resolveSelectedProduct(0).supporting,
        "Observe · Resolve · Act"
    );
    assert.deepEqual(resolveSelectedProduct(2).lines, ["MARKET", "HEATMAP"]);
    assert.equal(resolveSelectedProduct(5).supporting, "Backtest field");
});

test("fits a complete sphere against the stricter viewport field of view", () => {
    const landscape = fitPerspectiveSphere({
        center: { x: 0, y: .4, z: 0 },
        radius: 3.8,
        aspect: 16 / 9,
        verticalFovDegrees: 34,
        margin: 1.18
    });
    const portrait = fitPerspectiveSphere({
        center: { x: 0, y: .4, z: 0 },
        radius: 3.8,
        aspect: 375 / 812,
        verticalFovDegrees: 34,
        margin: 1.18
    });
    assert.deepEqual(landscape.target, { x: 0, y: .4, z: 0 });
    assert.ok(landscape.distance > 3.8);
    assert.ok(portrait.distance > landscape.distance);
});

test("refits the complete world after landscape and portrait resizes", async (t) => {
    const profile = sceneProfile({ outlineLevel: 0, foliageCount: 0, cloudCount: 0 });
    const world = createQuantPlanetWorld(THREE, { profile });
    const envelope = world.getFramingEnvelope();
    world.dispose();
    const { canvas, controller, frames } = await createTestScene(t, {
        width: 1440,
        height: 900,
        profile
    });

    assert.equal(controller.render(0), true);
    canvas.clientWidth = 375;
    canvas.clientHeight = 812;
    controller.resize();
    assert.equal(controller.render(0), true);

    const [landscape, portrait] = frames;
    assert.equal(landscape.aspect, 1440 / 900);
    assert.equal(portrait.aspect, 375 / 812);
    [landscape, portrait].forEach(({ target, worldQuaternion }) => {
        const expectedTarget = new THREE.Vector3(
            envelope.center.x,
            envelope.center.y,
            envelope.center.z
        ).applyQuaternion(worldQuaternion);
        assert.deepEqual(target.toArray(), [
            expectedTarget.x,
            expectedTarget.y,
            expectedTarget.z
        ]);
    });
    const landscapeDistance = landscape.position.distanceTo(landscape.target);
    const portraitDistance = portrait.position.distanceTo(portrait.target);
    assert.ok(portraitDistance > landscapeDistance);
    const verticalFov = 34 * Math.PI / 180;
    const portraitHorizontalFov = 2 * Math.atan(
        Math.tan(verticalFov / 2) * portrait.aspect
    );
    const framedRadius = portraitDistance
        * Math.sin(Math.min(verticalFov, portraitHorizontalFov) / 2);
    assert.ok(framedRadius >= envelope.radius);
});

test("scales the complete framing envelope with the scene world", async (t) => {
    const full = await createTestScene(t, {
        width: 1440,
        height: 900,
        profile: sceneProfile({ planetScale: 1 })
    });
    const balanced = await createTestScene(t, {
        width: 1440,
        height: 900,
        profile: sceneProfile({ planetScale: .82 })
    });
    full.controller.render(0);
    balanced.controller.render(0);
    const fullFrame = full.frames.at(-1);
    const balancedFrame = balanced.frames.at(-1);
    const fullDistance = fullFrame.position.distanceTo(fullFrame.target);
    const balancedDistance = balancedFrame.position.distanceTo(
        balancedFrame.target
    );

    assert.ok(Math.abs(balancedDistance / fullDistance - .82) <= 1e-6);
    assert.ok(Math.abs(
        balancedFrame.target.length() / fullFrame.target.length() - .82
    ) <= 1e-6);
});

test("derives safe portrait clip planes from fitted world bounds", async (t) => {
    const scene = await createTestScene(t, {
        width: 375,
        height: 812
    });
    scene.controller.render(0);
    const frame = scene.frames.at(-1);
    const nearestDepth = Math.min(...frame.depths);
    const farthestDepth = Math.max(...frame.depths);

    assert.ok(frame.near > .1);
    assert.ok(frame.near < nearestDepth);
    assert.ok(frame.far > farthestDepth);
});

test("keeps actual transformed bounds visible at extreme orbit poses", async (t) => {
    const scene = await createTestScene(t, {
        width: 375,
        height: 812,
        profile: sceneProfile({ planetScale: .82 })
    });
    const fixtures = [
        { pointer: { x: -1, y: -1 }, dragAngle: -Math.PI * 4, time: 0 },
        { pointer: { x: 1, y: 1 }, dragAngle: Math.PI * 4, time: 14000 }
    ];

    fixtures.forEach(({ pointer, dragAngle, time }) => {
        scene.controller.setPointer(pointer);
        scene.controller.setDragAngle(dragAngle);
        assert.equal(scene.controller.render(time), true);
        const frame = scene.frames.at(-1);
        frame.projected.forEach((point) => {
            assert.ok(Number.isFinite(point.x));
            assert.ok(Number.isFinite(point.y));
            assert.ok(Number.isFinite(point.z));
            assert.ok(Math.abs(point.x) <= 1 + 1e-6);
            assert.ok(Math.abs(point.y) <= 1 + 1e-6);
            assert.ok(Math.abs(point.z) <= 1 + 1e-6);
        });
    });
});

test("real scene adds no render-time yaw beyond the runtime world angle", async (t) => {
    const scene = await createTestScene(t, {
        width: 1440,
        height: 900
    });
    scene.controller.setDragAngle(.7);
    assert.equal(scene.controller.render(0), true);
    const initialRotation = scene.frames.at(-1).worldQuaternion;

    assert.equal(
        scene.controller.render(REFERENCE_TIMING.worldRevolutionMs / 2),
        true
    );
    const laterRotation = scene.frames.at(-1).worldQuaternion;

    assert.ok(initialRotation.angleTo(laterRotation) < 1e-7);
});

test("composes the primary silhouette at desktop and mobile occupancy", async (t) => {
    const profile = sceneProfile({
        name: "balanced",
        outlineLevel: 1,
        foliageCount: 18,
        cloudCount: 5,
        planetScale: .82
    });
    const desktop = await createTestScene(t, {
        width: 1440,
        height: 900,
        profile
    });
    const mobile = await createTestScene(t, {
        width: 375,
        height: 812,
        profile
    });
    desktop.controller.render(0);
    mobile.controller.render(0);
    const desktopOccupancy = projectedOccupancy(
        desktop.frames.at(-1).primaryProjected,
        "y"
    );
    const mobileOccupancy = projectedOccupancy(
        mobile.frames.at(-1).primaryProjected,
        "x"
    );

    assert.ok(
        desktopOccupancy >= .58 && desktopOccupancy <= .68,
        `desktop occupancy ${desktopOccupancy}`
    );
    assert.ok(
        mobileOccupancy >= .68 && mobileOccupancy <= .82,
        `mobile occupancy ${mobileOccupancy}`
    );
});

test("snaps drag selection to one of six wrapped product scenes", () => {
    const state = createProductSelection({
        rotationAngle: 0,
        dragAngle: Math.PI * .34,
        selectedIndex: 0,
        snap: true,
        count: 6
    });
    assert.equal(Number.isInteger(state.selectedIndex), true);
    assert.equal(state.items.length, 6);
    assert.equal(resolveAdjacentProduct(0, -1).route, "/signals/backtest");
});

test("centers the snapped sector and gives its adjacent labels equal depth", () => {
    const state = createProductSelection({
        rotationAngle: 0,
        dragAngle: 0,
        selectedIndex: 2,
        snap: true,
        count: 6
    });

    assert.equal(state.selectedIndex, 2);
    assert.equal(state.frontIndex, 2);
    assert.equal(state.items[2].depth, 1);
    assert.equal(state.items[1].depth, state.items[3].depth);
});
