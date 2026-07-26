import assert from "node:assert/strict";
import test from "node:test";
import * as THREE from "../../main/resources/static/js/vendor/three.module.js";
import { REFERENCE_HERO } from
    "../../main/resources/static/js/home-quant-planet-composition.js";
import { createQuantGlyphRig } from
    "../../main/resources/static/js/home-quant-planet-glyphs.js";

const GLYPH_RESPONSE = Object.freeze([
    [.96, .94, -.018, .92, -.018],
    [1.02, .98, .012, 1.04, .012],
    [.94, 1.04, -.01, .96, -.01],
    [1.04, .96, .014, 1.06, .014],
    [1, 1, 0, 1, 0],
    [.97, 1.03, -.014, .94, -.014],
    [.95, 1.02, .01, .98, .01],
    [1.03, .95, -.012, 1.03, -.012],
    [.98, .97, .018, .9, .018]
]);

function createImageData(width, height) {
    return {
        width,
        height,
        data: new Uint8ClampedArray(width * height * 4)
    };
}

function paintRectangle(image, left, top, width, height, alpha = 255) {
    for (let y = top; y < top + height; y += 1) {
        for (let x = left; x < left + width; x += 1) {
            image.data[(y * image.width + x) * 4 + 3] = alpha;
        }
    }
}

function createNineCellMask() {
    const image = createImageData(90, 90);
    for (let row = 0; row < 3; row += 1) {
        for (let column = 0; column < 3; column += 1) {
            paintRectangle(image, column * 30 + 6, row * 30 + 8, 18, 14);
        }
    }
    paintRectangle(image, 11, 11, 6, 6, 0);
    paintRectangle(image, 26, 10, 3, 5);
    return image;
}

function glyphPivots(group) {
    return group.children.filter(
        ({ name }) => name.startsWith("ReferenceGlyphPivot-")
    );
}

function assertNear(actual, expected, tolerance = 1e-12) {
    assert.ok(
        Math.abs(actual - expected) <= tolerance,
        `Expected ${actual} to be within ${tolerance} of ${expected}`
    );
}

function renderedWidth(group) {
    group.updateMatrixWorld(true);
    const bounds = new THREE.Box3().setFromObject(group);
    return bounds.max.x - bounds.min.x;
}

async function createRig(t, options = {}) {
    const rig = await createQuantGlyphRig(THREE, {
        imageDataSource: createNineCellMask(),
        ...options
    });
    t?.after(() => rig.dispose());
    return rig;
}

function collectResources(group) {
    const geometries = new Set();
    const outlineGeometries = new Set();
    const materials = new Set();
    const textures = new Set();

    group.traverse((object) => {
        if (object.isMesh || object.isLineSegments) {
            geometries.add(object.geometry);
            if (object.isLineSegments) {
                outlineGeometries.add(object.geometry);
            }
        }
        const objectMaterials = Array.isArray(object.material)
            ? object.material
            : object.material ? [object.material] : [];
        objectMaterials.forEach((material) => {
            materials.add(material);
            Object.values(material).forEach((value) => {
                if (value?.isTexture) {
                    textures.add(value);
                }
            });
        });
    });
    return { geometries, outlineGeometries, materials, textures };
}

test("builds nine independent extruded glyph pivots", async (t) => {
    const rig = await createRig(t);
    const pivots = glyphPivots(rig.group);

    assert.equal(rig.group.name, "ReferenceGlyphRig");
    assert.equal(pivots.length, 9);
    assert.equal(new Set(pivots).size, 9);
    pivots.forEach((pivot, index) => {
        const mesh = pivot.getObjectByName(`ReferenceGlyphMesh-${index + 1}`);
        const outline = pivot.getObjectByName(
            `ReferenceGlyphOutline-${index + 1}`
        );
        assert.equal(mesh?.geometry?.type, "ExtrudeGeometry");
        assert.ok(mesh.geometry.parameters.options.depth > 0);
        assert.equal(Array.isArray(mesh.material), true);
        assert.equal(mesh.material.length, 2);
        assert.equal(outline?.isLineSegments, true);
        assert.equal(outline.geometry.type, "EdgesGeometry");
    });
});

