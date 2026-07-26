import assert from "node:assert/strict";
import test from "node:test";
import * as THREE from "../../main/resources/static/js/vendor/three.module.js";
import { REFERENCE_HERO } from
    "../../main/resources/static/js/home-quant-planet-composition.js";
import { createQuantGlyphRig } from
    "../../main/resources/static/js/home-quant-planet-glyphs.js";

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

function createFragmentedNineCellMask() {
    const image = createImageData(90, 90);
    for (let row = 0; row < 3; row += 1) {
        for (let column = 0; column < 3; column += 1) {
            const left = column * 30;
            const top = row * 30;
            paintRectangle(image, left + 4, top + 6, 7, 14);
            paintRectangle(image, left + 13, top + 6, 5, 14);
            paintRectangle(image, left + 20, top + 6, 6, 14);
        }
    }
    return image;
}

function createPaddedNineCellMask() {
    const image = createImageData(120, 140);
    for (let row = 0; row < 3; row += 1) {
        for (let column = 0; column < 3; column += 1) {
            paintRectangle(
                image,
                32 + column * 24,
                22 + row * 39,
                14,
                18
            );
        }
    }
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

function restQuaternion(pivot) {
    return new THREE.Quaternion().setFromEuler(new THREE.Euler(
        pivot.userData.restPitch,
        pivot.userData.restYaw,
        pivot.userData.restRoll,
        pivot.rotation.order
    ));
}

function restAngle(pivot) {
    return pivot.quaternion.angleTo(restQuaternion(pivot));
}

function assertRestPose(pivot, tolerance = 1e-12) {
    const anchor = pivot.userData.layoutAnchor;
    assertNear(pivot.rotation.x, pivot.userData.restPitch, tolerance);
    assertNear(pivot.rotation.y, pivot.userData.restYaw, tolerance);
    assertNear(pivot.rotation.z, pivot.userData.restRoll, tolerance);
    assertNear(pivot.position.x, anchor.x, tolerance);
    assertNear(pivot.position.y, anchor.y, tolerance);
    assertNear(pivot.position.z, anchor.z, tolerance);
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

function advanceRig(rig, pointer, elapsedMs = 1000) {
    rig.update({
        pointer,
        elapsedMs: 0,
        titleProgress: 1,
        reduceMotion: false
    });
    for (let elapsed = 50; elapsed <= elapsedMs; elapsed += 50) {
        rig.update({
            pointer,
            elapsedMs: elapsed,
            titleProgress: 1,
            reduceMotion: false
        });
    }
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
        const overlay = pivot.getObjectByName(
            `ReferenceGlyphFaceOverlay-${index + 1}`
        );
        assert.equal(mesh?.geometry?.type, "ExtrudeGeometry");
        assert.ok(mesh.geometry.parameters.options.depth > 0);
        assert.equal(mesh?.material?.isMeshStandardMaterial, true);
        assert.equal(outline?.isLineSegments, true);
        assert.equal(outline.geometry.type, "EdgesGeometry");
        assert.equal(overlay?.geometry?.type, "ShapeGeometry");
        assert.equal(overlay?.material?.isMeshBasicMaterial, true);
    });
});

test("keeps a hole and disconnected component in one glyph extrusion",
        async (t) => {
    const rig = await createRig(t);
    const pivot = glyphPivots(rig.group)[0];
    const meshes = pivot.children.filter(({ isMesh }) => isMesh);
    const extrusion = pivot.getObjectByName("ReferenceGlyphMesh-1");
    const outlines = pivot.children.filter(
        ({ isLineSegments }) => isLineSegments
    );
    const shapes = extrusion.geometry.parameters.shapes;

    assert.equal(meshes.length, 2);
    assert.equal(outlines.length, 1);
    assert.equal(shapes.length, 2);
    assert.equal(
        shapes.filter((shape) => shape.holes.length === 1).length,
        1
    );
    assert.equal(extrusion.geometry.groups.length, 0);
});

test("maps a padded alpha-content box onto the complete glyph grid",
        async (t) => {
    const rig = await createRig(t, {
        imageDataSource: createPaddedNineCellMask()
    });

    glyphPivots(rig.group).forEach((pivot, index) => {
        const mesh = pivot.getObjectByName(
            `ReferenceGlyphMesh-${index + 1}`
        );
        assert.equal(mesh.geometry.parameters.shapes.length, 1);
    });
});

