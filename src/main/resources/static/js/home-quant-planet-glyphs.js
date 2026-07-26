import {
    extractGlyphContours,
    loadLocalImageData
} from "./home-quant-planet-glyph-contours.js";
import { REFERENCE_HERO } from "./home-quant-planet-composition.js";

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

const COLUMN_SPACING = 1.12;
const ROW_SPACING = 1.02;
const TITLE_DIAMETER_RATIO = .62;
const TITLE_DEPTH_RATIO = .98;
const FACE_COLOR = 0xf2efe4;
const SIDE_COLOR = 0x778279;
const ALPHA_THRESHOLD = 32;
const ZERO_POINTER = Object.freeze({ x: 0, y: 0 });

function createShape(THREE, record) {
    const shape = new THREE.Shape();
    record.outer.forEach(({ x, y }, index) => {
        if (index === 0) shape.moveTo(x, y);
        else shape.lineTo(x, y);
    });
    record.holes.forEach((hole) => {
        const path = new THREE.Path();
        hole.forEach(({ x, y }, index) => {
            if (index === 0) path.moveTo(x, y);
            else path.lineTo(x, y);
        });
        shape.holes.push(path);
    });
    return shape;
}

function finiteOr(value, fallback) {
    return Number.isFinite(value) ? value : fallback;
}

function clamp(value, minimum, maximum) {
    return Math.min(maximum, Math.max(minimum, value));
}

function triangleCount(geometry) {
    const primitives = geometry.index
        ? geometry.index.count
        : geometry.attributes.position.count;
    return primitives / 3;
}

function resolveAlphaContent(imageData) {
    const width = Number(imageData?.width) || 0;
    const height = Number(imageData?.height) || 0;
    const data = imageData?.data;
    let left = width;
    let top = height;
    let right = -1;
    let bottom = -1;

    for (let y = 0; y < height; y += 1) {
        for (let x = 0; x < width; x += 1) {
            if (data[(y * width + x) * 4 + 3] < ALPHA_THRESHOLD) {
                continue;
            }
            left = Math.min(left, x);
            top = Math.min(top, y);
            right = Math.max(right, x);
            bottom = Math.max(bottom, y);
        }
    }
    if (right < left || bottom < top) {
        return {
            imageData,
            bounds: { left: 0, top: 0, width, height }
        };
    }
    if (left === 0 && top === 0
            && right === width - 1 && bottom === height - 1) {
        return {
            imageData,
            bounds: { left, top, width, height }
        };
    }

    const trimmedWidth = right - left + 1;
    const trimmedHeight = bottom - top + 1;
    const trimmed = new Uint8ClampedArray(
        trimmedWidth * trimmedHeight * 4
    );
    for (let y = 0; y < trimmedHeight; y += 1) {
        const sourceStart = ((top + y) * width + left) * 4;
        const sourceEnd = sourceStart + trimmedWidth * 4;
        trimmed.set(data.subarray(sourceStart, sourceEnd),
            y * trimmedWidth * 4);
    }
    return {
        imageData: {
            width: trimmedWidth,
            height: trimmedHeight,
            data: trimmed
        },
        bounds: {
            left,
            top,
            width: trimmedWidth,
            height: trimmedHeight
        }
    };
}

function applyAtlasUvs(THREE, geometry, index, source, contentBounds) {
    const column = index % 3;
    const row = Math.floor(index / 3);
    const trimmedWidth = contentBounds.width;
    const trimmedHeight = contentBounds.height;
    const cellLeft = Math.floor(column / 3 * trimmedWidth);
    const cellRight = Math.floor((column + 1) / 3 * trimmedWidth);
    const cellTop = Math.floor(row / 3 * trimmedHeight);
    const cellBottom = Math.floor((row + 1) / 3 * trimmedHeight);
    const positions = geometry.getAttribute("position");
    const uvs = new Float32Array(positions.count * 2);
    for (let vertex = 0; vertex < positions.count; vertex += 1) {
        const x = positions.getX(vertex) + .5;
        const y = .5 - positions.getY(vertex);
        uvs[vertex * 2] = (
            contentBounds.left
            + cellLeft
            + x * (cellRight - cellLeft)
        ) / source.width;
        uvs[vertex * 2 + 1] = (
            contentBounds.top
            + cellTop
            + y * (cellBottom - cellTop)
        ) / source.height;
    }
    geometry.setAttribute("uv", new THREE.BufferAttribute(uvs, 2));
}

function applyFaceSideColors(THREE, geometry) {
    const positions = geometry.getAttribute("position");
    const indices = geometry.getIndex();
    const colors = new Float32Array(positions.count * 3);
    const face = new THREE.Color(FACE_COLOR);
    const side = new THREE.Color(SIDE_COLOR);

    geometry.groups.forEach((group) => {
        const color = group.materialIndex === 0 ? face : side;
        const end = Math.min(group.start + group.count,
            indices ? indices.count : positions.count);
        for (let offset = group.start; offset < end; offset += 1) {
            const vertex = indices ? indices.getX(offset) : offset;
            color.toArray(colors, vertex * 3);
        }
    });
    geometry.setAttribute(
        "color",
        new THREE.BufferAttribute(colors, 3)
    );
    geometry.clearGroups();
}

