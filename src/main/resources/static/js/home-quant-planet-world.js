import {
    CABLE_SEGMENT_PLACEMENTS,
    DETAIL_PLACEMENTS_BY_DISTRICT,
    FOREST_BELTS,
    PATH_FRAME_PLACEMENTS,
    PIPE_POLE_PLACEMENTS,
    TERRAIN_PATCHES,
    VILLAGE_PLACEMENTS,
    WORLD_DISTRICTS
} from "./home-quant-planet-world-layout.js";

export const QUANT_PLANET_PANORAMA_URL =
    "/images/quant-planet-panorama-v2.png";

const COLORS = Object.freeze({
    water: 0x78d7d0,
    waterDeep: 0x338f91,
    ground: 0xa8bf78,
    groundShade: 0x78965f,
    architecture: 0xf2e5c7,
    paper: 0xfffae9,
    coral: 0xd65f47,
    coralShade: 0xa94737,
    ink: 0x50656b,
    foliage: 0x39805b,
    foliageDark: 0x205f43,
    rock: 0x7c8580,
    gold: 0xe7b54f
});

function createGradientMap(THREE) {
    const texture = new THREE.DataTexture(
        new Uint8Array([52, 105, 158, 220]),
        4,
        1,
        THREE.RedFormat
    );
    texture.name = "ReferenceFourStepToonGradient";
    texture.minFilter = THREE.NearestFilter;
    texture.magFilter = THREE.NearestFilter;
    texture.generateMipmaps = false;
    texture.needsUpdate = true;
    return texture;
}

function createToonMaterial(THREE, color, gradientMap, options = {}) {
    return new THREE.MeshToonMaterial({ color, gradientMap, ...options });
}

function applySurfaceTexture(THREE, body, texture, material) {
    texture.colorSpace = THREE.SRGBColorSpace;
    texture.wrapS = THREE.RepeatWrapping;
    texture.wrapT = THREE.ClampToEdgeWrapping;
    texture.needsUpdate = true;
    material.map = texture;
    material.color.setHex(0xffffff);
    material.toneMapped = false;
    material.needsUpdate = true;
    body.material = material;
}

function restorePlainSurface(body, plainMaterial) {
    plainMaterial.map = null;
    plainMaterial.color.setHex(COLORS.ground);
    plainMaterial.needsUpdate = true;
    body.material = plainMaterial;
}

function loadBrowserSurfaceTexture(THREE, textureLoader, onError) {
    try {
        return (textureLoader ?? new THREE.TextureLoader()).load(
            QUANT_PLANET_PANORAMA_URL,
            undefined,
            undefined,
            onError
        );
    } catch {
        return null;
    }
}

function createWaterMaterial(THREE) {
    return new THREE.ShaderMaterial({
        uniforms: {
            time: { value: 0 },
            surfaceColor: { value: new THREE.Color(COLORS.water) },
            deepColor: { value: new THREE.Color(COLORS.waterDeep) }
        },
        vertexShader: `
            uniform float time;
            varying float vRipple;
            varying vec3 vNormal;

            void main() {
                float ripple = sin(position.x * 2.4 + time * 1.35)
                    * cos(position.z * 1.7 - time * .72);
                vRipple = ripple;
                vNormal = normalize(normalMatrix * normal);
                gl_Position = projectionMatrix
                    * modelViewMatrix
                    * vec4(position, 1.0);
            }
        `,
        fragmentShader: `
            uniform vec3 surfaceColor;
            uniform vec3 deepColor;
            varying float vRipple;
            varying vec3 vNormal;

            void main() {
                float light = .68 + max(vNormal.z, 0.0) * .24;
                float band = smoothstep(-.2, .7, vRipple);
                vec3 color = mix(deepColor, surfaceColor, .55 + band * .25);
                gl_FragColor = vec4(color * light, .84);
            }
        `,
        transparent: true,
        opacity: .84,
        depthWrite: false
    });
}

function addOutlinedMesh(THREE, parent, geometry, material, outlineMaterial, {
    position = [0, 0, 0],
    rotation = [0, 0, 0],
    scale = [1, 1, 1],
    outlined = true,
    name = ""
} = {}) {
    const mesh = new THREE.Mesh(geometry, material);
    mesh.name = name;
    mesh.position.set(...position);
    mesh.rotation.set(...rotation);
    mesh.scale.set(...scale);
    if (outlined && outlineMaterial) {
        const outline = new THREE.Mesh(geometry, outlineMaterial);
        outline.name = name ? `${name}Outline` : "ReferenceToonOutline";
        outline.scale.setScalar(1.035);
        mesh.add(outline);
    }
    parent.add(mesh);
    return mesh;
}

function orientInstance(THREE, matrix, position, scale, rotation = [0, 0, 0]) {
    const quaternion = new THREE.Quaternion().setFromEuler(
        new THREE.Euler(...rotation)
    );
    matrix.compose(
        new THREE.Vector3(...position),
        quaternion,
        new THREE.Vector3(...scale)
    );
}

function orientSurfaceInstance(
    THREE,
    matrix,
    position,
    scale,
    rotation = [0, 0, 0]
) {
    const surfaceQuaternion = new THREE.Quaternion().setFromUnitVectors(
        new THREE.Vector3(0, 1, 0),
        new THREE.Vector3(...position).normalize()
    );
    const authoredQuaternion = new THREE.Quaternion().setFromEuler(
        new THREE.Euler(...rotation)
    );
    surfaceQuaternion.multiply(authoredQuaternion);
    matrix.compose(
        new THREE.Vector3(...position),
        surfaceQuaternion,
        new THREE.Vector3(...scale)
    );
}

function pointOnPlanet(THREE, radius, yawDegrees, latitude) {
    // Keep exact 45-degree anchors inside, rather than below, their float bin.
    const yaw = yawDegrees * Math.PI / 180 + 1e-9;
    const horizontal = Math.cos(latitude);
    return new THREE.Vector3(
        Math.cos(yaw) * horizontal * radius,
        Math.sin(latitude) * radius,
        Math.sin(yaw) * horizontal * radius
    );
}

function buildDistrictAnchors(THREE) {
    const group = new THREE.Group();
    group.name = "ReferenceWorldDistricts";
    const byId = new Map();
    WORLD_DISTRICTS.forEach((districtConfig) => {
        const district = new THREE.Group();
        district.name = `ReferenceDistrict-${districtConfig.id}`;
        district.position.copy(pointOnPlanet(
            THREE,
            3.42,
            districtConfig.yawDegrees,
            districtConfig.latitude
        ));
        district.userData.landmarkAnchor = true;
        district.userData.landmarkType = districtConfig.type;
        district.userData.districtId = districtConfig.id;
        group.add(district);
        byId.set(districtConfig.id, district);
    });
    return { group, byId };
}

function markInstancesDirty(mesh) {
    mesh.instanceMatrix.needsUpdate = true;
    if (mesh.instanceColor) {
        mesh.instanceColor.needsUpdate = true;
    }
}

function buildColoredInstanceFamily(
    THREE,
    {
        name,
        geometry,
        material,
        placements
    }
) {
    const mesh = new THREE.InstancedMesh(
        geometry,
        material,
        placements.length
    );
    mesh.name = name;
    const matrix = new THREE.Matrix4();
    const color = new THREE.Color();
    const detailFamilyCounts = {};
    placements.forEach((placement, index) => {
        const orient = placement.surface
            ? orientSurfaceInstance
            : orientInstance;
        orient(
            THREE,
            matrix,
            placement.position,
            placement.scale,
            placement.rotation
        );
        mesh.setMatrixAt(index, matrix);
        mesh.setColorAt(index, color.setHex(placement.color));
        detailFamilyCounts[placement.family] =
            (detailFamilyCounts[placement.family] || 0) + 1;
    });
    mesh.userData.detailFamilyCounts = Object.freeze({
        ...detailFamilyCounts
    });
    mesh.castShadow = false;
    markInstancesDirty(mesh);
    return mesh;
}

const PACKED_DETAIL_FAMILY_ORDER = Object.freeze([
    "ReferenceIndustrialBarrelBands",
    "ReferenceIndustrialCableStays",
    "ReferenceVillageRoofCaps",
    "ReferenceVillageChimneys",
    "ReferenceTempleBeamEnds",
    "ReferenceMountainStrata",
    "ReferenceWaterFoamAccents",
    "ReferenceTerrainRetainingEdges",
    "ReferenceCanopyForks",
    "ReferenceUnderCanopyStones"
]);

const PACKED_DETAIL_COLORS = Object.freeze({
    ReferenceIndustrialBarrelBands: Object.freeze([
        COLORS.paper,
        COLORS.gold,
        COLORS.coral
    ]),
    ReferenceIndustrialCableStays: Object.freeze([
        COLORS.ink,
        COLORS.paper,
        COLORS.gold
    ]),
    ReferenceVillageRoofCaps: Object.freeze([
        COLORS.coral,
        COLORS.coralShade,
        COLORS.paper
    ]),
    ReferenceVillageChimneys: Object.freeze([
        COLORS.rock,
        COLORS.coralShade,
        COLORS.gold
    ]),
    ReferenceTempleBeamEnds: Object.freeze([
        COLORS.gold,
        COLORS.paper,
        COLORS.coral
    ]),
    ReferenceMountainStrata: Object.freeze([
        COLORS.rock,
        COLORS.paper,
        COLORS.groundShade
    ]),
    ReferenceWaterFoamAccents: Object.freeze([
        COLORS.paper,
        COLORS.water,
        COLORS.gold
    ]),
    ReferenceTerrainRetainingEdges: Object.freeze([
        COLORS.rock,
        COLORS.groundShade,
        COLORS.paper
    ]),
    ReferenceCanopyForks: Object.freeze([
        COLORS.foliage,
        COLORS.groundShade,
        COLORS.gold
    ]),
    ReferenceUnderCanopyStones: Object.freeze([
        COLORS.rock,
        COLORS.groundShade,
        COLORS.paper
    ])
});

