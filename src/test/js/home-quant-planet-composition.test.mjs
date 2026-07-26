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

function trackDisposableResources(root) {
    const resources = new Set();
    root.traverse((object) => {
        if (!object.isMesh) {
            return;
        }
        resources.add(object.geometry);
        const materials = Array.isArray(object.material)
            ? object.material
            : [object.material];
        materials.forEach((material) => {
            resources.add(material);
            Object.values(material).forEach((value) => {
                if (value?.isTexture) {
                    resources.add(value);
                }
            });
        });
    });
    const calls = new Map();
    resources.forEach((resource) => {
        const original = resource.dispose.bind(resource);
        calls.set(resource, 0);
        resource.dispose = () => {
            calls.set(resource, calls.get(resource) + 1);
            original();
        };
    });
    return calls;
}

async function createReferenceGlyphRig(Three) {
    const group = new Three.Group();
    group.name = "ReferenceGlyphRig";
    for (let index = 0; index < 9; index += 1) {
        const pivot = new Three.Group();
        pivot.name = `ReferenceGlyphPivot-${index + 1}`;
        group.add(pivot);
    }
    let disposed = false;
    return {
        group,
        setLayout({ center, radius, aspect }) {
            group.position.set(center.x, center.y, center.z + radius * 1.05);
            group.scale.setScalar(radius * (aspect < 1 ? .9 : 1));
        },
        update({ pointer, titleProgress = 1, reduceMotion }) {
            const pointerX = reduceMotion ? 0 : Number(pointer?.x) || 0;
            const pointerY = reduceMotion ? 0 : Number(pointer?.y) || 0;
            group.visible = titleProgress > 0;
            group.children.forEach((pivot, index) => {
                const response = 1 + index * .01;
                pivot.rotation.set(
                    -pointerY * response,
                    pointerX * response,
                    0
                );
            });
        },
        getRenderStats() {
            return group.visible
                ? { drawCalls: 29, shadowCalls: 0, triangles: 316 }
                : { drawCalls: 0, shadowCalls: 0, triangles: 0 };
        },
        dispose() {
            if (disposed) {
                return;
            }
            disposed = true;
            group.clear();
        }
    };
}

