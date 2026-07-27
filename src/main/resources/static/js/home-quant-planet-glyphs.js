import {
    extractGlyphContours,
    loadLocalImageData
} from "./home-quant-planet-glyph-contours.js";
import { REFERENCE_HERO } from "./home-quant-planet-composition.js";

const GLYPH_PROFILES = Object.freeze([
    Object.freeze({
        yawFromX: .72, yawFromY: -.46,
        pitchFromX: .64, pitchFromY: -.92,
        rollFromX: .78, rollFromY: -.5,
        responseSpeed: .94,
        restPitch: .035, restYaw: -.018, restRoll: -.045, restDepth: -.018,
        translateXFromX: .14, translateXFromY: -.05,
        translateYFromX: .04, translateYFromY: -.12,
        translateZFromX: .04, translateZFromY: -.07,
        rotationLimitPitch: .72, rotationLimitYaw: 2.55,
        rotationLimitRoll: .72,
        yawPulseRadians: 1.72,
        phaseSpeed: Math.PI / 6,
        phaseOffset: -5 * Math.PI / 6
    }),
    Object.freeze({
        yawFromX: -.34, yawFromY: -1.08,
        pitchFromX: -.72, pitchFromY: -1.08,
        rollFromX: -.64, rollFromY: -.58,
        responseSpeed: 1.08,
        restPitch: -.02, restYaw: .012, restRoll: .05, restDepth: .012,
        translateXFromX: -.11, translateXFromY: -.08,
        translateYFromX: -.06, translateYFromY: -.14,
        translateZFromX: -.03, translateZFromY: -.08,
        rotationLimitPitch: .72, rotationLimitYaw: 2.55,
        rotationLimitRoll: .72,
        yawPulseRadians: 3.5,
        phaseSpeed: Math.PI / 6,
        phaseOffset: -3 * Math.PI / 2
    }),
    Object.freeze({
        yawFromX: .48, yawFromY: .82,
        pitchFromX: .52, pitchFromY: -.96,
        rollFromX: .48, rollFromY: .68,
        responseSpeed: .96,
        restPitch: .03, restYaw: -.01, restRoll: -.04, restDepth: -.01,
        translateXFromX: .12, translateXFromY: .06,
        translateYFromX: .05, translateYFromY: -.11,
        translateZFromX: .05, translateZFromY: -.06,
        rotationLimitPitch: .72, rotationLimitYaw: 2.55,
        rotationLimitRoll: .72,
        yawPulseRadians: 2.1,
        phaseSpeed: Math.PI / 6,
        phaseOffset: -3 * Math.PI / 2
    }),
    Object.freeze({
        yawFromX: -1.02, yawFromY: .56,
        pitchFromX: -.86, pitchFromY: -.68,
        rollFromX: .76, rollFromY: .38,
        responseSpeed: 1.12,
        restPitch: -.05, restYaw: .014, restRoll: .07, restDepth: .014,
        translateXFromX: -.13, translateXFromY: .04,
        translateYFromX: -.06, translateYFromY: -.1,
        translateZFromX: -.06, translateZFromY: -.04,
        rotationLimitPitch: .72, rotationLimitYaw: 2.3,
        rotationLimitRoll: .72,
        yawPulseRadians: 1.5,
        phaseSpeed: Math.PI / 4,
        phaseOffset: Math.PI / 4
    }),
    Object.freeze({
        yawFromX: .38, yawFromY: -.66,
        pitchFromX: 1.12, pitchFromY: -.88,
        rollFromX: -.62, rollFromY: .52,
        responseSpeed: 1,
        restPitch: .01, restYaw: 0, restRoll: 0, restDepth: 0,
        translateXFromX: .1, translateXFromY: -.07,
        translateYFromX: .07, translateYFromY: -.13,
        translateZFromX: .03, translateZFromY: -.07,
        rotationLimitPitch: .72, rotationLimitYaw: 1.75,
        rotationLimitRoll: .72,
        yawPulseRadians: .8,
        phaseSpeed: Math.PI / 5,
        phaseOffset: Math.PI / 3
    }),
    Object.freeze({
        yawFromX: .66, yawFromY: -1.2,
        pitchFromX: -.68, pitchFromY: -.74,
        rollFromX: .54, rollFromY: -.72,
        responseSpeed: .9,
        restPitch: .04, restYaw: -.014, restRoll: -.07, restDepth: -.014,
        translateXFromX: .14, translateXFromY: -.04,
        translateYFromX: .05, translateYFromY: -.12,
        translateZFromX: .07, translateZFromY: -.08,
        rotationLimitPitch: .72, rotationLimitYaw: 2.55,
        rotationLimitRoll: .72,
        yawPulseRadians: 2.9,
        phaseSpeed: Math.PI / 4,
        phaseOffset: Math.PI / 4
    }),
    Object.freeze({
        yawFromX: -.84, yawFromY: .42,
        pitchFromX: .98, pitchFromY: -1.18,
        rollFromX: -.82, rollFromY: .48,
        responseSpeed: 1.16,
        restPitch: -.03, restYaw: .01, restRoll: .06, restDepth: .01,
        translateXFromX: -.12, translateXFromY: .07,
        translateYFromX: -.05, translateYFromY: -.13,
        translateZFromX: -.05, translateZFromY: -.06,
        rotationLimitPitch: .72, rotationLimitYaw: 2.3,
        rotationLimitRoll: .72,
        yawPulseRadians: 1.6,
        phaseSpeed: Math.PI / 4,
        phaseOffset: Math.PI / 4
    }),
    Object.freeze({
        yawFromX: .54, yawFromY: 1.06,
        pitchFromX: -.58, pitchFromY: -.78,
        rollFromX: .72, rollFromY: -.64,
        responseSpeed: .86,
        restPitch: .05, restYaw: -.012, restRoll: -.08, restDepth: -.012,
        translateXFromX: .13, translateXFromY: .05,
        translateYFromX: .06, translateYFromY: -.14,
        translateZFromX: .08, translateZFromY: -.05,
        rotationLimitPitch: .72, rotationLimitYaw: 2.55,
        rotationLimitRoll: .72,
        yawPulseRadians: 2.4,
        phaseSpeed: Math.PI / 2,
        phaseOffset: -Math.PI / 2
    }),
    Object.freeze({
        yawFromX: .9, yawFromY: -.52,
        pitchFromX: .76, pitchFromY: -.9,
        rollFromX: -.56, rollFromY: -.52,
        responseSpeed: 1.04,
        restPitch: -.02, restYaw: .018, restRoll: .04, restDepth: .018,
        translateXFromX: .11, translateXFromY: -.06,
        translateYFromX: .04, translateYFromY: -.11,
        translateZFromX: .04, translateZFromY: -.07,
        rotationLimitPitch: .72, rotationLimitYaw: 2.3,
        rotationLimitRoll: .72,
        yawPulseRadians: 1.7,
        phaseSpeed: Math.PI / 4,
        phaseOffset: Math.PI / 4
    })
]);