const LOCAL_DETAIL_TRANSFORMS = Object.freeze({
    ReferenceIndustrialBarrelBands: Object.freeze({
        position: Object.freeze([0, 0, 0]),
        rotation: Object.freeze([0, -1.82, 0])
    }),
    ReferenceIndustrialCableStays: Object.freeze({
        position: Object.freeze([0, 0, 0]),
        rotation: Object.freeze([0, -1.82, 0])
    }),
    ReferenceTempleBeamEnds: Object.freeze({
        position: Object.freeze([1.55, 0, 0]),
        rotation: Object.freeze([0, Math.PI / 2, 0])
    })
});

function transformedDetailPlacement(THREE, placement, familyName) {
    const parentTransform = LOCAL_DETAIL_TRANSFORMS[familyName];
    if (!parentTransform) {
        return placement;
    }
    const parentQuaternion = new THREE.Quaternion().setFromEuler(
        new THREE.Euler(...parentTransform.rotation)
    );
    const authoredQuaternion = new THREE.Quaternion().setFromEuler(
        new THREE.Euler(...placement.rotation)
    );
    const transformedQuaternion = parentQuaternion.clone()
        .multiply(authoredQuaternion);
    const transformedPosition = new THREE.Vector3(...placement.position)
        .applyQuaternion(parentQuaternion)
        .add(new THREE.Vector3(...parentTransform.position));
    const transformedRotation = new THREE.Euler()
        .setFromQuaternion(transformedQuaternion);
    return {
        ...placement,
        position: transformedPosition.toArray(),
        rotation: [
            transformedRotation.x,
            transformedRotation.y,
            transformedRotation.z
        ]
    };
}

function canopyCrownTransform(THREE, placement, index) {
    const normal = new THREE.Vector3(...placement.position).normalize();
    return {
        position: new THREE.Vector3(...placement.position)
            .addScaledVector(normal, .64 + (index % 3) * .07)
            .toArray(),
        scale: placement.scale.map((axis) => axis * .55),
        rotation: placement.rotation
    };
}

function connectForkToSelectedCrown(
    THREE,
    placement,
    foliagePlacements
) {
    const parentIndex = foliagePlacements.findIndex(
        (parent) => parent.id === placement.parentId
    );
    if (parentIndex < 0) {
        throw new Error(`Missing selected canopy parent ${placement.parentId}`);
    }
    const parent = foliagePlacements[parentIndex];
    const crown = canopyCrownTransform(THREE, parent, parentIndex);
    const normal = new THREE.Vector3(...parent.position).normalize();
    return {
        ...placement,
        position: new THREE.Vector3(...crown.position)
            .addScaledVector(normal, -crown.scale[1] * .55)
            .toArray()
    };
}

function createPackedMiniatureDetailPlacements(
    THREE,
    foliagePlacements
) {
    const placements = [];
    const detailFamilyRanges = {};
    PACKED_DETAIL_FAMILY_ORDER.forEach((familyName) => {
        const familyPlacements = Object.values(
            DETAIL_PLACEMENTS_BY_DISTRICT
        ).flatMap((district) => district[familyName] ?? []);
        const offset = placements.length;
        const colors = PACKED_DETAIL_COLORS[familyName];
        familyPlacements.forEach((placement, index) => {
            const profilePlacement = familyName === "ReferenceCanopyForks"
                ? connectForkToSelectedCrown(
                    THREE,
                    placement,
                    foliagePlacements
                )
                : placement;
            placements.push({
                ...transformedDetailPlacement(
                    THREE,
                    profilePlacement,
                    familyName
                ),
                family: familyName,
                color: colors[index % colors.length],
                surface: !LOCAL_DETAIL_TRANSFORMS[familyName]
            });
        });
        detailFamilyRanges[familyName] = Object.freeze({
            offset,
            count: familyPlacements.length,
            districtIds: Object.freeze(
                familyPlacements.map((placement) => placement.districtId)
            ),
            parentIds: Object.freeze(
                familyPlacements.map((placement) => placement.parentId)
            )
        });
    });
    return {
        placements,
        detailFamilyRanges
    };
}

function tagSurfaceInstances(mesh, placements) {
    mesh.userData.surfaceDetailIds = Object.freeze(
        placements.map((placement) => placement.id)
    );
}

const SURFACE_CATEGORIES = Object.freeze([
    "terrain",
    "structure",
    "foliageRock",
    "utilitySilhouette"
]);

function yawDegreesForPlacement(placement) {
    const yaw = Math.atan2(
        placement.position[2],
        placement.position[0]
    ) * 180 / Math.PI;
    return (yaw + 360) % 360;
}

function signedYawDelta(yawDegrees, centerDegrees) {
    return (
        (yawDegrees - centerDegrees + 540) % 360
    ) - 180;
}

function emptyCategoryCounts() {
    return {
        terrain: 0,
        structure: 0,
        foliageRock: 0,
        utilitySilhouette: 0
    };
}

function summarizePlacements(placements) {
    const counts = emptyCategoryCounts();
    placements.forEach((placement) => {
        counts[placement.category] += 1;
    });
    return Object.freeze(counts);
}

function categoryTotal(counts) {
    return SURFACE_CATEGORIES.reduce(
        (total, category) => total + counts[category],
        0
    );
}

function buildSurfaceDensity(placements) {
    const immutablePlacements = Object.freeze([...placements]);
    const azimuths = immutablePlacements
        .map(yawDegreesForPlacement)
        .toSorted((left, right) => left - right);
    let maximumAzimuthGapDegrees = 0;
    azimuths.forEach((yaw, index) => {
        const nextYaw = index === azimuths.length - 1
            ? azimuths[0] + 360
            : azimuths[index + 1];
        maximumAzimuthGapDegrees = Math.max(
            maximumAzimuthGapDegrees,
            nextYaw - yaw
        );
    });

    const sectors = Object.freeze(WORLD_DISTRICTS.map(
        (district, index) => {
            const sectorPlacements = immutablePlacements.filter(
                (placement) => placement.sectorIndex === index
            );
            const counts = summarizePlacements(sectorPlacements);
            return Object.freeze({
                index,
                districtId: district.id,
                centerYawDegrees: district.yawDegrees,
                counts,
                total: categoryTotal(counts)
            });
        }
    ));

    const boundarySamples = Object.freeze(WORLD_DISTRICTS.map(
        (_, index) => {
            const yawDegrees = index * 45 + 22.5;
            const samplePlacements = immutablePlacements.filter(
                (placement) => {
                    const delta = signedYawDelta(
                        yawDegreesForPlacement(placement),
                        yawDegrees
                    );
                    return delta >= -22.5 && delta < 22.5;
                }
            );
            const counts = summarizePlacements(samplePlacements);
            return Object.freeze({
                yawDegrees,
                counts,
                total: categoryTotal(counts)
            });
        }
    ));

    const frontViews = Object.freeze(WORLD_DISTRICTS.map((_, index) => {
        const yawDegrees = index * 45;
        const frontCenter = yawDegrees + 90;
        const frontPlacements = immutablePlacements.filter((placement) => {
            const delta = signedYawDelta(
                yawDegreesForPlacement(placement),
                frontCenter
            );
            return delta > -90 && delta <= 90;
        });
        const counts = summarizePlacements(frontPlacements);
        const projectedQuadrants = new Set();
        const yawRadians = yawDegrees * Math.PI / 180;
        frontPlacements.forEach((placement) => {
            const [x, y, z] = placement.position;
            const projectedX = Math.cos(yawRadians) * x
                + Math.sin(yawRadians) * z;
            projectedQuadrants.add(
                `${projectedX < 0 ? "left" : "right"}-${
                    y < 0 ? "lower" : "upper"
                }`
            );
        });
        return Object.freeze({
            yawDegrees,
            counts,
            total: categoryTotal(counts),
            visibleCategoryCount: SURFACE_CATEGORIES.filter(
                (category) => counts[category] > 0
            ).length,
            projectedQuadrants: Object.freeze(
                [...projectedQuadrants].toSorted()
            )
        });
    }));

    const latitudeBands = {
        upper: 0,
        middle: 0,
        lower: 0
    };
    immutablePlacements.forEach((placement) => {
        const [x, y, z] = placement.position;
        const latitude = Math.asin(y / Math.hypot(x, y, z));
        const band = latitude > .25
            ? "upper"
            : latitude < -.25
                ? "lower"
                : "middle";
        latitudeBands[band] += 1;
    });

    return Object.freeze({
        detailIds: Object.freeze(
            immutablePlacements.map((placement) => placement.id)
        ),
        totalDetails: immutablePlacements.length,
        sectors,
        boundarySamples,
        frontViews,
        latitudeBands: Object.freeze(latitudeBands),
        maximumAzimuthGapDegrees
    });
}

