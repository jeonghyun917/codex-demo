import {
    CABLE_SEGMENT_PLACEMENTS,
    DEBRIS_PLACEMENTS,
    FOREST_BELTS,
    PATH_FRAME_PLACEMENTS,
    PIPE_POLE_PLACEMENTS,
    TERRAIN_PATCHES,
    VILLAGE_PLACEMENTS,
    WORLD_DISTRICTS
} from "./home-quant-planet-world-layout.js";

const COLORS = Object.freeze({
    water: 0x78d7d0,
    waterDeep: 0x338f91,
    ground: 0xa8bf78,
    groundShade: 0x78965f,
    architecture: 0xf2e5c7,
    paper: 0xfffae9,
    coral: 0xd65f47,
    coralShade: 0xa94737,
    ink: 0x233a42,
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

function buildPlanetCore(THREE, resources, palette) {
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

    const bridges = new THREE.InstancedMesh(
        resources.geometry.box,
        palette.ink,
        4
    );
    bridges.name = "ReferenceWaterBridges";
    [
        [[2.66, 1.52, -1.72], [.58, .045, .12], [.04, .26, -.02]],
        [[.08, .9, 3.22], [.5, .04, .1], [-.03, -.22, .04]],
        [[-2.78, -.28, 1.88], [.46, .04, .1], [.05, .2, -.03]],
        [[1.92, -1.42, -2.3], [.42, .04, .09], [-.04, -.18, .03]]
    ].forEach(([position, scale, rotation], index) => {
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
    mainEngine.castShadow = Boolean(resources.shadows);
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
    const matrix = new THREE.Matrix4();
    const architectureColor = new THREE.Color(COLORS.architecture);
    const roofColor = new THREE.Color(COLORS.coralShade);
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
    });
    markInstancesDirty(buildings);
    markInstancesDirty(outlines);
    tagSurfaceInstances(buildings, VILLAGE_PLACEMENTS);
    return { clusters, buildings, outlines };
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

        const crownPosition = new THREE.Vector3(...placement.position)
            .addScaledVector(normal, .64 + (index % 3) * .07)
            .toArray();
        orientSurfaceInstance(
            THREE,
            matrix,
            crownPosition,
            placement.scale,
            placement.rotation
        );
        crowns.setMatrixAt(index, matrix);
        crowns.setColorAt(index, canopyColors[index % canopyColors.length]);
    }
    markInstancesDirty(trunks);
    markInstancesDirty(crowns);
    tagSurfaceInstances(trunks, placements);
    tagSurfaceInstances(crowns, placements);
    trunks.castShadow = Boolean(resources.shadows);
    crowns.castShadow = Boolean(resources.shadows);
    group.add(trunks, crowns);
    return group;
}

function buildFloatingDebris(THREE, resources, palette, placements) {
    const group = new THREE.Group();
    group.name = "ReferenceFloatingDebris";
    const debrisCount = placements.length;
    const matrix = new THREE.Matrix4();

    const pieces = new THREE.InstancedMesh(
        resources.geometry.box,
        palette.rock,
        debrisCount
    );
    pieces.name = "ReferenceDebrisPieces";
    const flecks = new THREE.InstancedMesh(
        resources.geometry.cone,
        palette.paper,
        debrisCount
    );
    flecks.name = "ReferenceDebrisFlecks";
    for (let index = 0; index < debrisCount; index += 1) {
        const placement = placements[index];
        orientInstance(
            THREE,
            matrix,
            placement.position,
            placement.scale,
            placement.rotation
        );
        pieces.setMatrixAt(index, matrix);
        orientInstance(
            THREE,
            matrix,
            [
                placement.position[0] + .16,
                placement.position[1] + .14,
                placement.position[2]
            ],
            [
                placement.scale[0] * .42,
                placement.scale[1] * .72,
                placement.scale[2] * .42
            ],
            [
                placement.rotation[0],
                placement.rotation[1],
                placement.rotation[2] + .8
            ]
        );
        flecks.setMatrixAt(index, matrix);
    }
    markInstancesDirty(pieces);
    markInstancesDirty(flecks);
    tagSurfaceInstances(pieces, placements);
    tagSurfaceInstances(flecks, placements);
    group.add(pieces, flecks);
    return group;
}

export function createQuantPlanetWorld(THREE, { profile = {} } = {}) {
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
            side: THREE.BackSide
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
                    new THREE.BoxGeometry(1.12, 1.12, 1.12, 1, 1, 1)
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
            groundShade: ownMaterial(
                createToonMaterial(THREE, 0xffffff, gradientMap, {
                    vertexColors: true
                })
            ),
            architecture: ownMaterial(
                createToonMaterial(THREE, COLORS.architecture, gradientMap)
            ),
            village: ownMaterial(
                createToonMaterial(
                    THREE,
                    COLORS.architecture,
                    gradientMap,
                    { vertexColors: true }
                )
            ),
            paper: ownMaterial(
                createToonMaterial(THREE, COLORS.paper, gradientMap)
            ),
            coral: ownMaterial(
                createToonMaterial(THREE, COLORS.coral, gradientMap)
            ),
            coralShade: ownMaterial(
                createToonMaterial(THREE, COLORS.coralShade, gradientMap)
            ),
            ink: ownMaterial(
                createToonMaterial(THREE, COLORS.ink, gradientMap)
            ),
            canopy: ownMaterial(
                createToonMaterial(
                    THREE,
                    0xffffff,
                    gradientMap,
                    {
                        vertexColors: true,
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
        const core = buildPlanetCore(THREE, resources, palette);
        const waterBand = buildWaterBand(THREE, resources, palette);
        const temple = buildTemplePeak(THREE, resources, palette);
        const industrial = buildIndustrialCluster(THREE, resources, palette);
        const utility = buildUtilityCluster(THREE, resources, palette);
        const villages = buildVillageClusters(THREE, resources, palette);
        const foliagePlacements = selectFoliagePlacements(profile);
        const canopy = buildLowerCanopy(
            THREE,
            resources,
            palette,
            foliagePlacements
        );
        const debrisCount = profile.name === "high"
            ? DEBRIS_PLACEMENTS.length
            : Math.max(0, Math.floor(profile.cloudCount ?? 8));
        const debrisPlacements = DEBRIS_PLACEMENTS.slice(0, debrisCount);
        const debris = buildFloatingDebris(
            THREE,
            resources,
            palette,
            debrisPlacements
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
            ...PIPE_POLE_PLACEMENTS,
            ...debrisPlacements
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
            ...villages.clusters.values(),
            canopy,
            debris,
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
            planet.traverse((object) => {
                if (!object.isMesh || object.visible === false) {
                    return;
                }
                const positions = object.geometry.attributes.position;
                const includeGeometry = (matrix) => {
                    for (let index = 0; index < positions.count; index += 1) {
                        framingPoint.fromBufferAttribute(positions, index)
                            .applyMatrix4(matrix);
                        visitor(framingPoint);
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
        const framingRadius = Math.sqrt(framingRadiusSquared);

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
            getRenderStats,
            getSurfaceDensity: () => surfaceDensity,
            dispose
        };
    } catch (error) {
        disposeTrackedResources();
        throw error;
    }
}