const COLUMN_SPACING = 1.12;
const ROW_SPACING = 1.02;
const TITLE_DIAMETER_RATIO = .62;
const TITLE_DEPTH_RATIO = .98;
const FACE_COLOR = 0xf2efe4;
const SIDE_COLOR = 0x778279;
const ALPHA_THRESHOLD = 32;
const ZERO_POINTER = Object.freeze({ x: 0, y: 0 });
const SWEEP_IMPULSE_PER_POINTER_UNIT = 44;
const SWEEP_IMPULSE_PITCH_RATIO = .22;
const SWEEP_IMPULSE_ROLL_RATIO = .18;
const SWEEP_FALLOFF = 4;
const REST_SPRING_PER_SECOND = 8;
const MAX_POINTER_VELOCITY = 50;
const MAX_TRANSLATION_X = .18;
const MAX_TRANSLATION_Y = .18;
const MAX_TRANSLATION_Z = .12;

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

function damp(current, target, responseSpeed, deltaSeconds) {
    const alpha = 1 - Math.exp(-responseSpeed * deltaSeconds);
    return current + (target - current) * alpha;
}

function integrateRestSpring(state, offsetKey, velocityKey, deltaSeconds) {
    if (deltaSeconds === 0) {
        return;
    }
    const offset = state[offsetKey];
    const velocity = state[velocityKey];
    const springTerm = velocity + REST_SPRING_PER_SECOND * offset;
    const decay = Math.exp(-REST_SPRING_PER_SECOND * deltaSeconds);
    state[offsetKey] = (
        offset + springTerm * deltaSeconds
    ) * decay;
    state[velocityKey] = (
        velocity - REST_SPRING_PER_SECOND * springTerm * deltaSeconds
    ) * decay;
}