function selectFoliagePlacements(profile) {
    const allPlacements = FOREST_BELTS.flatMap(
        (belt) => belt.placements
    );
    if (profile.name === "high") {
        return Object.freeze(allPlacements);
    }

    const requestedCount = Math.max(
        0,
        Math.floor(profile.foliageCount ?? 18)
    );
    if (requestedCount === 0) {
        return Object.freeze([]);
    }

    const minimumDenseCount = WORLD_DISTRICTS.length * 6;
    const targetCount = Math.min(
        allPlacements.length,
        profile.name === "balanced"
            ? Math.max(requestedCount, minimumDenseCount)
            : requestedCount
    );
    const continuousBelt = FOREST_BELTS.find(
        (belt) => belt.id === "continuous-canopy"
    );
    const continuousIds = new Set(
        continuousBelt?.placements.map((placement) => placement.id)
    );
    const prioritizedPlacements = [
        ...(continuousBelt?.placements ?? []),
        ...allPlacements.filter(
            (placement) => !continuousIds.has(placement.id)
        )
    ];
    const placementsBySector = Array.from(
        { length: WORLD_DISTRICTS.length },
        () => []
    );
    prioritizedPlacements.forEach((placement) => {
        placementsBySector[placement.sectorIndex].push(placement);
    });

    const selected = [];
    for (
        let round = 0;
        selected.length < targetCount;
        round += 1
    ) {
        let addedThisRound = false;
        for (
            let sectorIndex = 0;
            sectorIndex < placementsBySector.length
                && selected.length < targetCount;
            sectorIndex += 1
        ) {
            const placement = placementsBySector[sectorIndex][round];
            if (placement) {
                selected.push(placement);
                addedThisRound = true;
            }
        }
        if (!addedThisRound) {
            break;
        }
    }
    return Object.freeze(selected);
}

function buildPlanetCore(
    THREE,
    resources,
    palette,
    foliagePlacements
) {
    const group = new THREE.Group();
    group.name = "ReferencePlanetCore";

    const body = addOutlinedMesh(
        THREE,
        group,
        resources.geometry.detailedSphere,
        palette.ground,
        resources.heroOutline,
        {
            position: [0, 0, 0],
            rotation: [.04, -.16, -.045],
            scale: [3.35, 3.55, 3.35],
            name: "ReferencePlanetBody"
        }
    );
    body.receiveShadow = Boolean(resources.shadows);

    const terrain = new THREE.InstancedMesh(
        resources.geometry.sphere,
        palette.groundShade,
        TERRAIN_PATCHES.length
    );
    terrain.name = "ReferenceTerrainPatches";
    const matrix = new THREE.Matrix4();
    const terrainColors = [
        new THREE.Color(COLORS.rock),
        new THREE.Color(COLORS.groundShade),
        new THREE.Color(0xc8bb8a)
    ];
    TERRAIN_PATCHES.forEach((placement, index) => {
        orientSurfaceInstance(
            THREE,
            matrix,
            placement.position,
            placement.scale,
            placement.rotation
        );
        terrain.setMatrixAt(index, matrix);
        terrain.setColorAt(index, terrainColors[index % terrainColors.length]);
    });
    markInstancesDirty(terrain);
    tagSurfaceInstances(terrain, TERRAIN_PATCHES);
    group.add(terrain);

    const rockPositions = TERRAIN_PATCHES.filter((_, index) => index % 2);
    const rocks = new THREE.InstancedMesh(
        resources.geometry.sphere,
        palette.rock,
        rockPositions.length
    );
    rocks.name = "ReferenceCoreRocks";
    rockPositions.forEach((placement, index) => {
        const position = new THREE.Vector3(...placement.position)
            .multiplyScalar(1.025)
            .toArray();
        const size = .16 + (index % 4) * .035;
        orientSurfaceInstance(
            THREE,
            matrix,
            position,
            [size * 1.2, size, size * .85],
            placement.rotation
        );
        rocks.setMatrixAt(index, matrix);
    });
    markInstancesDirty(rocks);
    group.add(rocks);

    const reliefPlacements = TERRAIN_PATCHES
        .filter((_, index) => index % 3 === 0)
        .map((placement, index) => ({
            family: "cliffLedges",
            position: new THREE.Vector3(...placement.position)
                .multiplyScalar(1.035)
                .toArray(),
            scale: [
                placement.scale[0] * .58,
                .055 + (index % 3) * .012,
                placement.scale[2] * .48
            ],
            rotation: placement.rotation,
            color: index % 2 === 0
                ? COLORS.rock
                : COLORS.groundShade,
            surface: true
        }));
    TERRAIN_PATCHES
        .filter((_, index) => index % 3 === 0)
        .forEach((placement, index) => {
            const normal = new THREE.Vector3(...placement.position)
                .normalize();
            const tangent = new THREE.Vector3(
                -normal.z,
                0,
                normal.x
            ).normalize();
            reliefPlacements.push({
                family: "cliffStrata",
                position: new THREE.Vector3(...placement.position)
                    .multiplyScalar(1.046)
                    .addScaledVector(
                        tangent,
                        (index % 2 === 0 ? -.12 : .12)
                    )
                    .toArray(),
                scale: [
                    placement.scale[0] * .42,
                    .026,
                    placement.scale[2] * .34
                ],
                rotation: placement.rotation,
                color: index % 2 === 0
                    ? COLORS.paper
                    : COLORS.rock,
                surface: true
            });
            reliefPlacements.push({
                family: "secondaryStrata",
                position: new THREE.Vector3(...placement.position)
                    .multiplyScalar(1.054)
                    .addScaledVector(
                        tangent,
                        (index % 2 === 0 ? .16 : -.16)
                    )
                    .toArray(),
                scale: [
                    placement.scale[0] * .31,
                    .018,
                    placement.scale[2] * .26
                ],
                rotation: placement.rotation,
                color: index % 3 === 0
                    ? COLORS.gold
                    : COLORS.groundShade,
                surface: true
            });
        });
    TERRAIN_PATCHES.slice(0, 12).forEach((placement, index) => {
        const normal = new THREE.Vector3(...placement.position).normalize();
        const tangent = new THREE.Vector3(
            -normal.z,
            0,
            normal.x
        ).normalize();
        reliefPlacements.push({
            family: "pathChips",
            position: new THREE.Vector3(...placement.position)
                .multiplyScalar(1.061)
                .addScaledVector(
                    tangent,
                    (index % 3 - 1) * .14
                )
                .toArray(),
            scale: [
                .045 + (index % 2) * .018,
                .018,
                .035 + (index % 3) * .012
            ],
            rotation: [
                placement.rotation[0],
                placement.rotation[1] + index * .13,
                placement.rotation[2]
            ],
            color: index % 4 === 0 ? COLORS.paper : COLORS.rock,
            surface: true
        });
    });
    const packedDetails = createPackedMiniatureDetailPlacements(
        THREE,
        foliagePlacements
    );
    const packedOffset = reliefPlacements.length;
    reliefPlacements.push(...packedDetails.placements);
    const relief = buildColoredInstanceFamily(THREE, {
        name: "ReferenceTerrainReliefDetails",
        geometry: resources.geometry.box,
        material: palette.village,
        placements: reliefPlacements
    });
    relief.userData.detailFamilyRanges = Object.freeze(
        Object.fromEntries(Object.entries(
            packedDetails.detailFamilyRanges
        ).map(([name, range]) => [
            name,
            Object.freeze({
                ...range,
                offset: range.offset + packedOffset
            })
        ]))
    );
    relief.userData.detailDistrictIds = Object.freeze(
        reliefPlacements.map((placement) => placement.districtId)
    );
    group.add(relief);
    return group;
}