test("keeps a hole and disconnected component in one glyph extrusion",
        async (t) => {
    const rig = await createRig(t);
    const pivot = glyphPivots(rig.group)[0];
    const meshes = pivot.children.filter(({ isMesh }) => isMesh);
    const outlines = pivot.children.filter(
        ({ isLineSegments }) => isLineSegments
    );
    const shapes = meshes[0].geometry.parameters.shapes;

    assert.equal(meshes.length, 1);
    assert.equal(outlines.length, 1);
    assert.equal(shapes.length, 2);
    assert.equal(
        shapes.filter((shape) => shape.holes.length === 1).length,
        1
    );
    assert.equal(meshes[0].geometry.groups.length, 4);
});

test("uses the authored extrusion and face side and ink materials",
        async (t) => {
    const rig = await createRig(t);
    const pivot = glyphPivots(rig.group)[0];
    const mesh = pivot.getObjectByName("ReferenceGlyphMesh-1");
    const outline = pivot.getObjectByName("ReferenceGlyphOutline-1");
    const [face, side] = mesh.material;

    assert.equal(mesh.geometry.parameters.options.depth, .24);
    assert.equal(mesh.geometry.parameters.options.bevelEnabled, true);
    assert.equal(mesh.geometry.parameters.options.bevelSegments, 1);
    assert.equal(mesh.geometry.parameters.options.steps, 1);
    assert.equal(mesh.geometry.parameters.options.bevelSize, .025);
    assert.equal(mesh.geometry.parameters.options.bevelThickness, .025);
    assert.equal(mesh.geometry.parameters.options.curveSegments, 1);
    assert.equal(face.isMeshStandardMaterial, true);
    assert.equal(face.color.getHex(), 0xf2efe4);
    assert.equal(face.roughness, .76);
    assert.equal(face.metalness, .02);
    assert.equal(side.isMeshStandardMaterial, true);
    assert.equal(side.color.getHex(), 0x778279);
    assert.equal(side.roughness, .84);
    assert.equal(side.metalness, .01);
    assert.equal(outline.material.isLineBasicMaterial, true);
    assert.equal(outline.material.color.getHex(), 0x28312d);
    assert.equal(outline.geometry.parameters.thresholdAngle, 28);
});

test("pointer moves glyphs within limits and leaves the parent layout fixed",
        async (t) => {
    const rig = await createRig(t);
    rig.setLayout({
        center: { x: 0, y: 0, z: 0 },
        radius: 5,
        aspect: 1
    });
    rig.group.updateMatrix();
    const parentBefore = rig.group.matrix.clone();
    rig.update({
        pointer: { x: 1, y: -1 },
        elapsedMs: 1000,
        titleProgress: 1,
        reduceMotion: false
    });
    rig.group.updateMatrix();

    assert.deepEqual(rig.group.matrix.elements, parentBefore.elements);
    const pivots = glyphPivots(rig.group);
    assert.ok(pivots.every((pivot) =>
        Math.abs(pivot.rotation.y) <= REFERENCE_HERO.glyphYawRadians
            + REFERENCE_HERO.glyphVariationRadians + 1e-6));
    assert.ok(pivots.some((pivot) => Math.abs(pivot.rotation.y) > .01));
});

test("applies every authored response rest angle and depth offset",
        async (t) => {
    const rig = await createRig(t);
    const pointer = { x: .5, y: -.25 };
    glyphPivots(rig.group).forEach((pivot) => {
        pivot.position.z = .4;
    });
    rig.update({
        pointer,
        elapsedMs: 50,
        titleProgress: 1,
        reduceMotion: false
    });

    glyphPivots(rig.group).forEach((pivot, index) => {
        const [
            yawGain,
            pitchGain,
            restYaw,
            responseGain,
            depthOffset
        ] = GLYPH_RESPONSE[index];
        const alpha = 1 - Math.exp(
            -REFERENCE_HERO.glyphResponsePerSecond * responseGain * .05
        );
        const expectedYaw = restYaw
            + pointer.x * REFERENCE_HERO.glyphYawRadians * yawGain * alpha;
        const expectedPitch = -pointer.y
            * REFERENCE_HERO.glyphPitchRadians * pitchGain * alpha;
        const expectedDepth = .4 + (depthOffset - .4) * alpha;

        assert.equal(pivot.userData.restYaw, restYaw);
        assert.equal(pivot.userData.restPitch, 0);
        assertNear(pivot.rotation.y, expectedYaw);
        assertNear(pivot.rotation.x, expectedPitch);
        assertNear(pivot.position.z, expectedDepth);
    });
});