function distanceToPointerSegmentSquared(anchorX, anchorY,
        startX, startY, endX, endY) {
    const segmentX = endX - startX;
    const segmentY = endY - startY;
    const lengthSquared = segmentX * segmentX + segmentY * segmentY;
    const projection = lengthSquared === 0
        ? 0
        : clamp(
            ((anchorX - startX) * segmentX
                + (anchorY - startY) * segmentY) / lengthSquared,
            0,
            1
        );
    const nearestX = startX + segmentX * projection;
    const nearestY = startY + segmentY * projection;
    const distanceX = anchorX - nearestX;
    const distanceY = anchorY - nearestY;
    return distanceX * distanceX + distanceY * distanceY;
}

function dampSineWave(current, target, amplitude, phaseSpeed,
        phaseOffset, startSeconds, endSeconds, responseSpeed) {
    if (amplitude === 0 || phaseSpeed === 0) {
        return damp(current, target, responseSpeed, endSeconds - startSeconds);
    }
    const responseSquared = responseSpeed * responseSpeed;
    const phaseSquared = phaseSpeed * phaseSpeed;
    const denominator = responseSquared + phaseSquared;
    const coefficient = amplitude * responseSpeed / denominator;
    const startPhase = phaseSpeed * startSeconds + phaseOffset;
    const endPhase = phaseSpeed * endSeconds + phaseOffset;
    const startResponse = coefficient * (
        responseSpeed * Math.sin(startPhase) - phaseSpeed * Math.cos(startPhase)
    );
    const endResponse = coefficient * (
        responseSpeed * Math.sin(endPhase) - phaseSpeed * Math.cos(endPhase)
    );
    const decay = Math.exp(-responseSpeed * (endSeconds - startSeconds));
    return target + endResponse + (
        current - target - startResponse
    ) * decay;
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
    imageDataSource,
    profile = {}
} = {}) {
    const group = new THREE.Group();
    group.name = "ReferenceGlyphRig";

    const geometries = new Set();
    const materials = new Set();
    const textures = new Set();
    const pivots = [];
    const motionStates = [];
    let disposed = false;
    let previousElapsedMs = 0;
    let phaseElapsedSeconds = 0;
    let previousPointerX = 0;
    let previousPointerY = 0;
    let hasPreviousPointer = false;
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
        const faceSideMaterial = ownMaterial(new THREE.MeshBasicMaterial({
            color: 0xffffff,
            vertexColors: true,
            toneMapped: false
        }));
        const outlineEnabled = profile.name !== "balanced";
        const inkLineMaterial = outlineEnabled
            ? ownMaterial(new THREE.LineBasicMaterial({
                color: 0x28312d
            }))
            : null;
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
            const profile = GLYPH_PROFILES[index];
            const layoutAnchor = Object.freeze({
                x: (index % 3 - 1) * COLUMN_SPACING,
                y: (1 - Math.floor(index / 3)) * ROW_SPACING,
                z: profile.restDepth,
                normalizedX: index % 3 - 1,
                normalizedY: 1 - Math.floor(index / 3)
            });
            pivot.name = `ReferenceGlyphPivot-${index + 1}`;
            pivot.userData.glyphIndex = index;
            pivot.userData.profile = profile;
            pivot.userData.layoutAnchor = layoutAnchor;
            pivot.userData.restPitch = profile.restPitch;
            pivot.userData.restYaw = profile.restYaw;
            pivot.userData.restRoll = profile.restRoll;
            pivot.userData.rotationLimitYaw = profile.rotationLimitYaw;
            pivot.position.set(
                layoutAnchor.x,
                layoutAnchor.y,
                layoutAnchor.z
            );
            pivot.rotation.set(
                profile.restPitch,
                profile.restYaw,
                profile.restRoll
            );

            const mesh = new THREE.Mesh(
                geometry,
                faceSideMaterial
            );
            mesh.name = `ReferenceGlyphMesh-${index + 1}`;
            mesh.castShadow = false;
            mesh.receiveShadow = false;

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

            pivot.add(mesh);
            if (outlineEnabled) {
                const outlineGeometry = ownGeometry(
                    new THREE.EdgesGeometry(geometry, 28)
                );
                const outline = new THREE.LineSegments(
                    outlineGeometry,
                    inkLineMaterial
                );
                outline.name = `ReferenceGlyphOutline-${index + 1}`;
                pivot.add(outline);
            }
            pivot.add(overlay);
            group.add(pivot);
            pivots.push(pivot);
            motionStates.push({
                basePitch: profile.restPitch,
                baseYaw: profile.restYaw,
                baseRoll: profile.restRoll,
                pitchOffset: 0,
                yawOffset: 0,
                rollOffset: 0,
                pitchVelocity: 0,
                yawVelocity: 0,
                rollVelocity: 0
            });
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
            const rawDeltaSeconds = Math.max(
                0,
                elapsed - previousElapsedMs
            ) / 1000;
            const deltaSeconds = Math.min(
                .05,
                rawDeltaSeconds
            );
            previousElapsedMs = elapsed;
            const previousPhaseSeconds = phaseElapsedSeconds;
            phaseElapsedSeconds += deltaSeconds;
            const pointerX = reduceMotion
                ? 0
                : clamp(finiteOr(Number(pointer?.x), 0), -1, 1);
            const pointerY = reduceMotion
                ? 0
                : clamp(finiteOr(Number(pointer?.y), 0), -1, 1);
            const pointerMagnitude = Math.max(
                Math.abs(pointerX),
                Math.abs(pointerY)
            );
            const hasPointerVelocity = hasPreviousPointer
                && rawDeltaSeconds > 0
                && pointerMagnitude > 0;
            const pointerVelocityX = hasPointerVelocity
                ? clamp(
                    (pointerX - previousPointerX) / rawDeltaSeconds,
                    -MAX_POINTER_VELOCITY,
                    MAX_POINTER_VELOCITY
                )
                : 0;
            const pointerVelocityY = hasPointerVelocity
                ? clamp(
                    (pointerY - previousPointerY) / rawDeltaSeconds,
                    -MAX_POINTER_VELOCITY,
                    MAX_POINTER_VELOCITY
                )
                : 0;

            for (let index = 0; index < pivots.length; index += 1) {
                const pivot = pivots[index];
                const profile = pivot.userData.profile;
                const anchor = pivot.userData.layoutAnchor;
                const motion = motionStates[index];
                if (reduceMotion) {
                    motion.basePitch = profile.restPitch;
                    motion.baseYaw = profile.restYaw;
                    motion.baseRoll = profile.restRoll;
                    motion.pitchOffset = 0;
                    motion.yawOffset = 0;
                    motion.rollOffset = 0;
                    motion.pitchVelocity = 0;
                    motion.yawVelocity = 0;
                    motion.rollVelocity = 0;
                    pivot.rotation.x = profile.restPitch;
                    pivot.rotation.y = profile.restYaw;
                    pivot.rotation.z = profile.restRoll;
                    pivot.position.x = anchor.x;
                    pivot.position.y = anchor.y;
                    pivot.position.z = anchor.z;
                    continue;
                }
                const localPointerX = pointerX - anchor.normalizedX;
                const localPointerY = -pointerY - anchor.normalizedY;
                const distanceSquared = localPointerX * localPointerX
                    + localPointerY * localPointerY;
                const influence = 1 / (1 + distanceSquared * 1.5);
                const localX = influence * (
                    pointerX + localPointerX * pointerMagnitude * .18
                );
                const localY = influence * (
                    pointerY - localPointerY * pointerMagnitude * .18
                );
                const responseSpeed = REFERENCE_HERO.glyphResponsePerSecond
                    * profile.responseSpeed;
                const yawTarget = clamp(
                    profile.restYaw
                    + localX * REFERENCE_HERO.glyphYawRadians
                        * profile.yawFromX
                    + localY * REFERENCE_HERO.glyphPitchRadians
                        * profile.yawFromY
                    + pointerMagnitude * influence * profile.yawPulseRadians / 2,
                    -profile.rotationLimitYaw,
                    profile.rotationLimitYaw
                );
                const yawAmplitude = pointerMagnitude * influence
                    * profile.yawPulseRadians / 2;
                const pitchTarget = clamp(
                    profile.restPitch
                    + localX * REFERENCE_HERO.glyphYawRadians
                        * profile.pitchFromX
                    + localY * REFERENCE_HERO.glyphPitchRadians
                        * profile.pitchFromY,
                    -profile.rotationLimitPitch,
                    profile.rotationLimitPitch
                );
                const rollTarget = clamp(
                    profile.restRoll + (localX * profile.rollFromX
                        + localY * profile.rollFromY) * Math.PI / 5,
                    -profile.rotationLimitRoll,
                    profile.rotationLimitRoll
                );
                const positionXTarget = anchor.x + clamp(
                    localX * profile.translateXFromX
                        + localY * profile.translateXFromY,
                    -MAX_TRANSLATION_X,
                    MAX_TRANSLATION_X
                );
                const positionYTarget = anchor.y + clamp(
                    localX * profile.translateYFromX
                        + localY * profile.translateYFromY,
                    -MAX_TRANSLATION_Y,
                    MAX_TRANSLATION_Y
                );
                const positionZTarget = anchor.z + clamp(
                    localX * profile.translateZFromX
                        + localY * profile.translateZFromY,
                    -MAX_TRANSLATION_Z,
                    MAX_TRANSLATION_Z
                );
                motion.baseYaw = clamp(dampSineWave(
                    motion.baseYaw,
                    yawTarget,
                    yawAmplitude,
                    profile.phaseSpeed,
                    profile.phaseOffset,
                    previousPhaseSeconds,
                    phaseElapsedSeconds,
                    responseSpeed
                ), -profile.rotationLimitYaw, profile.rotationLimitYaw);
                motion.basePitch = damp(
                    motion.basePitch,
                    pitchTarget,
                    responseSpeed,
                    deltaSeconds
                );
                motion.baseRoll = damp(
                    motion.baseRoll,
                    rollTarget,
                    responseSpeed,
                    deltaSeconds
                );
                if (hasPointerVelocity) {
                    const distanceSquared = distanceToPointerSegmentSquared(
                        anchor.normalizedX,
                        anchor.normalizedY,
                        previousPointerX,
                        -previousPointerY,
                        pointerX,
                        -pointerY
                    );
                    const sweepInfluence = Math.exp(
                        -distanceSquared * SWEEP_FALLOFF
                    );
                    const velocityX = pointerVelocityX;
                    const velocityY = -pointerVelocityY;
                    const impulseSeconds = deltaSeconds
                        * SWEEP_IMPULSE_PER_POINTER_UNIT
                        * sweepInfluence;
                    motion.yawVelocity += (
                        velocityX * profile.yawFromX
                        + velocityY * profile.yawFromY
                    ) * impulseSeconds;
                    motion.pitchVelocity += (
                        velocityX * profile.pitchFromX
                        + velocityY * profile.pitchFromY
                    ) * impulseSeconds * SWEEP_IMPULSE_PITCH_RATIO;
                    motion.rollVelocity += (
                        velocityX * profile.rollFromX
                        + velocityY * profile.rollFromY
                    ) * impulseSeconds * SWEEP_IMPULSE_ROLL_RATIO;
                }
                integrateRestSpring(
                    motion,
                    "pitchOffset",
                    "pitchVelocity",
                    deltaSeconds
                );
                integrateRestSpring(
                    motion,
                    "yawOffset",
                    "yawVelocity",
                    deltaSeconds
                );
                integrateRestSpring(
                    motion,
                    "rollOffset",
                    "rollVelocity",
                    deltaSeconds
                );
                pivot.rotation.x = clamp(
                    motion.basePitch + motion.pitchOffset,
                    -profile.rotationLimitPitch,
                    profile.rotationLimitPitch
                );
                pivot.rotation.y = clamp(
                    motion.baseYaw + motion.yawOffset,
                    -profile.rotationLimitYaw,
                    profile.rotationLimitYaw
                );
                pivot.rotation.z = clamp(
                    motion.baseRoll + motion.rollOffset,
                    -profile.rotationLimitRoll,
                    profile.rotationLimitRoll
                );
                pivot.position.x = damp(
                    pivot.position.x,
                    positionXTarget,
                    responseSpeed,
                    deltaSeconds
                );
                pivot.position.y = damp(
                    pivot.position.y,
                    positionYTarget,
                    responseSpeed,
                    deltaSeconds
                );
                pivot.position.z = damp(
                    pivot.position.z,
                    positionZTarget,
                    responseSpeed,
                    deltaSeconds
                );
            }
            previousPointerX = pointerX;
            previousPointerY = pointerY;
            hasPreviousPointer = true;

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