function buildWaterBand(THREE, resources, palette) {
    const group = new THREE.Group();
    group.name = "ReferenceWaterBand";
    const waterPlacements = [
        [[2.72, 1.34, -1.7], [.92, .055, .64], [.02, -.12, .08]],
        [[.12, .72, 3.26], [.78, .05, .58], [-.04, .14, -.06]],
        [[-2.76, -.42, 1.8], [.72, .045, .52], [.06, -.16, .1]],
        [[1.92, -1.56, -2.2], [.66, .045, .48], [-.05, .12, -.08]]
    ];
    const water = new THREE.InstancedMesh(
        resources.geometry.sphere,
        palette.water,
        waterPlacements.length
    );
    water.name = "ReferenceWaterSurface";
    const matrix = new THREE.Matrix4();
    waterPlacements.forEach(([position, scale, rotation], index) => {
        orientSurfaceInstance(
            THREE,
            matrix,
            position,
            scale,
            rotation
        );
        water.setMatrixAt(index, matrix);
    });
    markInstancesDirty(water);
    group.add(water);
    water.renderOrder = 2;

    const foam = new THREE.InstancedMesh(
        resources.geometry.box,
        palette.paper,
        8
    );
    foam.name = "ReferenceWaterFoamBars";
    const foamBars = [
        [[2.78, 1.44, -1.52], [.32, .025, .04], [0, .18, 0]],
        [[2.5, 1.2, -1.98], [.24, .025, .035], [0, -.14, .04]],
        [[.4, .86, 3.18], [.3, .025, .04], [0, .12, -.04]],
        [[-.3, .62, 3.28], [.22, .025, .035], [0, -.18, .03]],
        [[-2.62, -.24, 2.06], [.28, .025, .04], [0, .16, -.04]],
        [[-2.92, -.56, 1.58], [.22, .025, .035], [0, -.12, .04]],
        [[2.08, -1.38, -2.12], [.26, .025, .04], [0, .14, -.03]],
        [[1.7, -1.72, -2.36], [.2, .025, .035], [0, -.16, .03]]
    ];
    foamBars.forEach(([position, scale, rotation], index) => {
        orientSurfaceInstance(
            THREE,
            matrix,
            position,
            scale,
            rotation
        );
        foam.setMatrixAt(index, matrix);
    });
    markInstancesDirty(foam);
    group.add(foam);

    const bridgePlacements = [
        [[2.66, 1.52, -1.72], [.58, .045, .12], [.04, .26, -.02]],
        [[.08, .9, 3.22], [.5, .04, .1], [-.03, -.22, .04]],
        [[-2.78, -.28, 1.88], [.46, .04, .1], [.05, .2, -.03]],
        [[1.92, -1.42, -2.3], [.42, .04, .09], [-.04, -.18, .03]]
    ];
    const bridges = new THREE.InstancedMesh(
        resources.geometry.box,
        palette.ink,
        bridgePlacements.length
    );
    bridges.name = "ReferenceWaterBridges";
    bridgePlacements.forEach(([position, scale, rotation], index) => {
        orientSurfaceInstance(
            THREE,
            matrix,
            position,
            scale,
            rotation
        );
        bridges.setMatrixAt(index, matrix);
    });
    markInstancesDirty(bridges);
    group.add(bridges);

    const waterEdgePlacements = [];
    bridgePlacements.forEach(([position, scale, rotation]) => {
        const normal = new THREE.Vector3(...position).normalize();
        const tangent = new THREE.Vector3(
            -normal.z,
            0,
            normal.x
        ).normalize();
        for (const side of [-1, 1]) {
            waterEdgePlacements.push({
                family: "bridgeRails",
                position: new THREE.Vector3(...position)
                    .addScaledVector(normal, .11)
                    .addScaledVector(tangent, side * scale[2] * .72)
                    .toArray(),
                scale: [scale[0] * 1.04, .035, .035],
                rotation,
                color: side === -1 ? COLORS.paper : COLORS.gold,
                surface: true
            });
        }
        for (const offset of [-.42, -.14, .14, .42]) {
            waterEdgePlacements.push({
                family: "bridgeTies",
                position: new THREE.Vector3(...position)
                    .addScaledVector(normal, .075)
                    .addScaledVector(tangent, offset * scale[0])
                    .toArray(),
                scale: [.035, .025, scale[2] * .82],
                rotation,
                color: Math.abs(offset) > .3
                    ? COLORS.gold
                    : COLORS.paper,
                surface: true
            });
        }
        for (const offset of [-.48, -.24, 0, .24, .48]) {
            waterEdgePlacements.push({
                family: "bridgePlanks",
                position: new THREE.Vector3(...position)
                    .addScaledVector(normal, .085)
                    .addScaledVector(tangent, offset * scale[0])
                    .toArray(),
                scale: [.055, .018, scale[2] * .68],
                rotation,
                color: offset === 0 ? COLORS.gold : COLORS.paper,
                surface: true
            });
        }
    });
    waterPlacements.forEach(([position], waterIndex) => {
        const normal = new THREE.Vector3(...position).normalize();
        const tangent = new THREE.Vector3(
            -normal.z,
            0,
            normal.x
        ).normalize();
        for (const offset of [-.42, 0, .42]) {
            waterEdgePlacements.push({
                family: "bankStones",
                position: new THREE.Vector3(...position)
                    .multiplyScalar(1.035)
                    .addScaledVector(tangent, offset)
                    .toArray(),
                scale: [
                    .18 + (waterIndex % 2) * .035,
                    .08,
                    .14 + Math.abs(offset) * .06
                ],
                rotation: [0, offset * .5, waterIndex * .08],
                color: offset === 0 ? COLORS.groundShade : COLORS.rock,
                surface: true
            });
        }
        for (const offset of [-.28, 0, .28]) {
            waterEdgePlacements.push({
                family: "foamAccents",
                position: new THREE.Vector3(...position)
                    .multiplyScalar(1.052)
                    .addScaledVector(tangent, offset)
                    .toArray(),
                scale: [
                    .12 + (waterIndex % 2) * .025,
                    .014,
                    .028
                ],
                rotation: [0, offset * .34, waterIndex * .08],
                color: COLORS.paper,
                surface: true
            });
        }
    });
    const waterEdges = buildColoredInstanceFamily(THREE, {
        name: "ReferenceWaterEdgeDetails",
        geometry: resources.geometry.box,
        material: palette.village,
        placements: waterEdgePlacements
    });
    group.add(waterEdges);
    return { group, water };
}

function buildTemplePeak(THREE, resources, palette) {
    const group = new THREE.Group();
    group.name = "ReferenceTemplePeak";

    const mountainPlacements = [
        [-1.35, 3.63, .78, [1.1, 2.55, 1.02], -.06],
        [.1, 3.72, .58, [1.38, 2.88, 1.2], .02],
        [1.48, 3.5, .7, [1.02, 2.35, .94], .08]
    ];
    const mountains = new THREE.InstancedMesh(
        resources.geometry.cone,
        palette.paper,
        mountainPlacements.length
    );
    mountains.name = "ReferencePaleMountains";
    const matrix = new THREE.Matrix4();
    mountainPlacements.forEach(([x, y, z, scale, rotation], index) => {
        orientInstance(
            THREE,
            matrix,
            [x, y, z],
            scale,
            [0, 0, rotation]
        );
        mountains.setMatrixAt(index, matrix);
    });
    markInstancesDirty(mountains);
    group.add(mountains);

    addOutlinedMesh(
        THREE,
        group,
        resources.geometry.box,
        palette.architecture,
        resources.outline,
        {
            position: [-.05, 4.38, 1.72],
            rotation: [0, -.08, 0],
            scale: [1.24, .56, .72],
            name: "ReferenceTempleHall"
        }
    );

    const columns = new THREE.InstancedMesh(
        resources.geometry.cylinder,
        palette.paper,
        5
    );
    columns.name = "ReferenceTempleColumns";
    for (let index = 0; index < columns.count; index += 1) {
        orientInstance(
            THREE,
            matrix,
            [-.47 + index * .23, 4.12, 2.12],
            [.045, .28, .045]
        );
        columns.setMatrixAt(index, matrix);
    }
    markInstancesDirty(columns);
    group.add(columns);

    const roofPieces = new THREE.InstancedMesh(
        resources.geometry.box,
        palette.ink,
        3
    );
    roofPieces.name = "ReferenceTempleRoofPieces";
    [
        [[-.05, 4.77, 1.72], [1.48, .17, .93], [0, -.08, 0]],
        [[-1.19, 4.83, 1.72], [.72, .12, .86], [0, -.08, .13]],
        [[1.08, 4.81, 1.72], [.72, .12, .86], [0, -.08, -.13]]
    ].forEach(([position, scale, rotation], index) => {
        orientInstance(THREE, matrix, position, scale, rotation);
        roofPieces.setMatrixAt(index, matrix);
    });
    markInstancesDirty(roofPieces);
    group.add(roofPieces);

    addOutlinedMesh(
        THREE,
        group,
        resources.geometry.cylinder,
        palette.gold,
        null,
        {
            position: [-.05, 5.03, 1.72],
            scale: [.045, .24, .045],
            outlined: false,
            name: "ReferenceTempleFinialStem"
        }
    );
    addOutlinedMesh(
        THREE,
        group,
        resources.geometry.cone,
        palette.gold,
        null,
        {
            position: [-.05, 5.28, 1.72],
            scale: [.2, .28, .2],
            outlined: false,
            name: "ReferenceTempleFinial"
        }
    );
    const templeDetails = buildColoredInstanceFamily(THREE, {
        name: "ReferenceTempleBoxDetails",
        geometry: resources.geometry.box,
        material: palette.village,
        placements: [
            {
                family: "roofTrim",
                position: [-.05, 4.9, 1.72],
                scale: [1.5, .07, .08],
                rotation: [0, -.08, 0],
                color: COLORS.paper
            },
            {
                family: "roofTrim",
                position: [-1.16, 4.91, 1.72],
                scale: [.72, .055, .07],
                rotation: [0, -.08, .13],
                color: COLORS.coral
            },
            {
                family: "roofTrim",
                position: [1.05, 4.9, 1.72],
                scale: [.72, .055, .07],
                rotation: [0, -.08, -.13],
                color: COLORS.coral
            },
            {
                family: "entrance",
                position: [-.05, 4.34, 2.095],
                scale: [.34, .32, .035],
                rotation: [0, -.08, 0],
                color: COLORS.ink
            },
            ...Array.from({ length: 6 }, (_, index) => ({
                family: "eaveBrackets",
                position: [-1.2 + index * .46, 4.73, 1.94],
                scale: [.055, .15, .07],
                rotation: [0, -.08, index % 2 === 0 ? -.08 : .08],
                color: index % 2 === 0 ? COLORS.gold : COLORS.coral
            })),
            ...Array.from({ length: 3 }, (_, index) => ({
                family: "steps",
                position: [-.05, 4.05 - index * .11, 2.08 + index * .07],
                scale: [.48 + index * .12, .045, .12],
                rotation: [0, -.08, 0],
                color: index === 1 ? COLORS.paper : COLORS.rock
            })),
            ...Array.from({ length: 8 }, (_, index) => ({
                family: "eaveTeeth",
                position: [-1.22 + index * .35, 4.65, 2.03],
                scale: [.045, .09, .055],
                rotation: [0, -.08, index % 2 === 0 ? -.05 : .05],
                color: index % 3 === 0 ? COLORS.gold : COLORS.paper
            })),
            ...Array.from({ length: 3 }, (_, index) => ({
                family: "stairBands",
                position: [-.05, 4.03 - index * .11, 2.16 + index * .07],
                scale: [.5 + index * .12, .018, .035],
                rotation: [0, -.08, 0],
                color: index === 1 ? COLORS.gold : COLORS.ink
            })),
            ...Array.from({ length: 5 }, (_, index) => ({
                family: "columnBases",
                position: [-.47 + index * .23, 3.86, 2.12],
                scale: [.085, .055, .085],
                rotation: [0, 0, 0],
                color: COLORS.gold
            })),
            {
                family: "rockLedges",
                position: [-1.18, 3.1, 1.06],
                scale: [.92, .12, .42],
                rotation: [.08, -.22, -.12],
                color: COLORS.rock
            },
            {
                family: "rockLedges",
                position: [.18, 2.98, .9],
                scale: [1.08, .13, .48],
                rotation: [-.06, .16, .08],
                color: COLORS.groundShade
            },
            {
                family: "rockLedges",
                position: [1.28, 3.08, 1.02],
                scale: [.76, .11, .4],
                rotation: [.05, .28, .13],
                color: COLORS.rock
            }
        ]
    });
    group.add(templeDetails);
    return group;
}