export async function createQuantGlyphRig(THREE, {
    assetUrl = REFERENCE_HERO.titleAssetUrl,
    documentTarget = globalThis.document,
    imageDataSource
} = {}) {
    const group = new THREE.Group();
    group.name = "ReferenceGlyphRig";

    const geometries = new Set();
    const materials = new Set();
    const textures = new Set();
    const pivots = [];
    let disposed = false;
    let previousElapsedMs = 0;
    let titleProgress = 1;
    let layoutScale = 1;
    let centerX = 0;
    let centerY = 0;
    let finalZ = 0;
    let revealDistance = 0;

    function ownGeometry(geometry) {
        geometries.add(geometry);
        return geometry;
    }

    function ownMaterial(material) {
        materials.add(material);
        Object.values(material).forEach((value) => {
            if (value?.isTexture) {
                textures.add(value);
            }
        });
        return material;
    }

    function disposeTrackedResources() {
        if (disposed) {
            return;
        }
        disposed = true;
        let firstError = null;
        [geometries, materials, textures].forEach((resources) => {
            resources.forEach((resource) => {
                try {
                    resource.dispose();
                } catch (error) {
                    firstError ||= error;
                }
            });
        });
        group.clear();
        if (firstError) {
            throw firstError;
        }
    }

    function applyReveal() {
        const progress = clamp(titleProgress, 0, 1);
        const revealScale = .82 + progress * .18;
        group.visible = progress > 0;
        group.position.set(
            centerX,
            centerY,
            finalZ - revealDistance * (1 - progress)
        );
        group.scale.setScalar(layoutScale * revealScale);
    }

    try {
        const source = imageDataSource === undefined
            ? loadLocalImageData(assetUrl, documentTarget)
            : typeof imageDataSource === "function"
                ? imageDataSource()
                : imageDataSource;
        const imageData = await source;
        const alphaContent = resolveAlphaContent(imageData);
        const glyphContours = extractGlyphContours(
            alphaContent.imageData,
            { alphaThreshold: ALPHA_THRESHOLD }
        );
        const atlasTexture = new THREE.DataTexture(
            imageData.data,
            imageData.width,
            imageData.height,
            THREE.RGBAFormat,
            THREE.UnsignedByteType
        );
        atlasTexture.name = "ReferenceTitleAtlas";
        atlasTexture.colorSpace = THREE.SRGBColorSpace;
        atlasTexture.minFilter = THREE.LinearFilter;
        atlasTexture.magFilter = THREE.LinearFilter;
        atlasTexture.generateMipmaps = false;
        atlasTexture.needsUpdate = true;
        const faceSideMaterial = ownMaterial(new THREE.MeshStandardMaterial({
            color: 0xffffff,
            vertexColors: true,
            roughness: .78,
            metalness: .02
        }));
        const inkLineMaterial = ownMaterial(new THREE.LineBasicMaterial({
            color: 0x28312d
        }));
        const overlayMaterial = ownMaterial(new THREE.MeshBasicMaterial({
            map: atlasTexture,
            transparent: true,
            alphaTest: .04,
            depthWrite: false,
            toneMapped: false
        }));

        glyphContours.forEach((records, index) => {
            const shapes = records.map((record) => createShape(THREE, record));
            const geometry = ownGeometry(new THREE.ExtrudeGeometry(shapes, {
                depth: .24,
                bevelEnabled: true,
                bevelSegments: 1,
                steps: 1,
                bevelSize: .025,
                bevelThickness: .025,
                curveSegments: 1
            }));
            geometry.center();
            applyFaceSideColors(THREE, geometry);

            const pivot = new THREE.Group();
            const response = GLYPH_RESPONSE[index];
            const restYaw = response[2];
            const depthOffset = response[4];
            pivot.name = `ReferenceGlyphPivot-${index + 1}`;
            pivot.userData.glyphIndex = index;
            pivot.userData.restPitch = 0;
            pivot.userData.restYaw = restYaw;
            pivot.position.set(
                (index % 3 - 1) * COLUMN_SPACING,
                (1 - Math.floor(index / 3)) * ROW_SPACING,
                depthOffset
            );
            pivot.rotation.set(0, restYaw, 0);

            const mesh = new THREE.Mesh(
                geometry,
                faceSideMaterial
            );
            mesh.name = `ReferenceGlyphMesh-${index + 1}`;
            mesh.castShadow = false;
            mesh.receiveShadow = false;

            const outlineGeometry = ownGeometry(
                new THREE.EdgesGeometry(geometry, 28)
            );
            const outline = new THREE.LineSegments(
                outlineGeometry,
                inkLineMaterial
            );
            outline.name = `ReferenceGlyphOutline-${index + 1}`;

            const overlayGeometry = ownGeometry(
                new THREE.ShapeGeometry(shapes, 1)
            );
            applyAtlasUvs(
                THREE,
                overlayGeometry,
                index,
                imageData,
                alphaContent.bounds
            );
            overlayGeometry.center();
            geometry.computeBoundingBox();
            const overlay = new THREE.Mesh(
                overlayGeometry,
                overlayMaterial
            );
            overlay.name = `ReferenceGlyphFaceOverlay-${index + 1}`;
            overlay.position.z = geometry.boundingBox.max.z + .004;
            overlay.renderOrder = 1;

            pivot.add(mesh, outline, overlay);
            group.add(pivot);
            pivots.push(pivot);
        });
        const layoutBounds = new THREE.Box3().setFromObject(group);
        const localLayoutWidth = Math.max(
            .001,
            layoutBounds.max.x - layoutBounds.min.x
        );

        function setLayout({
            center = { x: 0, y: 0, z: 0 },
            radius = 1,
            depthRadius = radius,
            aspect = 1
        } = {}) {
            if (disposed) {
                return;
            }
            const safeRadius = Math.max(.001, finiteOr(Number(radius), 1));
            const safeDepthRadius = Math.max(
                .001,
                finiteOr(Number(depthRadius), safeRadius)
            );
            const safeAspect = Math.max(.1, finiteOr(Number(aspect), 1));
            const portraitFactor = safeAspect < 1
                ? .9 + safeAspect * .1
                : 1;
            centerX = finiteOr(Number(center?.x), 0);
            centerY = finiteOr(Number(center?.y), 0);
            finalZ = finiteOr(Number(center?.z), 0)
                + safeDepthRadius * TITLE_DEPTH_RATIO;
            revealDistance = safeRadius * .18;
            layoutScale = safeRadius * 2 * TITLE_DIAMETER_RATIO
                / localLayoutWidth * portraitFactor;
            applyReveal();
        }

        function update({
            pointer = ZERO_POINTER,
            elapsedMs = 0,
            titleProgress: nextTitleProgress = 1,
            reduceMotion = false
        } = {}) {
            if (disposed) {
                return;
            }
            const elapsed = Math.max(0, finiteOr(Number(elapsedMs), 0));
            const deltaSeconds = Math.min(
                .05,
                Math.max(0, elapsed - previousElapsedMs) / 1000
            );
            previousElapsedMs = elapsed;
            const pointerX = reduceMotion
                ? 0
                : clamp(finiteOr(Number(pointer?.x), 0), -1, 1);
            const pointerY = reduceMotion
                ? 0
                : clamp(finiteOr(Number(pointer?.y), 0), -1, 1);
            const targetYaw = pointerX * REFERENCE_HERO.glyphYawRadians;
            const targetPitch = -pointerY
                * REFERENCE_HERO.glyphPitchRadians;

            for (let index = 0; index < pivots.length; index += 1) {
                const pivot = pivots[index];
                const response = GLYPH_RESPONSE[index];
                const yawGain = response[0];
                const pitchGain = response[1];
                const restYaw = response[2];
                const responseGain = response[3];
                const depthOffset = response[4];
                if (reduceMotion) {
                    pivot.rotation.y = restYaw;
                    pivot.rotation.x = pivot.userData.restPitch;
                    pivot.position.z = depthOffset;
                    continue;
                }
                const alpha = 1 - Math.exp(
                    -REFERENCE_HERO.glyphResponsePerSecond
                        * responseGain * deltaSeconds
                );
                pivot.rotation.y += (
                    restYaw + targetYaw * yawGain - pivot.rotation.y
                ) * alpha;
                pivot.rotation.x += (
                    pivot.userData.restPitch + targetPitch * pitchGain
                        - pivot.rotation.x
                ) * alpha;
                pivot.position.z += (
                    depthOffset - pivot.position.z
                ) * alpha;
            }

            titleProgress = reduceMotion
                ? 1
                : clamp(finiteOr(Number(nextTitleProgress), 1), 0, 1);
            applyReveal();
        }

        function getRenderStats() {
            let drawCalls = 0;
            let triangles = 0;
            let ancestor = group;
            while (ancestor) {
                if (ancestor.visible === false) {
                    return {
                        drawCalls: 0,
                        shadowCalls: 0,
                        triangles: 0
                    };
                }
                ancestor = ancestor.parent;
            }
            group.traverseVisible((object) => {
                if (object.isMesh) {
                    drawCalls += Array.isArray(object.material)
                        ? Math.max(1, object.geometry.groups.length)
                        : 1;
                    triangles += triangleCount(object.geometry);
                } else if (object.isLineSegments) {
                    drawCalls += 1;
                }
            });
            return {
                drawCalls,
                shadowCalls: 0,
                triangles
            };
        }

        function dispose() {
            disposeTrackedResources();
        }

        applyReveal();
        return {
            group,
            update,
            setLayout,
            getRenderStats,
            dispose
        };
    } catch (error) {
        try {
            disposeTrackedResources();
        } catch {
            // Preserve the construction failure after best-effort rollback.
        }
        throw error;
    }
}