function createSceneHarness({ rendererDisposeError = null } = {}) {
    const frames = [];
    const pixelRatios = [];
    const renderers = [];
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
            this.disposeCalls = 0;
            renderers.push(this);
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
            const glyphs = scene.getObjectByName("ReferenceGlyphRig");
            const worldPoints = collectMeshPoints(planet);
            this.scene = scene;
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
                cameraPosition: camera.position.clone(),
                target: camera.lookAtTarget.clone(),
                worldScale: planet.scale.x,
                worldQuaternion: planet.quaternion.clone(),
                glyphPosition: glyphs.position.clone(),
                glyphScale: glyphs.scale.clone(),
                glyphQuaternions: glyphs.children.map((pivot) =>
                    pivot.quaternion.toArray()),
                siblingRigs: planet.parent === glyphs.parent
                    && planet.parent === scene,
                projected: worldPoints.map((point) =>
                    point.clone().project(camera)),
                depths: worldPoints.map((point) =>
                    -point.clone().applyMatrix4(camera.matrixWorldInverse).z)
            });
        }
        dispose() {
            this.disposeCalls += 1;
            if (rendererDisposeError) {
                throw rendererDisposeError;
            }
        }
    }
    return {
        frames,
        pixelRatios,
        renderers,
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
    profile = sceneProfile(),
    glyphFactory = createReferenceGlyphRig
}) {
    const { createQuantPlanetScene } = await import(
        "../../main/resources/static/js/home-quant-planet-scene.js"
    );
    const harness = createSceneHarness();
    const canvas = { clientWidth: width, clientHeight: height };
    const controller = await createQuantPlanetScene({
        canvas,
        profile,
        three: harness.three,
        glyphFactory
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

test("forwards the world angle without pointer or drag coupling", async (t) => {
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
        new THREE.Euler(0, .45, 0)
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

test("pointer changes glyphs without changing planet or camera", async (t) => {
    const enabled = await createTestScene(t, {
        width: 1242,
        height: 1270,
        profile: sceneProfile({ pointerEnabled: true })
    });
    enabled.controller.render(pose({
        worldAngle: .45,
        pointer: { x: -1, y: -.5 },
        elapsedMs: 1000
    }));
    enabled.controller.render(pose({
        worldAngle: .45,
        pointer: { x: 1, y: .5 },
        elapsedMs: 1100
    }));

    const [left, right] = enabled.frames.slice(-2);
    assert.deepEqual(
        left.worldQuaternion.toArray(),
        right.worldQuaternion.toArray()
    );
    assert.deepEqual(
        left.cameraPosition.toArray(),
        right.cameraPosition.toArray()
    );
    assert.notDeepEqual(left.glyphQuaternions, right.glyphQuaternions);
    assert.equal(right.glyphQuaternions.length, 9);
    assert.equal(right.siblingRigs, true);

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
    assert.deepEqual(
        disabledLeft.glyphQuaternions,
        disabledRight.glyphQuaternions
    );
});

test("combines renderer-real rig diagnostics within the scene budget", async (t) => {
    const scene = await createTestScene(t, {
        width: 1440,
        height: 900,
        profile: sceneProfile({ pixelRatio: 3 })
    });

    scene.controller.render(pose());
    assert.deepEqual(scene.pixelRatios, [1.5]);
    assert.deepEqual(scene.controller.getDiagnostics(), {
        drawCalls: 72,
        triangles: 9465,
        limits: { drawCalls: 75, triangles: 140000 },
        withinBudget: true
    });
});

test("keeps high-profile shadows inside the combined call budget", async (t) => {
    const scene = await createTestScene(t, {
        width: 1440,
        height: 900,
        profile: sceneProfile({ shadows: true })
    });

    scene.controller.render(pose());
    const diagnostics = scene.controller.getDiagnostics();
    assert.ok(diagnostics.drawCalls <= 75);
    assert.ok(diagnostics.triangles <= 140000);
    assert.equal(diagnostics.withinBudget, true);
});

test("refits glyph layout only when the viewport changes", async (t) => {
    const scene = await createTestScene(t, {
        width: 1440,
        height: 900
    });

    scene.controller.render(pose({ pointer: { x: -1, y: -.5 } }));
    scene.controller.render(pose({ pointer: { x: 1, y: .5 } }));
    const [beforePointer, afterPointer] = scene.frames;
    assert.deepEqual(
        beforePointer.glyphPosition.toArray(),
        afterPointer.glyphPosition.toArray()
    );
    assert.deepEqual(
        beforePointer.glyphScale.toArray(),
        afterPointer.glyphScale.toArray()
    );

    scene.canvas.clientWidth = 375;
    scene.canvas.clientHeight = 812;
    scene.controller.resize();
    scene.controller.render(pose());
    assert.notDeepEqual(
        afterPointer.glyphScale.toArray(),
        scene.frames.at(-1).glyphScale.toArray()
    );
});

test("disposal attempts glyphs, world, and renderer once when cleanup throws", async () => {
    const glyphError = new Error("glyph cleanup failed");
    let glyphDisposeCalls = 0;
    const harness = createSceneHarness({
        rendererDisposeError: new Error("renderer cleanup failed")
    });
    const baseGlyphs = await createReferenceGlyphRig(harness.three);
    const controller = await (await import(
        "../../main/resources/static/js/home-quant-planet-scene.js"
    )).createQuantPlanetScene({
        canvas: { clientWidth: 800, clientHeight: 600 },
        profile: sceneProfile(),
        three: harness.three,
        glyphFactory: async () => ({
            ...baseGlyphs,
            dispose() {
                glyphDisposeCalls += 1;
                throw glyphError;
            }
        })
    });
    controller.resize();
    controller.render(pose());
    const world = harness.renderers[0].scene.getObjectByName("QuantPlanet");
    const worldDisposals = trackDisposableResources(world);

    assert.throws(() => controller.dispose(), glyphError);
    assert.doesNotThrow(() => controller.dispose());
    assert.equal(glyphDisposeCalls, 1);
    assert.equal(harness.renderers[0].disposeCalls, 1);
    worldDisposals.forEach((calls) => assert.equal(calls, 1));
});

test("construction rollback cleans every completed rig despite cleanup errors", async () => {
    const constructionError = new Error("layout failed");
    let glyphDisposeCalls = 0;
    let worldDisposals;
    const harness = createSceneHarness({
        rendererDisposeError: new Error("renderer cleanup failed")
    });
    const glyphs = await createReferenceGlyphRig(harness.three);

    await assert.rejects((await import(
        "../../main/resources/static/js/home-quant-planet-scene.js"
    )).createQuantPlanetScene({
        canvas: { clientWidth: 800, clientHeight: 600 },
        profile: sceneProfile(),
        three: harness.three,
        glyphFactory: async () => ({
            ...glyphs,
            setLayout() {
                const world = glyphs.group.parent.getObjectByName(
                    "QuantPlanet"
                );
                worldDisposals = trackDisposableResources(world);
                throw constructionError;
            },
            dispose() {
                glyphDisposeCalls += 1;
                throw new Error("glyph cleanup failed");
            }
        })
    }), constructionError);

    assert.equal(glyphDisposeCalls, 1);
    assert.equal(harness.renderers[0].disposeCalls, 1);
    worldDisposals.forEach((calls) => assert.equal(calls, 1));
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