test("uses frame-rate-independent damping for glyph motion", async (t) => {
    const oneStep = await createRig(t);
    const twoSteps = await createRig(t);
    const pose = {
        pointer: { x: .8, y: -.6 },
        titleProgress: 1,
        reduceMotion: false
    };

    oneStep.update({ ...pose, elapsedMs: 0 });
    oneStep.update({ ...pose, elapsedMs: 50 });
    twoSteps.update({ ...pose, elapsedMs: 0 });
    twoSteps.update({ ...pose, elapsedMs: 25 });
    twoSteps.update({ ...pose, elapsedMs: 50 });

    glyphPivots(oneStep.group).forEach((pivot, index) => {
        const other = glyphPivots(twoSteps.group)[index];
        assert.ok(Math.abs(pivot.rotation.x - other.rotation.x) < 1e-12);
        assert.ok(Math.abs(pivot.rotation.y - other.rotation.y) < 1e-12);
        assert.ok(Math.abs(pivot.position.z - other.position.z) < 1e-12);
    });
});

test("caps a large elapsed-time jump to one 50ms response step", async (t) => {
    const capped = await createRig(t);
    const longFrame = await createRig(t);
    const pose = {
        pointer: { x: -.7, y: .3 },
        titleProgress: 1,
        reduceMotion: false
    };

    capped.update({ ...pose, elapsedMs: 50 });
    longFrame.update({ ...pose, elapsedMs: 5000 });

    glyphPivots(capped.group).forEach((pivot, index) => {
        const other = glyphPivots(longFrame.group)[index];
        assertNear(pivot.rotation.x, other.rotation.x);
        assertNear(pivot.rotation.y, other.rotation.y);
        assertNear(pivot.position.z, other.position.z);
    });
});

test("reduced motion restores all glyphs to authored rest poses", async (t) => {
    const rig = await createRig(t);
    rig.update({
        pointer: { x: -1, y: -1 },
        elapsedMs: 50,
        titleProgress: 1,
        reduceMotion: false
    });
    rig.update({
        pointer: { x: 1, y: 1 },
        elapsedMs: 100,
        titleProgress: .2,
        reduceMotion: true
    });
    glyphPivots(rig.group).forEach((pivot, index) => {
        assert.equal(pivot.rotation.x, pivot.userData.restPitch);
        assert.equal(pivot.rotation.y, pivot.userData.restYaw);
        assert.equal(pivot.position.z, GLYPH_RESPONSE[index][4]);
    });
    assert.equal(rig.group.visible, true);
});

test("lays out and reveals the title in front of the world", async (t) => {
    const rig = await createRig(t);
    rig.setLayout({
        center: { x: 2, y: 3, z: 4 },
        radius: 5,
        aspect: 1
    });
    rig.update({
        pointer: { x: 0, y: 0 },
        elapsedMs: 0,
        titleProgress: 0,
        reduceMotion: false
    });

    assert.equal(rig.group.visible, false);
    assert.equal(rig.group.position.x, 2);
    assert.equal(rig.group.position.y, 3);
    assert.ok(Math.abs(rig.group.position.z - 8.35) < 1e-12);
    const hiddenScale = rig.group.scale.x;

    rig.update({
        pointer: { x: 0, y: 0 },
        elapsedMs: 16,
        titleProgress: 1,
        reduceMotion: false
    });
    assert.equal(rig.group.visible, true);
    assert.ok(Math.abs(rig.group.position.z - 9.25) < 1e-12);
    assert.ok(rig.group.scale.x > hiddenScale);
    assert.ok(Math.abs(rig.group.scale.x / hiddenScale - 1 / .82) < 1e-12);
});

