import assert from "node:assert/strict";
import test from "node:test";
import * as THREE from "../../main/resources/static/js/vendor/three.module.js";
import * as composition from "../../main/resources/static/js/home-quant-planet-composition.js";

const {
    REFERENCE_HERO,
    fitPerspectiveSphere
} = composition;

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
                worldMatrix.multiplyMatrices(
                    object.matrixWorld,
                    instanceMatrix
                );
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
    const pixelRatios = [];
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
            this.info = { render: { calls: 43, triangles: 7155 } };
        }
        setPixelRatio(value) {
            pixelRatios.push(value);
        }
        setSize() {}
        render(scene, camera) {
            scene.updateMatrixWorld(true);
            camera.updateMatrixWorld(true);
            camera.matrixWorldInverse.copy(camera.matrixWorld).invert();
            const planet = scene.getObjectByName("QuantPlanet");
            const worldPoints = collectMeshPoints(planet);
            frames.push({
                aspect: camera.aspect,
                near: camera.near,
                far: camera.far,
                fog: scene.fog
                    ? {
                        near: scene.fog.near,
                        far: scene.fog.far
                    }
                    : null,
                position: camera.position.clone(),
                target: camera.lookAtTarget.clone(),
                worldScale: planet.scale.x,
                worldQuaternion: planet.quaternion.clone(),
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
        pixelRatios,
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
    pointerEnabled: true,
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
    const { createQuantPlanetScene } = await import(
        "../../main/resources/static/js/home-quant-planet-scene.js"
    );
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

const pose = (overrides = {}) => ({
    worldAngle: 0,
    dragAngle: 0,
    pointer: { x: 0, y: 0 },
    elapsedMs: 0,
    reduceMotion: false,
    ...overrides
});

test("exports only the reference hero and bounds fitting contracts", () => {
    assert.deepEqual(Object.keys(composition).sort(), [
        "REFERENCE_HERO",
        "fitPerspectiveSphere"
    ]);
});

test("fits complete bounds against the stricter viewport field of view", () => {
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

test("forwards the Task 3 pose directly to the world without extra yaw", async (t) => {
    const scene = await createTestScene(t, {
        width: 1440,
        height: 900
    });
    const directPose = pose({
        worldAngle: .45,
        dragAngle: -.17,
        pointer: { x: .4, y: -.25 },
        elapsedMs: 1800
    });

    assert.equal(scene.controller.render(directPose), true);
    const actual = scene.frames.at(-1).worldQuaternion;
    const expected = new THREE.Quaternion().setFromEuler(
        new THREE.Euler(0, .28, 0)
    );
    assert.ok(actual.angleTo(expected) < 1e-7);
});

test("refits complete world bounds after landscape and portrait resizes", async (t) => {
    const scene = await createTestScene(t, {
        width: 1440,
        height: 900
    });

    assert.equal(scene.controller.render(pose()), true);
    scene.canvas.clientWidth = 375;
    scene.canvas.clientHeight = 812;
    scene.controller.resize();
    assert.equal(scene.controller.render(pose()), true);

    const [landscape, portrait] = scene.frames;
    assert.equal(landscape.aspect, 1440 / 900);
    assert.equal(portrait.aspect, 375 / 812);
    assert.ok(
        portrait.position.distanceTo(portrait.target)
        > landscape.position.distanceTo(landscape.target)
    );
    portrait.projected.forEach((point) => {
        assert.ok(Number.isFinite(point.x));
        assert.ok(Number.isFinite(point.y));
        assert.ok(Number.isFinite(point.z));
        assert.ok(Math.abs(point.x) <= 1 + 1e-6);
        assert.ok(Math.abs(point.y) <= 1 + 1e-6);
        assert.ok(Math.abs(point.z) <= 1 + 1e-6);
    });
    assert.ok(portrait.near < Math.min(...portrait.depths));
    assert.ok(portrait.far > Math.max(...portrait.depths));
});

test("applies profile-gated pointer parallax without changing world pose", async (t) => {
    const enabled = await createTestScene(t, {
        width: 1440,
        height: 900,
        profile: sceneProfile({ pointerEnabled: true })
    });
    enabled.controller.render(pose({ pointer: { x: -1, y: -.5 } }));
    enabled.controller.render(pose({ pointer: { x: 1, y: .5 } }));
    const [enabledLeft, enabledRight] = enabled.frames;
    assert.ok(enabledLeft.position.distanceTo(enabledRight.position) > .1);
    assert.ok(
        enabledLeft.worldQuaternion.angleTo(enabledRight.worldQuaternion)
        < 1e-9
    );

    const disabled = await createTestScene(t, {
        width: 1440,
        height: 900,
        profile: sceneProfile({
            name: "balanced",
            pointerEnabled: false
        })
    });
    disabled.controller.render(pose({ pointer: { x: -1, y: -.5 } }));
    disabled.controller.render(pose({ pointer: { x: 1, y: .5 } }));
    const [disabledLeft, disabledRight] = disabled.frames;
    assert.ok(disabledLeft.position.distanceTo(disabledRight.position) < 1e-9);
    assert.ok(disabledLeft.target.distanceTo(disabledRight.target) < 1e-9);
});

test("caps renderer DPR and preserves diagnostics budgets", async (t) => {
    const scene = await createTestScene(t, {
        width: 1440,
        height: 900,
        profile: sceneProfile({ pixelRatio: 3 })
    });

    scene.controller.render(pose());
    assert.deepEqual(scene.pixelRatios, [1.5]);
    assert.deepEqual(scene.controller.getDiagnostics(), {
        drawCalls: 43,
        triangles: 7155,
        limits: { drawCalls: 75, triangles: 140000 },
        withinBudget: true
    });
});

test("keeps authored world colors unobscured at every fitted camera depth", async (t) => {
    const scene = await createTestScene(t, {
        width: 1190,
        height: 1087
    });

    assert.equal(scene.controller.render(pose()), true);
    scene.canvas.clientWidth = 375;
    scene.canvas.clientHeight = 812;
    scene.controller.resize();
    assert.equal(scene.controller.render(pose()), true);

    assert.equal(scene.frames[0].fog, null);
    assert.equal(scene.frames[1].fog, null);
});