function buildIndustrialCluster(THREE, resources, palette) {
    const group = new THREE.Group();
    group.name = "ReferenceIndustrialCluster";

    const mainEngine = addOutlinedMesh(
        THREE,
        group,
        resources.geometry.cylinder,
        palette.coral,
        resources.outline,
        {
            position: [-3.72, .62, 1.18],
            rotation: [0, 0, Math.PI / 2],
            scale: [.68, 1.34, .68],
            name: "ReferenceMainEngine"
        }
    );
    mainEngine.castShadow = false;
    addOutlinedMesh(
        THREE,
        group,
        resources.geometry.cylinder,
        palette.coralShade,
        resources.outline,
        {
            position: [-3.42, -.78, 1.22],
            rotation: [0, 0, Math.PI / 2],
            scale: [.58, 1.04, .58],
            name: "ReferenceLowerEngine"
        }
    );
    addOutlinedMesh(
        THREE,
        group,
        resources.geometry.cylinder,
        palette.coral,
        null,
        {
            position: [-4.15, 1.82, .94],
            rotation: [0, 0, Math.PI / 2],
            scale: [.31, .78, .31],
            outlined: false,
            name: "ReferenceUpperPipe"
        }
    );
    addOutlinedMesh(
        THREE,
        group,
        resources.geometry.torus,
        palette.coralShade,
        null,
        {
            position: [-4.02, -1.62, 1.05],
            rotation: [Math.PI / 2, .1, 0],
            scale: [.58, .58, .58],
            outlined: false,
            name: "ReferencePipeElbow"
        }
    );
    addOutlinedMesh(
        THREE,
        group,
        resources.geometry.box,
        palette.rock,
        null,
        {
            position: [-3.2, .02, .88],
            rotation: [0, -.18, -.12],
            scale: [.65, 2.35, .62],
            outlined: false,
            name: "ReferenceEngineMount"
        }
    );

    const braces = new THREE.InstancedMesh(
        resources.geometry.box,
        palette.ink,
        8
    );
    braces.name = "ReferenceIndustrialBraces";
    const matrix = new THREE.Matrix4();
    for (let index = 0; index < braces.count; index += 1) {
        const upper = index < 4;
        orientInstance(
            THREE,
            matrix,
            [-4.72 + (index % 4) * .58, upper ? .22 : -.78, 1.84],
            [.045, .42, .045],
            [0, 0, upper ? -.24 : .24]
        );
        braces.setMatrixAt(index, matrix);
    }
    markInstancesDirty(braces);
    group.add(braces);

    const industrialRings = buildColoredInstanceFamily(THREE, {
        name: "ReferenceIndustrialRingDetails",
        geometry: resources.geometry.torus,
        material: palette.village,
        placements: [
            [-4.28, .62, 1.18, .73, COLORS.paper],
            [-3.18, .62, 1.18, .73, COLORS.coralShade],
            [-3.86, -.78, 1.22, .63, COLORS.paper],
            [-3.02, -.78, 1.22, .63, COLORS.coral]
        ].map(([x, y, z, scale, color]) => ({
            family: "collars",
            position: [x, y, z],
            scale: [scale, scale, scale],
            rotation: [0, Math.PI / 2, 0],
            color
        }))
    });

    const industrialBoxPlacements = [];
    for (const y of [.35, .9]) {
        for (const x of [-4.14, -3.86, -3.58, -3.3]) {
            industrialBoxPlacements.push({
                family: "fasteners",
                position: [x, y, 1.87],
                scale: [.075, .075, .055],
                rotation: [0, 0, .18],
                color: COLORS.gold
            });
        }
    }
    industrialBoxPlacements.push(
        {
            family: "catwalk",
            position: [-3.72, -.08, 1.88],
            scale: [1.58, .07, .34],
            rotation: [0, 0, -.04],
            color: COLORS.rock
        },
        {
            family: "catwalk",
            position: [-3.72, .13, 2.02],
            scale: [1.58, .045, .045],
            rotation: [0, 0, -.04],
            color: COLORS.ink
        },
        {
            family: "catwalk",
            position: [-3.72, .13, 1.73],
            scale: [1.58, .045, .045],
            rotation: [0, 0, -.04],
            color: COLORS.ink
        },
        {
            family: "accessPanel",
            position: [-3.72, .62, 1.9],
            scale: [.5, .38, .045],
            rotation: [0, 0, -.03],
            color: COLORS.paper
        },
        {
            family: "secondaryPipes",
            position: [-4.52, 1.32, 1.08],
            scale: [.42, .12, .12],
            rotation: [0, 0, .34],
            color: COLORS.coralShade
        },
        {
            family: "secondaryPipes",
            position: [-3.15, -1.42, 1.08],
            scale: [.36, .1, .1],
            rotation: [0, 0, -.46],
            color: COLORS.coral
        }
    );
    for (let index = 0; index < 6; index += 1) {
        industrialBoxPlacements.push({
            family: "panelSeams",
            position: [-4.18 + index * .19, .62, 1.955],
            scale: [.035, .31, .022],
            rotation: [0, 0, -.03],
            color: index % 2 === 0 ? COLORS.rock : COLORS.ink
        });
        industrialBoxPlacements.push({
            family: "gaugeTicks",
            position: [
                -3.9 + Math.cos(index * Math.PI / 3) * .2,
                .62 + Math.sin(index * Math.PI / 3) * .19,
                1.975
            ],
            scale: [.035, .065, .02],
            rotation: [0, 0, index * Math.PI / 3],
            color: COLORS.gold
        });
    }
    for (let index = 0; index < 5; index += 1) {
        industrialBoxPlacements.push({
            family: "ladderRungs",
            position: [-3.08, -.34 + index * .18, 1.94],
            scale: [.22, .028, .025],
            rotation: [0, 0, -.03],
            color: index % 2 === 0 ? COLORS.paper : COLORS.rock
        });
    }
    for (let index = 0; index < 8; index += 1) {
        const upper = index < 4;
        industrialBoxPlacements.push({
            family: "pipeClamps",
            position: [
                -4.48 + (index % 4) * .46,
                upper ? .68 : -.76,
                upper ? 1.74 : 1.7
            ],
            scale: [.055, .16, .035],
            rotation: [0, 0, upper ? -.08 : .08],
            color: index % 2 === 0 ? COLORS.gold : COLORS.paper
        });
    }
    for (const x of [-3.32, -2.84]) {
        industrialBoxPlacements.push({
            family: "ladderRails",
            position: [x, .02, 1.91],
            scale: [.03, .54, .025],
            rotation: [0, 0, -.03],
            color: COLORS.ink
        });
    }
    const industrialBoxes = buildColoredInstanceFamily(THREE, {
        name: "ReferenceIndustrialBoxDetails",
        geometry: resources.geometry.box,
        material: palette.village,
        placements: industrialBoxPlacements
    });
    group.add(industrialRings, industrialBoxes);

    return group;
}