test("sizes the visible title to 62% of diameter and narrows portraits",
        async (t) => {
    const rig = await createRig(t);
    const radius = 5;
    const expectedLandscapeWidth = radius * 2 * .62;

    rig.setLayout({
        center: { x: 0, y: 0, z: 0 },
        radius,
        aspect: 1
    });
    const landscapeWidth = renderedWidth(rig.group);
    assertNear(landscapeWidth, expectedLandscapeWidth, 1e-9);

    rig.setLayout({
        center: { x: 0, y: 0, z: 0 },
        radius,
        aspect: .5
    });
    const portraitWidth = renderedWidth(rig.group);
    assert.ok(portraitWidth < landscapeWidth);
    assert.ok(portraitWidth > landscapeWidth * .9);
});

test("reports actual multi-material glyph draw calls and triangle counts",
        async (t) => {
    const rig = await createRig(t);
    const expectedTriangles = glyphPivots(rig.group).reduce((sum, pivot) => {
        const geometry = pivot.getObjectByName(
            `ReferenceGlyphMesh-${pivot.userData.glyphIndex + 1}`
        ).geometry;
        const primitives = geometry.index
            ? geometry.index.count
            : geometry.attributes.position.count;
        return sum + primitives / 3;
    }, 0);

    assert.deepEqual(rig.getRenderStats(), {
        drawCalls: 29,
        shadowCalls: 0,
        triangles: expectedTriangles
    });
});

test("reports zero render work when the rig or an ancestor is hidden",
        async (t) => {
    const rig = await createRig(t);
    const parent = new THREE.Group();
    parent.add(rig.group);
    const visibleStats = rig.getRenderStats();
    assert.ok(visibleStats.drawCalls > 0);
    assert.ok(visibleStats.triangles > 0);

    rig.update({
        pointer: { x: 0, y: 0 },
        elapsedMs: 0,
        titleProgress: 0,
        reduceMotion: false
    });
    assert.deepEqual(rig.getRenderStats(), {
        drawCalls: 0,
        shadowCalls: 0,
        triangles: 0
    });

    rig.update({
        pointer: { x: 0, y: 0 },
        elapsedMs: 16,
        titleProgress: 1,
        reduceMotion: false
    });
    parent.visible = false;
    assert.deepEqual(rig.getRenderStats(), {
        drawCalls: 0,
        shadowCalls: 0,
        triangles: 0
    });
});

test("disposes every glyph resource exactly once", async () => {
    const rig = await createRig();
    const resources = collectResources(rig.group);
    const tracked = new Set([
        ...resources.geometries,
        ...resources.materials,
        ...resources.textures
    ]);
    const counts = new Map();
    tracked.forEach((resource) => {
        const dispose = resource.dispose.bind(resource);
        counts.set(resource, 0);
        resource.dispose = () => {
            counts.set(resource, counts.get(resource) + 1);
            dispose();
        };
    });

    rig.dispose();
    rig.dispose();

    assert.equal(resources.geometries.size, 18);
    assert.equal(resources.outlineGeometries.size, 9);
    assert.equal(resources.materials.size, 3);
    assert.equal(resources.textures.size, 0);
    assert.ok([...counts.values()].every((count) => count === 1));
});

test("rolls back every allocated resource after partial construction failure",
        async () => {
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
    const TrackedExtrudeGeometry = trackDisposable(THREE.ExtrudeGeometry);
    let extrudeCount = 0;
    class FailingExtrudeGeometry extends TrackedExtrudeGeometry {
        constructor(...args) {
            extrudeCount += 1;
            if (extrudeCount === 5) {
                throw new Error("injected glyph construction failure");
            }
            super(...args);
        }
    }
    const FaultThree = {
        ...THREE,
        ExtrudeGeometry: FailingExtrudeGeometry,
        EdgesGeometry: trackDisposable(THREE.EdgesGeometry),
        MeshStandardMaterial: trackDisposable(THREE.MeshStandardMaterial),
        LineBasicMaterial: trackDisposable(THREE.LineBasicMaterial)
    };

    await assert.rejects(
        createQuantGlyphRig(FaultThree, {
            imageDataSource: createNineCellMask()
        }),
        /injected glyph construction failure/
    );
    assert.ok(allocations.length > 0);
    assert.ok(allocations.every((resource) => resource.disposeCount === 1));
});