test("uses one vertex-colored face-side material and the ink outline",
        async (t) => {
    const rig = await createRig(t);
    const pivot = glyphPivots(rig.group)[0];
    const mesh = pivot.getObjectByName("ReferenceGlyphMesh-1");
    const outline = pivot.getObjectByName("ReferenceGlyphOutline-1");
    const faceSide = mesh.material;
    const colors = mesh.geometry.getAttribute("color");

    assert.equal(mesh.geometry.parameters.options.depth, .24);
    assert.equal(mesh.geometry.parameters.options.bevelEnabled, true);
    assert.equal(mesh.geometry.parameters.options.bevelSegments, 1);
    assert.equal(mesh.geometry.parameters.options.steps, 1);
    assert.equal(mesh.geometry.parameters.options.bevelSize, .025);
    assert.equal(mesh.geometry.parameters.options.bevelThickness, .025);
    assert.equal(mesh.geometry.parameters.options.curveSegments, 1);
    assert.equal(faceSide.isMeshStandardMaterial, true);
    assert.equal(faceSide.color.getHex(), 0xffffff);
    assert.equal(faceSide.vertexColors, true);
    assert.equal(faceSide.roughness, .78);
    assert.equal(faceSide.metalness, .02);
    assert.equal(colors.itemSize, 3);
    assert.equal(colors.count, mesh.geometry.attributes.position.count);
    assert.ok(new Set(Array.from(colors.array, (value) =>
        value.toFixed(5))).size > 2);
    assert.equal(outline.material.isLineBasicMaterial, true);
    assert.equal(outline.material.color.getHex(), 0x28312d);
    assert.equal(outline.geometry.parameters.thresholdAngle, 28);
});

test("maps nine front overlays onto one shared local title atlas",
        async (t) => {
    const rig = await createRig(t, {
        imageDataSource: createPaddedNineCellMask()
    });
    const overlays = glyphPivots(rig.group).map((pivot, index) =>
        pivot.getObjectByName(`ReferenceGlyphFaceOverlay-${index + 1}`));
    assert.ok(overlays.every(Boolean), "Missing glyph face overlay");
    const maps = overlays.map(({ material }) => material.map);
    const firstUvs = overlays[0].geometry.getAttribute("uv");
    const lastUvs = overlays[8].geometry.getAttribute("uv");

    assert.equal(new Set(maps).size, 1);
    assert.equal(maps[0].isDataTexture, true);
    assert.deepEqual(
        [maps[0].image.width, maps[0].image.height],
        [120, 140]
    );
    assert.ok(overlays.every(({ material }) =>
        material.toneMapped === false));
    assert.ok(Math.max(...firstUvs.array) < Math.max(...lastUvs.array));
    glyphPivots(rig.group).forEach((pivot, index) => {
        const extrusion = pivot.getObjectByName(
            `ReferenceGlyphMesh-${index + 1}`
        );
        const overlay = overlays[index];
        const extrusionCenter = new THREE.Box3()
            .setFromBufferAttribute(
                extrusion.geometry.getAttribute("position")
            )
            .getCenter(new THREE.Vector3());
        const overlayCenter = new THREE.Box3()
            .setFromBufferAttribute(
                overlay.geometry.getAttribute("position")
            )
            .getCenter(new THREE.Vector3());
        assert.ok(extrusionCenter.distanceTo(overlayCenter) < 1e-12);
    });
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
        Math.abs(pivot.rotation.y) <= pivot.userData.rotationLimitYaw
            + 1e-6));
    assert.ok(pivots.some((pivot) => Math.abs(pivot.rotation.y) > .01));
});