function buildUtilityCluster(THREE, resources, palette) {
    const group = new THREE.Group();
    group.name = "ReferenceUtilityCluster";

    addOutlinedMesh(
        THREE,
        group,
        resources.geometry.box,
        palette.architecture,
        resources.outline,
        {
            position: [3.02, -.9, 1.34],
            rotation: [0, .12, -.04],
            scale: [1.06, 1.12, .82],
            name: "ReferenceUtilityHouse"
        }
    );
    addOutlinedMesh(
        THREE,
        group,
        resources.geometry.cone,
        palette.coralShade,
        null,
        {
            position: [3.02, -.22, 1.34],
            rotation: [0, 0, .08],
            scale: [.82, .46, .72],
            outlined: false,
            name: "ReferenceUtilityRoof"
        }
    );
    addOutlinedMesh(
        THREE,
        group,
        resources.geometry.box,
        palette.architecture,
        null,
        {
            position: [4.08, -1.72, .72],
            rotation: [0, -.2, -.08],
            scale: [.84, .74, .72],
            outlined: false,
            name: "ReferenceOuterUtilityHut"
        }
    );
    addOutlinedMesh(
        THREE,
        group,
        resources.geometry.cylinder,
        palette.ink,
        null,
        {
            position: [3.7, 1.08, 1.22],
            scale: [.055, 1.62, .055],
            outlined: false,
            name: "ReferenceUtilityMast"
        }
    );
    addOutlinedMesh(
        THREE,
        group,
        resources.geometry.box,
        palette.ink,
        null,
        {
            position: [3.7, 1.78, 1.22],
            rotation: [0, 0, -.05],
            scale: [.92, .055, .055],
            outlined: false,
            name: "ReferenceUtilityCrossbar"
        }
    );
    addOutlinedMesh(
        THREE,
        group,
        resources.geometry.dish,
        palette.paper,
        null,
        {
            position: [3.18, .92, 1.48],
            rotation: [Math.PI / 2, 0, -.48],
            scale: [.58, .58, .58],
            outlined: false,
            name: "ReferenceUtilityDish"
        }
    );

    const utilityRotor = new THREE.Group();
    utilityRotor.name = "ReferenceUtilityRotor";
    utilityRotor.position.set(3.72, 2.35, 1.28);
    const rotorBlades = new THREE.InstancedMesh(
        resources.geometry.box,
        palette.paper,
        4
    );
    rotorBlades.name = "ReferenceUtilityRotorBlades";
    const matrix = new THREE.Matrix4();
    for (let index = 0; index < rotorBlades.count; index += 1) {
        const angle = index * Math.PI / 2;
        orientInstance(
            THREE,
            matrix,
            [Math.cos(angle) * .25, Math.sin(angle) * .25, 0],
            [.42, .07, .045],
            [0, 0, angle]
        );
        rotorBlades.setMatrixAt(index, matrix);
    }
    markInstancesDirty(rotorBlades);
    utilityRotor.add(rotorBlades);
    addOutlinedMesh(
        THREE,
        utilityRotor,
        resources.geometry.cylinder,
        palette.gold,
        null,
        {
            rotation: [Math.PI / 2, 0, 0],
            scale: [.1, .09, .1],
            outlined: false,
            name: "ReferenceUtilityRotorHub"
        }
    );
    group.add(utilityRotor);

    const utilityBoxes = buildColoredInstanceFamily(THREE, {
        name: "ReferenceUtilityBoxDetails",
        geometry: resources.geometry.box,
        material: palette.village,
        placements: [
            {
                family: "mastFeet",
                position: [3.52, -.48, 1.22],
                scale: [.18, .12, .18],
                rotation: [0, .12, 0],
                color: COLORS.rock
            },
            {
                family: "mastFeet",
                position: [3.88, -.48, 1.22],
                scale: [.18, .12, .18],
                rotation: [0, -.12, 0],
                color: COLORS.rock
            },
            {
                family: "dishBracket",
                position: [3.36, .72, 1.38],
                scale: [.42, .055, .055],
                rotation: [0, 0, -.48],
                color: COLORS.ink
            },
            {
                family: "dishBracket",
                position: [3.16, .58, 1.4],
                scale: [.055, .34, .055],
                rotation: [0, 0, -.48],
                color: COLORS.gold
            },
            ...Array.from({ length: 4 }, (_, index) => {
                const angle = index * Math.PI / 2;
                return {
                    family: "rotorRing",
                    position: [
                        3.72 + Math.cos(angle) * .42,
                        2.35 + Math.sin(angle) * .42,
                        1.28
                    ],
                    scale: [.16, .045, .045],
                    rotation: [0, 0, angle],
                    color: index % 2 === 0 ? COLORS.gold : COLORS.paper
                };
            }),
            ...Array.from({ length: 4 }, (_, index) => ({
                family: "cableSupports",
                position: [
                    3.16 + index * .34,
                    1.36 + (index % 2) * .18,
                    1.26
                ],
                scale: [.045, .3, .045],
                rotation: [0, 0, index % 2 === 0 ? -.18 : .18],
                color: COLORS.ink
            })),
            {
                family: "windowFrames",
                position: [2.73, -.74, 2.19],
                scale: [.34, .035, .025],
                rotation: [0, .12, -.04],
                color: COLORS.waterDeep
            },
            {
                family: "windowFrames",
                position: [2.73, -1.06, 2.19],
                scale: [.34, .035, .025],
                rotation: [0, .12, -.04],
                color: COLORS.waterDeep
            },
            {
                family: "windowFrames",
                position: [2.42, -.9, 2.19],
                scale: [.035, .2, .025],
                rotation: [0, .12, -.04],
                color: COLORS.ink
            },
            {
                family: "windowFrames",
                position: [3.04, -.9, 2.19],
                scale: [.035, .2, .025],
                rotation: [0, .12, -.04],
                color: COLORS.ink
            },
            ...Array.from({ length: 5 }, (_, index) => ({
                family: "ladderRungs",
                position: [4.16, -1.96 + index * .16, 1.47],
                scale: [.22, .025, .025],
                rotation: [0, -.2, -.08],
                color: index % 2 === 0 ? COLORS.gold : COLORS.paper
            })),
            ...Array.from({ length: 6 }, (_, index) => ({
                family: "connectorBlocks",
                position: [
                    3.38 + (index % 3) * .2,
                    1.12 + Math.floor(index / 3) * .27,
                    1.31
                ],
                scale: [.075, .075, .065],
                rotation: [0, 0, index % 2 === 0 ? -.1 : .1],
                color: index % 2 === 0 ? COLORS.gold : COLORS.paper
            })),
            ...Array.from({ length: 4 }, (_, index) => ({
                family: "roofBrackets",
                position: [
                    2.48 + index * .36,
                    -.26,
                    1.78 + (index % 2) * .08
                ],
                scale: [.045, .16, .055],
                rotation: [0, .12, index % 2 === 0 ? -.14 : .14],
                color: index % 2 === 0 ? COLORS.coral : COLORS.ink
            }))
        ]
    });
    group.add(utilityBoxes);

    return { group, utilityRotor };
}

function buildVillageClusters(THREE, resources, palette) {
    const clusters = new Map();
    const clusterNames = new Map([
        ["mountain-village", "ReferenceMountainVillage"],
        ["forest-hamlet", "ReferenceForestHamlet"],
        ["backside-houses", "ReferenceBacksideVillage"],
        ["water-works", "ReferenceWaterVillage"]
    ]);
    clusterNames.forEach((name, clusterId) => {
        const cluster = new THREE.Group();
        cluster.name = name;
        cluster.userData.villageCluster = clusterId;
        clusters.set(clusterId, cluster);
    });

    const buildings = new THREE.InstancedMesh(
        resources.geometry.box,
        palette.village,
        VILLAGE_PLACEMENTS.length * 3
    );
    buildings.name = "ReferenceVillageBuildingFamily";
    const outlines = new THREE.InstancedMesh(
        resources.geometry.villageOutlineBox,
        resources.villageOutline,
        VILLAGE_PLACEMENTS.length * 3
    );
    outlines.name = "ReferenceVillageOutlineFamily";
    // The panorama already carries its own hand-drawn ink. Rendering every
    // backing instance stacks transparent silhouettes into an opaque black
    // ring at the live hero scale, so retain the authored transforms for
    // diagnostics while keeping the duplicate backing pass out of the scene.
    outlines.visible = false;
    const matrix = new THREE.Matrix4();
    const architectureColor = new THREE.Color(COLORS.architecture);
    const roofColor = new THREE.Color(COLORS.coralShade);
    const facadePlacements = [];
    VILLAGE_PLACEMENTS.forEach((placement, placementIndex) => {
        const instanceIndex = placementIndex * 3;
        orientSurfaceInstance(
            THREE,
            matrix,
            placement.position,
            placement.scale,
            placement.rotation
        );
        buildings.setMatrixAt(instanceIndex, matrix);
        outlines.setMatrixAt(instanceIndex, matrix);
        buildings.setColorAt(instanceIndex, architectureColor);

        const normal = new THREE.Vector3(...placement.position).normalize();
        const tangent = new THREE.Vector3(
            -normal.z,
            0,
            normal.x
        ).normalize();
        for (const side of [-1, 1]) {
            const roofPosition = new THREE.Vector3(...placement.position)
                .addScaledVector(normal, placement.scale[1] * .72)
                .addScaledVector(
                    tangent,
                    side * placement.scale[0] * .28
                )
                .toArray();
            orientSurfaceInstance(
                THREE,
                matrix,
                roofPosition,
                [
                    placement.scale[0] * .7,
                    placement.scale[1] * .13,
                    placement.scale[2] * 1.18
                ],
                [
                    placement.rotation[0],
                    placement.rotation[1],
                    placement.rotation[2] + side * .42
                ]
            );
            buildings.setMatrixAt(
                instanceIndex + (side === -1 ? 1 : 2),
                matrix
            );
            outlines.setMatrixAt(
                instanceIndex + (side === -1 ? 1 : 2),
                matrix
            );
            buildings.setColorAt(
                instanceIndex + (side === -1 ? 1 : 2),
                roofColor
            );
        }

        const binormal = new THREE.Vector3()
            .crossVectors(normal, tangent)
            .normalize();
        const facadeCenter = new THREE.Vector3(...placement.position)
            .addScaledVector(tangent, placement.scale[2] * .53);
        for (const side of [-1, 1]) {
            facadePlacements.push({
                family: "windows",
                position: facadeCenter.clone()
                    .addScaledVector(normal, placement.scale[1] * .12)
                    .addScaledVector(
                        binormal,
                        side * placement.scale[0] * .2
                    )
                    .toArray(),
                scale: [
                    placement.scale[0] * .16,
                    placement.scale[1] * .2,
                    .025
                ],
                rotation: placement.rotation,
                color: placementIndex % 3 === 0
                    ? COLORS.waterDeep
                    : COLORS.ink,
                surface: true
            });
            facadePlacements.push({
                family: "windowSills",
                position: facadeCenter.clone()
                    .addScaledVector(
                        normal,
                        placement.scale[1] * .02
                    )
                    .addScaledVector(
                        binormal,
                        side * placement.scale[0] * .2
                    )
                    .toArray(),
                scale: [
                    placement.scale[0] * .21,
                    .025,
                    .045
                ],
                rotation: placement.rotation,
                color: placementIndex % 2 === 0
                    ? COLORS.paper
                    : COLORS.gold,
                surface: true
            });
            facadePlacements.push({
                family: "windowCrossbars",
                position: facadeCenter.clone()
                    .addScaledVector(normal, placement.scale[1] * .12)
                    .addScaledVector(
                        binormal,
                        side * placement.scale[0] * .2
                    )
                    .toArray(),
                scale: [
                    .025,
                    placement.scale[1] * .18,
                    .032
                ],
                rotation: placement.rotation,
                color: placementIndex % 3 === 0
                    ? COLORS.paper
                    : COLORS.gold,
                surface: true
            });
            facadePlacements.push({
                family: "canopySupports",
                position: facadeCenter.clone()
                    .addScaledVector(normal, placement.scale[1] * .04)
                    .addScaledVector(
                        binormal,
                        side * placement.scale[0] * .14
                    )
                    .toArray(),
                scale: [.025, placement.scale[1] * .17, .025],
                rotation: placement.rotation,
                color: COLORS.ink,
                surface: true
            });
        }
        facadePlacements.push(
            {
                family: "doors",
                position: facadeCenter.clone()
                    .addScaledVector(normal, -placement.scale[1] * .14)
                    .toArray(),
                scale: [
                    placement.scale[0] * .19,
                    placement.scale[1] * .3,
                    .03
                ],
                rotation: placement.rotation,
                color: placementIndex % 2 === 0
                    ? COLORS.coralShade
                    : COLORS.rock,
                surface: true
            },
            {
                family: "roofTrim",
                position: new THREE.Vector3(...placement.position)
                    .addScaledVector(normal, placement.scale[1] * .78)
                    .toArray(),
                scale: [
                    placement.scale[0] * .82,
                    .035,
                    placement.scale[2] * .16
                ],
                rotation: placement.rotation,
                color: placementIndex % 4 === 0
                    ? COLORS.gold
                    : COLORS.coral,
                surface: true
            },
            {
                family: "doorCanopies",
                position: facadeCenter.clone()
                    .addScaledVector(normal, placement.scale[1] * .08)
                    .toArray(),
                scale: [
                    placement.scale[0] * .3,
                    .035,
                    placement.scale[2] * .18
                ],
                rotation: placement.rotation,
                color: placementIndex % 3 === 0
                    ? COLORS.coral
                    : COLORS.paper,
                surface: true
            }
        );
        for (const side of [-1, 1]) {
            facadePlacements.push({
                family: "wallPosts",
                position: facadeCenter.clone()
                    .addScaledVector(
                        binormal,
                        side * placement.scale[0] * .42
                    )
                    .toArray(),
                scale: [
                    .035,
                    placement.scale[1] * .47,
                    .045
                ],
                rotation: placement.rotation,
                color: side === -1 ? COLORS.ink : COLORS.rock,
                surface: true
            });
            facadePlacements.push({
                family: "doorFrames",
                position: facadeCenter.clone()
                    .addScaledVector(
                        binormal,
                        side * placement.scale[0] * .21
                    )
                    .addScaledVector(
                        normal,
                        -placement.scale[1] * .13
                    )
                    .toArray(),
                scale: [
                    .028,
                    placement.scale[1] * .32,
                    .04
                ],
                rotation: placement.rotation,
                color: COLORS.paper,
                surface: true
            });
        }
        facadePlacements.push(
            {
                family: "doorFrames",
                position: facadeCenter.clone()
                    .addScaledVector(normal, placement.scale[1] * .16)
                    .toArray(),
                scale: [
                    placement.scale[0] * .24,
                    .028,
                    .04
                ],
                rotation: placement.rotation,
                color: COLORS.paper,
                surface: true
            },
            {
                family: "eaveBands",
                position: facadeCenter.clone()
                    .addScaledVector(normal, placement.scale[1] * .43)
                    .toArray(),
                scale: [
                    placement.scale[0] * .5,
                    .035,
                    placement.scale[2] * .11
                ],
                rotation: placement.rotation,
                color: placementIndex % 2 === 0
                    ? COLORS.coral
                    : COLORS.gold,
                surface: true
            },
            {
                family: "roofRidges",
                position: new THREE.Vector3(...placement.position)
                    .addScaledVector(normal, placement.scale[1] * .88)
                    .toArray(),
                scale: [
                    placement.scale[0] * .46,
                    .045,
                    placement.scale[2] * .13
                ],
                rotation: placement.rotation,
                color: placementIndex % 3 === 0
                    ? COLORS.gold
                    : COLORS.ink,
                surface: true
            },
            {
                family: "entrySteps",
                position: facadeCenter.clone()
                    .addScaledVector(
                        normal,
                        -placement.scale[1] * .48
                    )
                    .toArray(),
                scale: [
                    placement.scale[0] * .28,
                    .04,
                    placement.scale[2] * .18
                ],
                rotation: placement.rotation,
                color: placementIndex % 2 === 0
                    ? COLORS.rock
                    : COLORS.groundShade,
                surface: true
            }
        );
    });
    markInstancesDirty(buildings);
    markInstancesDirty(outlines);
    tagSurfaceInstances(buildings, VILLAGE_PLACEMENTS);
    const facades = buildColoredInstanceFamily(THREE, {
        name: "ReferenceVillageFacadeDetails",
        geometry: resources.geometry.box,
        material: palette.village,
        placements: facadePlacements
    });
    return { clusters, buildings, outlines, facades };
}

function buildSurfaceUtilityFamilies(THREE, resources, palette) {
    const group = new THREE.Group();
    group.name = "ReferenceSurfaceUtilityFamilies";
    const matrix = new THREE.Matrix4();
    const families = [
        {
            name: "ReferenceCableSegmentFamily",
            geometry: resources.geometry.cylinder,
            material: palette.ink,
            placements: CABLE_SEGMENT_PLACEMENTS
        },
        {
            name: "ReferencePathFrameFamily",
            geometry: resources.geometry.box,
            material: palette.rock,
            placements: PATH_FRAME_PLACEMENTS
        },
        {
            name: "ReferencePipePoleFamily",
            geometry: resources.geometry.cylinder,
            material: palette.coralShade,
            placements: PIPE_POLE_PLACEMENTS
        }
    ];
    families.forEach((family) => {
        const mesh = new THREE.InstancedMesh(
            family.geometry,
            family.material,
            family.placements.length
        );
        mesh.name = family.name;
        family.placements.forEach((placement, index) => {
            orientSurfaceInstance(
                THREE,
                matrix,
                placement.position,
                placement.scale,
                placement.rotation
            );
            mesh.setMatrixAt(index, matrix);
        });
        markInstancesDirty(mesh);
        tagSurfaceInstances(mesh, family.placements);
        group.add(mesh);
    });
    return group;
}

function buildLowerCanopy(THREE, resources, palette, placements) {
    const group = new THREE.Group();
    group.name = "ReferenceLowerCanopy";
    const canopyCount = placements.length;
    const matrix = new THREE.Matrix4();

    const trunks = new THREE.InstancedMesh(
        resources.geometry.cylinder,
        palette.ink,
        canopyCount
    );
    trunks.name = "ReferenceCanopyTrunks";
    const crowns = new THREE.InstancedMesh(
        resources.geometry.sphere,
        palette.canopy,
        canopyCount
    );
    crowns.name = "ReferenceCanopyCrowns";
    const canopyColors = [
        new THREE.Color(COLORS.foliage),
        new THREE.Color(COLORS.foliageDark),
        new THREE.Color(COLORS.groundShade)
    ];

    for (let index = 0; index < canopyCount; index += 1) {
        const placement = placements[index];
        const normal = new THREE.Vector3(...placement.position).normalize();
        const trunkPosition = new THREE.Vector3(...placement.position)
            .addScaledVector(normal, .16)
            .toArray();
        orientSurfaceInstance(
            THREE,
            matrix,
            trunkPosition,
            [.07, .62 + (index % 3) * .08, .07],
            placement.rotation
        );
        trunks.setMatrixAt(index, matrix);

        const crown = canopyCrownTransform(THREE, placement, index);
        orientSurfaceInstance(
            THREE,
            matrix,
            crown.position,
            crown.scale,
            crown.rotation
        );
        crowns.setMatrixAt(index, matrix);
        crowns.setColorAt(index, canopyColors[index % canopyColors.length]);
    }
    markInstancesDirty(trunks);
    markInstancesDirty(crowns);
    tagSurfaceInstances(trunks, placements);
    tagSurfaceInstances(crowns, placements);
    trunks.castShadow = false;
    crowns.castShadow = false;
    group.add(trunks, crowns);
    return group;
}