test("articulates nine distinct glyph poses across direct and cross axes",
        async (t) => {
    const pointer = { x: .9, y: -.7 };
    const rig = await createRig(t);
    const pivots = glyphPivots(rig.group);
    const anchors = pivots.map(({ position }) => position.clone());

    advanceRig(rig, pointer);
    const signatures = pivots.map((pivot, index) => [
        pivot.rotation.x,
        pivot.rotation.y,
        pivot.rotation.z,
        pivot.position.x - anchors[index].x,
        pivot.position.y - anchors[index].y,
        pivot.position.z
    ].map((value) => Number(value.toFixed(3))).join(","));

    assert.equal(new Set(signatures).size, 9);
    assert.ok(pivots.filter((pivot) =>
        Math.abs(pivot.rotation.z) > .05).length >= 3);
    assert.ok(pivots.filter((pivot) =>
        Math.abs(pivot.rotation.y) >= 70 * Math.PI / 180).length >= 3);
    assert.ok(pivots.some((pivot, index) =>
        Math.abs(pivot.position.x - anchors[index].x) > .01));
    assert.ok(pivots.some((pivot, index) =>
        Math.abs(pivot.position.y - anchors[index].y) > .01));
    assert.ok(pivots.some((pivot) =>
        Math.abs(
            pivot.position.z - pivot.userData.layoutAnchor.z
        ) > .01));

    const crossUpRig = await createRig(t);
    const crossDownRig = await createRig(t);
    advanceRig(crossUpRig, { x: 0, y: -.7 });
    advanceRig(crossDownRig, { x: 0, y: .7 });
    const crossUpCenter = glyphPivots(crossUpRig.group)[4];
    const crossDownCenter = glyphPivots(crossDownRig.group)[4];
    assert.ok(
        Math.abs(crossUpCenter.rotation.y - crossDownCenter.rotation.y) > .05
    );

    const directLeftRig = await createRig(t);
    const directRightRig = await createRig(t);
    advanceRig(directLeftRig, { x: -.9, y: 0 });
    advanceRig(directRightRig, { x: .9, y: 0 });
    const directLeftCenter = glyphPivots(directLeftRig.group)[4];
    const directRightCenter = glyphPivots(directRightRig.group)[4];
    assert.ok(
        Math.abs(
            directLeftCenter.rotation.x - directRightCenter.rotation.x
        ) > .05
    );
});

test("weights glyph articulation by each anchor's local pointer distance",
        async (t) => {
    const topRig = await createRig(t);
    const bottomRig = await createRig(t);
    advanceRig(topRig, { x: 0, y: -.9 });
    advanceRig(bottomRig, { x: 0, y: .9 });

    const response = (pivot) => Math.abs(
        pivot.rotation.x - pivot.userData.restPitch
    ) + Math.abs(
        pivot.rotation.y - pivot.userData.restYaw
    ) + Math.abs(
        pivot.rotation.z - pivot.userData.restRoll
    );
    const rowAverage = (pivots, start) => pivots
        .slice(start, start + 3)
        .reduce((total, pivot) => total + response(pivot), 0) / 3;
    const topPivots = glyphPivots(topRig.group);
    const bottomPivots = glyphPivots(bottomRig.group);

    assert.ok(rowAverage(topPivots, 0) >= rowAverage(topPivots, 6) * 2);
    assert.ok(rowAverage(bottomPivots, 6)
        >= rowAverage(bottomPivots, 0) * 2);
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
        assert.ok(Math.abs(pivot.rotation.z - other.rotation.z) < 1e-12);
        assert.ok(Math.abs(pivot.position.x - other.position.x) < 1e-12);
        assert.ok(Math.abs(pivot.position.y - other.position.y) < 1e-12);
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
        assertNear(pivot.rotation.z, other.rotation.z);
        assertNear(pivot.position.x, other.position.x);
        assertNear(pivot.position.y, other.position.y);
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
    glyphPivots(rig.group).forEach((pivot) => assertRestPose(pivot));
    rig.update({
        pointer: { x: 0, y: 0 },
        elapsedMs: 150,
        titleProgress: 1,
        reduceMotion: false
    });
    glyphPivots(rig.group).forEach((pivot) => assertRestPose(pivot));
    assert.equal(rig.group.visible, true);
});

test("center return settles all six pose components to authored rest",
        async (t) => {
    const rig = await createRig(t);
    advanceRig(rig, { x: .9, y: -.7 }, 500);
    for (let elapsedMs = 550; elapsedMs <= 2500; elapsedMs += 50) {
        rig.update({
            pointer: { x: 0, y: 0 },
            elapsedMs,
            titleProgress: 1,
            reduceMotion: false
        });
    }

    glyphPivots(rig.group).forEach((pivot) =>
        assertRestPose(pivot, 5e-5));
});