export function createQuantPlanetWorld(THREE, {
    profile = {},
    surfaceTexture = null,
    documentTarget = typeof document === "undefined" ? null : document,
    textureLoader = null
} = {}) {
    const geometries = new Set();
    const materials = new Set();
    const textures = new Set();
    const ownGeometry = (geometry) => (geometries.add(geometry), geometry);
    const ownMaterial = (material) => (materials.add(material), material);
    let disposed = false;

    function disposeTrackedResources() {
        if (disposed) {
            return;
        }
        disposed = true;
        [geometries, materials, textures].forEach((resources) => {
            resources.forEach((resource) => {
                try {
                    resource.dispose();
                } catch {
                    // Continue disposing independent resources during rollback.
                }
            });
            resources.clear();
        });
    }

    try {
        const gradientMap = createGradientMap(THREE);
        textures.add(gradientMap);
        const outline = profile.outlineLevel > 0
            ? ownMaterial(new THREE.MeshBasicMaterial({
                color: COLORS.ink,
                side: THREE.BackSide
            }))
            : null;
        const villageOutline = ownMaterial(new THREE.MeshBasicMaterial({
            color: COLORS.ink,
            side: THREE.BackSide,
            transparent: true,
            opacity: .08,
            depthWrite: false
        }));
        const resources = {
            ownGeometry,
            shadows: Boolean(profile.shadows),
            outline,
            heroOutline: profile.outlineLevel > 1 ? outline : null,
            villageOutline,
            geometry: {
                box: ownGeometry(new THREE.BoxGeometry(1, 1, 1, 1, 1, 1)),
                villageOutlineBox: ownGeometry(
                    new THREE.BoxGeometry(1.035, 1.035, 1.035, 1, 1, 1)
                ),
                cylinder: ownGeometry(
                    new THREE.CylinderGeometry(1, 1, 1, 8, 1)
                ),
                sphere: ownGeometry(new THREE.IcosahedronGeometry(1, 1)),
                detailedSphere: null,
                cone: ownGeometry(new THREE.ConeGeometry(1, 1, 7)),
                torus: ownGeometry(new THREE.TorusGeometry(1, .18, 5, 12)),
                dish: ownGeometry(
                    new THREE.SphereGeometry(
                        .65,
                        12,
                        5,
                        0,
                        Math.PI * 2,
                        0,
                        .55
                    )
                )
            }
        };
        const detailedSphere = ownGeometry(
            new THREE.IcosahedronGeometry(1, 2)
        );
        const detailedPositions = detailedSphere.attributes.position;
        const detailedVertex = new THREE.Vector3();
        for (let index = 0; index < detailedPositions.count; index += 1) {
            detailedVertex.fromBufferAttribute(detailedPositions, index)
                .normalize();
            const displacement = 1
                + Math.sin(
                    detailedVertex.x * 12.7
                    + detailedVertex.y * 8.3
                    + detailedVertex.z * 10.9
                ) * .045
                + Math.cos(
                    detailedVertex.x * 5.1
                    - detailedVertex.y * 9.7
                    + detailedVertex.z * 7.3
                ) * .022;
            detailedPositions.setXYZ(
                index,
                detailedVertex.x * displacement,
                detailedVertex.y * displacement,
                detailedVertex.z * displacement
            );
        }
        detailedPositions.needsUpdate = true;
        detailedSphere.computeVertexNormals();
        resources.geometry.detailedSphere = detailedSphere;
        const palette = {
            water: ownMaterial(createWaterMaterial(THREE)),
            ground: ownMaterial(
                createToonMaterial(THREE, COLORS.ground, gradientMap)
            ),
            groundShade: ownMaterial(new THREE.MeshBasicMaterial({
                color: 0xffffff,
                toneMapped: false
            })),
            architecture: ownMaterial(
                createToonMaterial(THREE, COLORS.architecture, gradientMap)
            ),
            village: ownMaterial(new THREE.MeshBasicMaterial({
                color: 0xffffff,
                toneMapped: false
            })),
            paper: ownMaterial(
                createToonMaterial(THREE, COLORS.paper, gradientMap)
            ),
            coral: ownMaterial(
                createToonMaterial(THREE, COLORS.coral, gradientMap)
            ),
            coralShade: ownMaterial(
                createToonMaterial(THREE, COLORS.coralShade, gradientMap)
            ),
            ink: ownMaterial(new THREE.MeshBasicMaterial({
                color: COLORS.ink,
                toneMapped: false
            })),
            canopy: ownMaterial(
                createToonMaterial(
                    THREE,
                    0xffffff,
                    gradientMap,
                    {
                        emissive: COLORS.foliageDark,
                        emissiveIntensity: .8
                    }
                )
            ),
            rock: ownMaterial(
                createToonMaterial(THREE, COLORS.rock, gradientMap)
            ),
            gold: ownMaterial(
                createToonMaterial(THREE, COLORS.gold, gradientMap)
            )
        };

        const planet = new THREE.Group();
        planet.name = "QuantPlanet";
        const content = new THREE.Group();
        content.name = "ReferencePlanetMiniature";
        const foliagePlacements = selectFoliagePlacements(profile);
        const core = buildPlanetCore(
            THREE,
            resources,
            palette,
            foliagePlacements
        );
        const body = core.getObjectByName("ReferencePlanetBody");
        const plainSurfaceMaterial = body.material;
        let browserTexture = null;
        let browserTextureFailed = false;
        if (!surfaceTexture && documentTarget) {
            browserTexture = loadBrowserSurfaceTexture(
                THREE,
                textureLoader,
                () => {
                    browserTextureFailed = true;
                    restorePlainSurface(body, plainSurfaceMaterial);
                }
            );
        }
        const resolvedSurfaceTexture = surfaceTexture
            ?? (browserTextureFailed ? null : browserTexture);
        if (resolvedSurfaceTexture) {
            textures.add(resolvedSurfaceTexture);
            applySurfaceTexture(
                THREE,
                body,
                resolvedSurfaceTexture,
                ownMaterial(new THREE.MeshBasicMaterial({
                    color: 0xffffff,
                    map: resolvedSurfaceTexture,
                    toneMapped: false
                }))
            );
        }
        const waterBand = buildWaterBand(THREE, resources, palette);
        const temple = buildTemplePeak(THREE, resources, palette);
        const industrial = buildIndustrialCluster(THREE, resources, palette);
        const utility = buildUtilityCluster(THREE, resources, palette);
        const villages = buildVillageClusters(THREE, resources, palette);
        const canopy = buildLowerCanopy(
            THREE,
            resources,
            palette,
            foliagePlacements
        );
        const surfaceUtilities = buildSurfaceUtilityFamilies(
            THREE,
            resources,
            palette
        );
        const surfaceDensity = buildSurfaceDensity([
            ...TERRAIN_PATCHES,
            ...VILLAGE_PLACEMENTS,
            ...foliagePlacements,
            ...CABLE_SEGMENT_PLACEMENTS,
            ...PATH_FRAME_PLACEMENTS,
            ...PIPE_POLE_PLACEMENTS
        ]);
        const districts = buildDistrictAnchors(THREE);
        temple.rotation.y = Math.PI / 2;
        temple.position.x = 1.55;
        industrial.rotation.y = -1.82;
        utility.group.rotation.y = -1.22;
        content.add(
            core,
            waterBand.group,
            temple,
            industrial,
            utility.group,
            villages.buildings,
            villages.outlines,
            villages.facades,
            ...villages.clusters.values(),
            canopy,
            surfaceUtilities,
            districts.group
        );
        content.updateMatrixWorld(true);
        districts.byId.get("summit-shrine").attach(temple);
        districts.byId.get("mountain-village").attach(
            villages.clusters.get("mountain-village")
        );
        districts.byId.get("utility-ridge").attach(utility.group);
        districts.byId.get("forest-hamlet").attach(
            villages.clusters.get("forest-hamlet")
        );
        districts.byId.get("root-gardens").attach(canopy);
        districts.byId.get("backside-houses").attach(
            villages.clusters.get("backside-houses")
        );
        districts.byId.get("industrial-yard").attach(industrial);
        const waterDistrict = districts.byId.get("water-works");
        waterDistrict.attach(waterBand.group);
        waterDistrict.attach(villages.clusters.get("water-works"));
        planet.add(content);

        const framingBox = new THREE.Box3();
        const framingPoint = new THREE.Vector3();
        const framingInstanceMatrix = new THREE.Matrix4();
        const framingWorldMatrix = new THREE.Matrix4();
        function forEachFramingPoint(visitor) {
            planet.updateMatrixWorld(true);
            content.traverse((object) => {
                if (!object.isMesh || object.visible === false) {
                    return;
                }
                const positions = object.geometry.attributes.position;
                const includeGeometry = (matrix) => {
                    for (let index = 0; index < positions.count; index += 1) {
                        framingPoint.fromBufferAttribute(positions, index)
                            .applyMatrix4(matrix);
                        visitor(framingPoint, object);
                    }
                };
                if (object.isInstancedMesh) {
                    for (let index = 0; index < object.count; index += 1) {
                        object.getMatrixAt(index, framingInstanceMatrix);
                        framingWorldMatrix.multiplyMatrices(
                            object.matrixWorld,
                            framingInstanceMatrix
                        );
                        includeGeometry(framingWorldMatrix);
                    }
                    return;
                }
                includeGeometry(object.matrixWorld);
            });
        }

        forEachFramingPoint((point) => framingBox.expandByPoint(point));
        const authoredCenter = framingBox.getCenter(new THREE.Vector3());
        content.position.copy(authoredCenter).multiplyScalar(-1);
        framingBox.makeEmpty();
        forEachFramingPoint((point) => framingBox.expandByPoint(point));
        const framingCenter = framingBox.getCenter(new THREE.Vector3());
        let framingRadiusSquared = 0;
        forEachFramingPoint((point) => {
            framingRadiusSquared = Math.max(
                framingRadiusSquared,
                point.distanceToSquared(framingCenter)
            );
        });
        // Keep the camera composition stable after removing the old detached
        // debris ring. The reference framing radius is an art-direction
        // boundary, while min/max continue to report measured structure.
        const framingRadius = Math.max(
            6.7,
            Math.sqrt(framingRadiusSquared)
        );

        function getFramingEnvelope() {
            return {
                center: {
                    x: framingCenter.x,
                    y: framingCenter.y,
                    z: framingCenter.z
                },
                radius: framingRadius,
                min: {
                    x: framingBox.min.x,
                    y: framingBox.min.y,
                    z: framingBox.min.z
                },
                max: {
                    x: framingBox.max.x,
                    y: framingBox.max.y,
                    z: framingBox.max.z
                },
                top: framingBox.max.y,
                bottom: framingBox.min.y
            };
        }

        function getRenderStats() {
            const stats = {
                drawCalls: 0,
                shadowCalls: 0,
                triangles: 0
            };
            planet.traverse((object) => {
                if (!object.isMesh || object.visible === false) {
                    return;
                }
                stats.drawCalls += 1;
                stats.shadowCalls += object.castShadow ? 1 : 0;
                const primitives = object.geometry.index
                    ? object.geometry.index.count
                    : object.geometry.attributes.position.count;
                const instances = object.isInstancedMesh ? object.count : 1;
                stats.triangles += primitives / 3 * instances;
            });
            return stats;
        }

        function update(pose = {}) {
            if (disposed) {
                return;
            }
            const worldAngle = Number.isFinite(pose.worldAngle)
                ? pose.worldAngle
                : 0;
            const elapsedMs = Number.isFinite(pose.elapsedMs)
                ? pose.elapsedMs
                : 0;
            planet.rotation.set(0, worldAngle, 0);
            waterBand.water.material.uniforms.time.value = pose.reduceMotion
                ? 0
                : elapsedMs / 1000;
            utility.utilityRotor.rotation.z = pose.reduceMotion
                ? 0
                : elapsedMs * .00035;
        }

        function dispose() {
            disposeTrackedResources();
        }

        return {
            group: planet,
            update,
            getFramingEnvelope,
            forEachFramingPoint,
            getRenderStats,
            getSurfaceDensity: () => surfaceDensity,
            dispose
        };
    } catch (error) {
        disposeTrackedResources();
        throw error;
    }
}