test("fast top-row sweep flips one nearby glyph without flipping the grid",
        async (t) => {
    const rig = await createRig(t);
    const sweep = [
        { elapsedMs: 0, pointer: { x: -1, y: -1 } },
        { elapsedMs: 20, pointer: { x: 0, y: -1 } },
        { elapsedMs: 40, pointer: { x: 1, y: -1 } },
        { elapsedMs: 80, pointer: { x: 0, y: 0 } }
    ];
    const pivots = glyphPivots(rig.group);
    const peakAngles = Array(9).fill(0);
    const recordAngles = () => pivots.forEach((pivot, index) => {
        peakAngles[index] = Math.max(peakAngles[index], restAngle(pivot));
    });

    sweep.forEach((sample) => {
        rig.update({
            ...sample,
            titleProgress: 1,
            reduceMotion: false
        });
        recordAngles();
    });
    for (let elapsedMs = 120; elapsedMs <= 480; elapsedMs += 40) {
        rig.update({
            pointer: { x: 0, y: 0 },
            elapsedMs,
            titleProgress: 1,
            reduceMotion: false
        });
        recordAngles();
    }

    const ninetyDegrees = 90 * Math.PI / 180;
    const twentyFiveDegrees = 25 * Math.PI / 180;
    assert.ok(
        peakAngles.slice(0, 3).some((angle) => angle >= ninetyDegrees),
        `Expected top-row peak >= 90°, got ${peakAngles.map((angle) =>
            (angle * 180 / Math.PI).toFixed(1)).join(", ")}`
    );
    assert.ok(
        peakAngles.filter((angle) => angle <= twentyFiveDegrees).length >= 6
    );

    for (let elapsedMs = 520; elapsedMs <= 1480; elapsedMs += 40) {
        rig.update({
            pointer: { x: 0, y: 0 },
            elapsedMs,
            titleProgress: 1,
            reduceMotion: false
        });
    }
    const twelveDegrees = 12 * Math.PI / 180;
    pivots.forEach((pivot) => assert.ok(restAngle(pivot) <= twelveDegrees));
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
    assert.ok(Math.abs(rig.group.position.z - 8) < 1e-12);
    const hiddenScale = rig.group.scale.x;

    rig.update({
        pointer: { x: 0, y: 0 },
        elapsedMs: 16,
        titleProgress: 1,
        reduceMotion: false
    });
    assert.equal(rig.group.visible, true);
    assert.ok(Math.abs(rig.group.position.z - 8.9) < 1e-12);
    assert.ok(rig.group.scale.x > hiddenScale);
    assert.ok(Math.abs(rig.group.scale.x / hiddenScale - 1 / .82) < 1e-12);
});

test("keeps every settled glyph vertex behind the scene near plane",
        async (t) => {
    const rig = await createRig(t);
    const center = { x: 0, y: 0, z: 4 };
    const radius = 3.5;
    const depthRadius = 5;
    rig.setLayout({ center, radius, depthRadius, aspect: 1 });
    rig.update({
        pointer: { x: 0, y: 0 },
        elapsedMs: 0,
        titleProgress: 1,
        reduceMotion: false
    });
    rig.group.updateMatrixWorld(true);
    const bounds = new THREE.Box3().setFromObject(rig.group);
    const nearPlaneWorldZ = center.z + depthRadius * 1.08;

    assert.ok(bounds.max.z > center.z + depthRadius);
    assert.ok(
        bounds.max.z < nearPlaneWorldZ,
        `Expected nearest glyph z ${bounds.max.z} behind ${nearPlaneWorldZ}`
    );
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

test("reports actual vertex-colored glyph draw calls and triangle counts",
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
        drawCalls: 27,
        shadowCalls: 0,
        triangles: expectedTriangles
            + glyphPivots(rig.group).reduce((sum, pivot, index) => {
                const overlay = pivot.getObjectByName(
                    `ReferenceGlyphFaceOverlay-${index + 1}`
                );
                const primitives = overlay.geometry.index
                    ? overlay.geometry.index.count
                    : overlay.geometry.attributes.position.count;
                return sum + primitives / 3;
            }, 0)
    });
});

test("bounds glyph draw calls independently of disconnected contours",
        async (t) => {
    const rig = await createRig(t, {
        imageDataSource: createFragmentedNineCellMask()
    });

    assert.equal(rig.getRenderStats().drawCalls, 27);
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

    assert.equal(resources.geometries.size, 27);
    assert.equal(resources.outlineGeometries.size, 9);
    assert.equal(resources.materials.size, 3);
    assert.equal(resources.textures.size, 1);
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
