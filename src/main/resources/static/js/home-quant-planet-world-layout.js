function frozenVector(values) {
    return Object.freeze([...values]);
}

function frozenPlacement({
    id,
    districtId,
    clusterId,
    category,
    family,
    sectorIndex,
    position,
    scale,
    rotation
}) {
    return Object.freeze({
        id,
        ...(districtId ? { districtId } : {}),
        ...(clusterId ? { clusterId } : {}),
        ...(category ? { category } : {}),
        ...(family ? { family } : {}),
        ...(Number.isInteger(sectorIndex) ? { sectorIndex } : {}),
        position: frozenVector(position),
        scale: frozenVector(scale),
        rotation: frozenVector(rotation)
    });
}

export const WORLD_DISTRICTS = Object.freeze([
    ["summit-shrine", 0, .86, "temple"],
    ["mountain-village", 45, .55, "village"],
    ["utility-ridge", 90, .24, "utility"],
    ["forest-hamlet", 135, -.16, "forest"],
    ["root-gardens", 180, -.58, "forest"],
    ["backside-houses", 225, -.12, "village"],
    ["industrial-yard", 270, .18, "industrial"],
    ["water-works", 315, .48, "water"]
].map(([id, yaw, latitude, type]) => Object.freeze({
    id,
    yawDegrees: yaw,
    latitude,
    type
})));

function normalizedYawDegrees(position) {
    const yaw = Math.atan2(position[2], position[0]) * 180 / Math.PI;
    return (yaw + 360) % 360;
}

function sectorIndexForPosition(position) {
    return Math.floor(
        (normalizedYawDegrees(position) + 22.5) / 45
    ) % WORLD_DISTRICTS.length;
}

function pointOnSurface(yawDegrees, latitude, radius) {
    const yaw = yawDegrees * Math.PI / 180;
    const horizontal = Math.cos(latitude);
    return [
        Math.cos(yaw) * horizontal * radius,
        Math.sin(latitude) * radius,
        Math.sin(yaw) * horizontal * radius
    ];
}

function tagSurfacePlacement(placement, category, family) {
    const sectorIndex = sectorIndexForPosition(placement.position);
    return frozenPlacement({
        ...placement,
        districtId: placement.districtId
            ?? WORLD_DISTRICTS[sectorIndex].id,
        category,
        family,
        sectorIndex
    });
}

function generatedPlacements({
    prefix,
    category,
    family,
    yawOffsets,
    latitudes,
    radius,
    scaleForIndex,
    rotationForIndex,
    clusterByDistrict = false
}) {
    return WORLD_DISTRICTS.flatMap((district, sectorIndex) =>
        yawOffsets.map((offset, detailIndex) => frozenPlacement({
            id: `${prefix}-${sectorIndex}-${detailIndex}`,
            districtId: district.id,
            ...(clusterByDistrict ? { clusterId: district.id } : {}),
            category,
            family,
            sectorIndex,
            position: pointOnSurface(
                district.yawDegrees + offset,
                latitudes[detailIndex % latitudes.length],
                radius + (detailIndex % 2) * .04
            ),
            scale: scaleForIndex(detailIndex, sectorIndex),
            rotation: rotationForIndex(detailIndex, sectorIndex)
        }))
    );
}

const BASE_TERRAIN_PATCHES = Object.freeze([
    frozenPlacement({
        id: "summit-shoulder-a",
        districtId: "summit-shrine",
        position: [2.68, 2.08, .18],
        scale: [.92, .24, .72],
        rotation: [.08, .12, -.14]
    }),
    frozenPlacement({
        id: "summit-shoulder-b",
        districtId: "summit-shrine",
        position: [2.32, 2.54, -.62],
        scale: [.72, .18, .58],
        rotation: [-.06, -.18, .21]
    }),
    frozenPlacement({
        id: "mountain-shelf-a",
        districtId: "mountain-village",
        position: [2.22, 1.72, 1.92],
        scale: [1.08, .26, .76],
        rotation: [.14, -.08, .16]
    }),
    frozenPlacement({
        id: "mountain-shelf-b",
        districtId: "mountain-village",
        position: [1.64, 2.06, 2.3],
        scale: [.72, .22, .66],
        rotation: [-.12, .18, -.2]
    }),
    frozenPlacement({
        id: "utility-shelf-a",
        districtId: "utility-ridge",
        position: [.42, .86, 3.2],
        scale: [1.05, .22, .7],
        rotation: [.08, .2, -.12]
    }),
    frozenPlacement({
        id: "utility-shelf-b",
        districtId: "utility-ridge",
        position: [-.72, .54, 3.18],
        scale: [.78, .18, .62],
        rotation: [-.1, -.15, .18]
    }),
    frozenPlacement({
        id: "forest-shelf-a",
        districtId: "forest-hamlet",
        position: [-2.18, -.62, 2.5],
        scale: [1.12, .23, .74],
        rotation: [.14, .1, .2]
    }),
    frozenPlacement({
        id: "forest-shelf-b",
        districtId: "forest-hamlet",
        position: [-2.62, -.18, 1.96],
        scale: [.76, .2, .62],
        rotation: [-.08, -.18, -.12]
    }),
    frozenPlacement({
        id: "root-shelf-a",
        districtId: "root-gardens",
        position: [-2.7, -1.92, .38],
        scale: [1.04, .25, .72],
        rotation: [.12, .16, -.22]
    }),
    frozenPlacement({
        id: "root-shelf-b",
        districtId: "root-gardens",
        position: [-2.42, -2.16, -.62],
        scale: [.82, .2, .68],
        rotation: [-.14, -.12, .18]
    }),
    frozenPlacement({
        id: "backside-shelf-a",
        districtId: "backside-houses",
        position: [-2.28, -.46, -2.42],
        scale: [1.08, .24, .76],
        rotation: [.1, -.2, .15]
    }),
    frozenPlacement({
        id: "backside-shelf-b",
        districtId: "backside-houses",
        position: [-1.72, -.9, -2.7],
        scale: [.72, .18, .62],
        rotation: [-.12, .14, -.18]
    }),
    frozenPlacement({
        id: "industrial-shelf-a",
        districtId: "industrial-yard",
        position: [-.38, .62, -3.24],
        scale: [1.14, .25, .78],
        rotation: [.12, .18, -.16]
    }),
    frozenPlacement({
        id: "industrial-shelf-b",
        districtId: "industrial-yard",
        position: [.7, .9, -3.12],
        scale: [.76, .2, .66],
        rotation: [-.08, -.16, .2]
    }),
    frozenPlacement({
        id: "water-shelf-a",
        districtId: "water-works",
        position: [2.18, 1.52, -2.08],
        scale: [1.12, .2, .78],
        rotation: [.08, .14, -.2]
    }),
    frozenPlacement({
        id: "water-shelf-b",
        districtId: "water-works",
        position: [2.6, 1.12, -1.78],
        scale: [.78, .18, .64],
        rotation: [-.12, -.16, .14]
    }),
    frozenPlacement({
        id: "southern-cap-a",
        districtId: "root-gardens",
        position: [-.84, -3.14, .72],
        scale: [1.02, .22, .78],
        rotation: [.18, .08, -.1]
    }),
    frozenPlacement({
        id: "southern-cap-b",
        districtId: "root-gardens",
        position: [.92, -3.08, -.84],
        scale: [.96, .2, .72],
        rotation: [-.16, -.12, .18]
    })
]);

const DENSE_TERRAIN_PATCHES = generatedPlacements({
    prefix: "terrain-field",
    category: "terrain",
    family: "terrain",
    yawOffsets: [-18, -11, -4, 4, 11, 18],
    latitudes: [-.46, .5, -.12, .28, -.32, .08],
    radius: 3.52,
    scaleForIndex: (index) => [
        .58 + index % 3 * .07,
        .16 + index % 2 * .03,
        .46 + (index + 1) % 3 * .055
    ],
    rotationForIndex: (index, sectorIndex) => [
        (index % 2 ? -.08 : .08),
        sectorIndex * .07,
        (index - 2.5) * .055
    ]
});

export const TERRAIN_PATCHES = Object.freeze([
    ...BASE_TERRAIN_PATCHES.map((placement) =>
        tagSurfacePlacement(placement, "terrain", "terrain")
    ),
    ...DENSE_TERRAIN_PATCHES
]);

const BASE_VILLAGE_PLACEMENTS = Object.freeze([
    frozenPlacement({
        id: "mountain-house-a",
        clusterId: "mountain-village",
        position: [2.34, 1.82, 2.02],
        scale: [.34, .46, .32],
        rotation: [.04, -.18, -.13]
    }),
    frozenPlacement({
        id: "mountain-house-b",
        clusterId: "mountain-village",
        position: [1.86, 2.15, 2.26],
        scale: [.29, .4, .3],
        rotation: [-.05, .22, .16]
    }),
    frozenPlacement({
        id: "mountain-house-c",
        clusterId: "mountain-village",
        position: [2.62, 1.45, 1.78],
        scale: [.3, .38, .28],
        rotation: [.08, .12, -.19]
    }),
    frozenPlacement({
        id: "forest-house-a",
        clusterId: "forest-hamlet",
        position: [-2.34, -.34, 2.4],
        scale: [.34, .42, .3],
        rotation: [-.08, -.2, .17]
    }),
    frozenPlacement({
        id: "forest-house-b",
        clusterId: "forest-hamlet",
        position: [-2.72, -.72, 1.84],
        scale: [.3, .38, .28],
        rotation: [.06, .18, -.15]
    }),
    frozenPlacement({
        id: "forest-house-c",
        clusterId: "forest-hamlet",
        position: [-1.92, -.92, 2.54],
        scale: [.28, .36, .27],
        rotation: [-.04, -.12, .21]
    }),
    frozenPlacement({
        id: "backside-house-a",
        clusterId: "backside-houses",
        position: [-2.38, -.3, -2.3],
        scale: [.34, .44, .31],
        rotation: [.06, .24, -.18]
    }),
    frozenPlacement({
        id: "backside-house-b",
        clusterId: "backside-houses",
        position: [-1.84, -.74, -2.62],
        scale: [.3, .39, .28],
        rotation: [-.08, -.16, .2]
    }),
    frozenPlacement({
        id: "backside-house-c",
        clusterId: "backside-houses",
        position: [-2.66, -.86, -1.84],
        scale: [.28, .36, .27],
        rotation: [.05, .14, -.16]
    }),
    frozenPlacement({
        id: "water-house-a",
        clusterId: "water-works",
        position: [2.3, 1.46, -2.02],
        scale: [.34, .43, .3],
        rotation: [-.06, -.22, .18]
    }),
    frozenPlacement({
        id: "water-house-b",
        clusterId: "water-works",
        position: [2.68, 1.04, -1.68],
        scale: [.3, .38, .28],
        rotation: [.08, .16, -.2]
    }),
    frozenPlacement({
        id: "water-house-c",
        clusterId: "water-works",
        position: [1.86, 1.78, -2.26],
        scale: [.28, .36, .27],
        rotation: [-.05, -.14, .15]
    })
]);

const DENSE_VILLAGE_PLACEMENTS = generatedPlacements({
    prefix: "surface-house",
    category: "structure",
    family: "village",
    yawOffsets: [-14, 0, 14],
    latitudes: [-.3, .12, .42],
    radius: 3.62,
    scaleForIndex: (index, sectorIndex) => [
        .52 + (index + sectorIndex) % 3 * .04,
        .56 + index * .05,
        .48 + (index + 1) % 2 * .05
    ],
    rotationForIndex: (index, sectorIndex) => [
        index % 2 ? -.05 : .05,
        sectorIndex * .09,
        (index - 1) * .15
    ],
    clusterByDistrict: true
});

export const VILLAGE_PLACEMENTS = Object.freeze([
    ...BASE_VILLAGE_PLACEMENTS.map((placement) =>
        tagSurfacePlacement(placement, "structure", "village")
    ),
    ...DENSE_VILLAGE_PLACEMENTS
]);

const FOREST_PLACEMENTS = [
    [
        ["southern-a", [2.2, -2.46, .18], [.44, .52, .42], [.1, .14, -.08]],
        ["southern-b", [1.92, -2.48, 1.12], [.4, .48, .38], [-.08, -.2, .12]],
        ["southern-c", [1.08, -2.56, 1.9], [.48, .56, .44], [.12, .18, -.14]],
        ["southern-d", [.12, -2.62, 2.18], [.42, .5, .4], [-.1, -.12, .16]],
        ["southern-e", [-.94, -2.54, 1.98], [.46, .54, .42], [.08, .22, -.12]],
        ["southern-f", [-1.84, -2.48, 1.24], [.4, .48, .38], [-.12, -.16, .14]],
        ["southern-g", [-2.22, -2.44, .22], [.48, .56, .44], [.1, .18, -.16]],
        ["southern-h", [-1.92, -2.5, -.98], [.42, .5, .4], [-.08, -.2, .12]],
        ["southern-i", [-1.02, -2.58, -1.9], [.46, .54, .42], [.12, .14, -.14]],
        ["southern-j", [.08, -2.64, -2.18], [.4, .48, .38], [-.1, -.18, .16]],
        ["southern-k", [1.12, -2.56, -1.86], [.48, .56, .44], [.08, .2, -.12]],
        ["southern-l", [1.94, -2.48, -1.02], [.42, .5, .4], [-.12, -.14, .14]]
    ],
    [
        ["hamlet-a", [-2.9, -1.26, 1.08], [.42, .5, .4], [.08, .18, -.12]],
        ["hamlet-b", [-2.46, -1.38, 1.86], [.48, .56, .44], [-.1, -.16, .14]],
        ["hamlet-c", [-1.84, -1.54, 2.38], [.4, .48, .38], [.12, .2, -.16]],
        ["hamlet-d", [-2.98, -1.08, .16], [.46, .54, .42], [-.08, -.14, .12]],
        ["hamlet-e", [-2.82, -1.3, -.94], [.42, .5, .4], [.1, .18, -.14]],
        ["hamlet-f", [-2.32, -1.46, -1.82], [.48, .56, .44], [-.12, -.2, .16]],
        ["hamlet-g", [-1.56, -1.62, -2.5], [.4, .48, .38], [.08, .16, -.12]],
        ["hamlet-h", [-.66, -1.72, -2.78], [.46, .54, .42], [-.1, -.18, .14]],
        ["hamlet-i", [.42, -1.74, -2.82], [.42, .5, .4], [.12, .2, -.16]],
        ["hamlet-j", [1.4, -1.62, -2.56], [.48, .56, .44], [-.08, -.14, .12]],
        ["hamlet-k", [2.2, -1.46, -1.92], [.4, .48, .38], [.1, .18, -.14]]
    ],
    [
        ["root-a", [-1.42, -3.02, .12], [.48, .58, .44], [.12, .16, -.14]],
        ["root-b", [-1.08, -3.08, .82], [.42, .52, .4], [-.1, -.18, .12]],
        ["root-c", [-.48, -3.16, 1.24], [.46, .56, .42], [.08, .2, -.16]],
        ["root-d", [.28, -3.18, 1.3], [.4, .5, .38], [-.12, -.14, .14]],
        ["root-e", [1.02, -3.1, .92], [.48, .58, .44], [.1, .18, -.12]],
        ["root-f", [1.4, -3.04, .22], [.42, .52, .4], [-.08, -.2, .16]],
        ["root-g", [1.18, -3.08, -.72], [.46, .56, .42], [.12, .14, -.14]],
        ["root-h", [.52, -3.16, -1.22], [.4, .5, .38], [-.1, -.18, .12]],
        ["root-i", [-.26, -3.18, -1.3], [.48, .58, .44], [.08, .2, -.16]],
        ["root-j", [-.98, -3.1, -.9], [.42, .52, .4], [-.12, -.14, .14]],
        ["root-k", [-1.38, -3.04, -.28], [.46, .56, .42], [.1, .18, -.12]]
    ]
];

const BASE_FOREST_BELTS = Object.freeze([
    ["southern-canopy", "root-gardens", FOREST_PLACEMENTS[0]],
    ["reverse-hamlet", "forest-hamlet", FOREST_PLACEMENTS[1]],
    ["root-garden", "root-gardens", FOREST_PLACEMENTS[2]]
].map(([id, districtId, placements]) => Object.freeze({
    id,
    districtId,
    placements: Object.freeze(placements.map(
        ([placementId, position, scale, rotation]) => tagSurfacePlacement(
            frozenPlacement({
                id: placementId,
                districtId,
                position,
                scale,
                rotation
            }),
            "foliageRock",
            "canopy"
        )
    ))
})));

const DENSE_FOLIAGE_PLACEMENTS = generatedPlacements({
    prefix: "canopy-field",
    category: "foliageRock",
    family: "canopy",
    yawOffsets: [-17, -10, -3, 3, 10, 17],
    latitudes: [-.5, .46, -.18, .18, -.34, .32],
    radius: 3.48,
    scaleForIndex: (index, sectorIndex) => [
        .26 + (index + sectorIndex) % 3 * .035,
        .31 + index % 2 * .045,
        .25 + (index + 1) % 3 * .03
    ],
    rotationForIndex: (index, sectorIndex) => [
        index % 2 ? -.08 : .08,
        sectorIndex * .06,
        (index - 2.5) * .07
    ]
});

export const FOREST_BELTS = Object.freeze([
    ...BASE_FOREST_BELTS,
    Object.freeze({
        id: "continuous-canopy",
        districtId: "root-gardens",
        placements: Object.freeze(DENSE_FOLIAGE_PLACEMENTS)
    })
]);

const BASE_DEBRIS_PLACEMENTS = Object.freeze([
    frozenPlacement({
        id: "debris-00",
        position: [4.82, 3.84, .16],
        scale: [.22, .14, .18],
        rotation: [.1, .4, .22]
    }),
    frozenPlacement({
        id: "debris-01",
        position: [4.18, -3.72, 1.92],
        scale: [.18, .28, .16],
        rotation: [.4, .1, -.26]
    }),
    frozenPlacement({
        id: "debris-02",
        position: [3.36, 5.08, 3.42],
        scale: [.28, .18, .22],
        rotation: [.2, -.5, .3]
    }),
    frozenPlacement({
        id: "debris-03",
        position: [1.82, -4.96, 4.3],
        scale: [.22, .34, .2],
        rotation: [-.25, .2, -.18]
    }),
    frozenPlacement({
        id: "debris-04",
        position: [.14, 4.72, 4.84],
        scale: [.3, .16, .18],
        rotation: [.42, .25, -.12]
    }),
    frozenPlacement({
        id: "debris-05",
        position: [-1.9, -4.58, 4.36],
        scale: [.16, .3, .14],
        rotation: [.18, -.3, .42]
    }),
    frozenPlacement({
        id: "debris-06",
        position: [-3.48, 4.9, 3.28],
        scale: [.18, .12, .34],
        rotation: [-.2, .48, .1]
    }),
    frozenPlacement({
        id: "debris-07",
        position: [-4.34, -4.82, 1.78],
        scale: [.24, .14, .28],
        rotation: [.32, -.12, .27]
    }),
    frozenPlacement({
        id: "debris-08",
        position: [-4.86, 3.68, -.18],
        scale: [.14, .2, .18],
        rotation: [.18, .34, -.4]
    }),
    frozenPlacement({
        id: "debris-09",
        position: [-4.22, -3.9, -1.96],
        scale: [.19, .13, .23],
        rotation: [-.22, .18, .36]
    }),
    frozenPlacement({
        id: "debris-10",
        position: [-3.3, 5.02, -3.46],
        scale: [.25, .16, .2],
        rotation: [.3, -.25, .12]
    }),
    frozenPlacement({
        id: "debris-11",
        position: [-1.76, -5.08, -4.26],
        scale: [.18, .22, .15],
        rotation: [-.2, .38, -.34]
    }),
    frozenPlacement({
        id: "debris-12",
        position: [.18, 4.64, -4.88],
        scale: [.24, .15, .2],
        rotation: [.14, -.36, .28]
    }),
    frozenPlacement({
        id: "debris-13",
        position: [1.94, -4.7, -4.34],
        scale: [.2, .26, .16],
        rotation: [-.3, .22, -.18]
    }),
    frozenPlacement({
        id: "debris-14",
        position: [3.42, 4.86, -3.34],
        scale: [.16, .2, .3],
        rotation: [.26, -.18, .34]
    }),
    frozenPlacement({
        id: "debris-15",
        position: [4.3, -4.9, -1.84],
        scale: [.22, .16, .26],
        rotation: [-.18, .3, -.24]
    })
]);

export const DEBRIS_PLACEMENTS = Object.freeze(
    BASE_DEBRIS_PLACEMENTS.map((placement) =>
        tagSurfacePlacement(
            placement,
            "utilitySilhouette",
            "debris"
        )
    )
);

export const CABLE_SEGMENT_PLACEMENTS = Object.freeze(generatedPlacements({
    prefix: "cable-segment",
    category: "utilitySilhouette",
    family: "cable",
    yawOffsets: [-15, 15],
    latitudes: [-.2, .24],
    radius: 3.66,
    scaleForIndex: (index) => [
        .025,
        .48 + index * .08,
        .025
    ],
    rotationForIndex: (index, sectorIndex) => [
        0,
        sectorIndex * .08,
        index ? -.82 : .82
    ]
}));

export const PATH_FRAME_PLACEMENTS = Object.freeze(generatedPlacements({
    prefix: "path-frame",
    category: "utilitySilhouette",
    family: "pathFrame",
    yawOffsets: [-8, 8],
    latitudes: [-.4, .38],
    radius: 3.55,
    scaleForIndex: (index) => [
        .055,
        .34 + index * .06,
        .055
    ],
    rotationForIndex: (index, sectorIndex) => [
        index ? -.08 : .08,
        sectorIndex * .05,
        index ? -.62 : .62
    ]
}));

export const PIPE_POLE_PLACEMENTS = Object.freeze(generatedPlacements({
    prefix: "pipe-pole",
    category: "utilitySilhouette",
    family: "pipePole",
    yawOffsets: [0],
    latitudes: [0],
    radius: 3.62,
    scaleForIndex: (_, sectorIndex) => [
        .045 + sectorIndex % 2 * .008,
        .5 + sectorIndex % 3 * .05,
        .045 + sectorIndex % 2 * .008
    ],
    rotationForIndex: (_, sectorIndex) => [
        0,
        sectorIndex * .04,
        sectorIndex % 2 ? -.12 : .12
    ]
}));

function detailPlacement(id, districtId, position, scale, rotation) {
    return frozenPlacement({
        id,
        districtId,
        position,
        scale,
        rotation
    });
}

function surfaceOffset(position, normalDistance, tangentDistance = 0) {
    const length = Math.hypot(...position);
    const normal = position.map((axis) => axis / length);
    const tangentLength = Math.hypot(normal[0], normal[2]) || 1;
    const tangent = [
        -normal[2] / tangentLength,
        0,
        normal[0] / tangentLength
    ];
    return position.map((axis, index) =>
        axis
            + normal[index] * normalDistance
            + tangent[index] * tangentDistance
    );
}

function villageDetailsForDistrict(
    districtId,
    roofCount,
    chimneyCount
) {
    const placements = VILLAGE_PLACEMENTS.filter(
        (placement) => placement.clusterId === districtId
    );
    const roofCaps = placements.slice(0, roofCount).map(
        (placement, index) => detailPlacement(
            `roof-cap-${districtId}-${index}`,
            districtId,
            surfaceOffset(
                placement.position,
                placement.scale[1] * .94
            ),
            [
                placement.scale[0] * (.2 + index % 2 * .035),
                .075 + index % 3 * .012,
                placement.scale[2] * (.22 + index % 2 * .035)
            ],
            [
                placement.rotation[0],
                placement.rotation[1] + index * .045,
                placement.rotation[2] + (index % 2 ? -.08 : .08)
            ]
        )
    );
    const chimneys = placements.slice(-chimneyCount).map(
        (placement, index) => detailPlacement(
            `chimney-${districtId}-${index}`,
            districtId,
            surfaceOffset(
                placement.position,
                placement.scale[1] * .88,
                (index % 2 ? -.22 : .22) * placement.scale[0]
            ),
            [
                .045 + index % 2 * .012,
                .16 + index % 3 * .025,
                .045 + (index + 1) % 2 * .012
            ],
            [
                placement.rotation[0] + (index % 2 ? -.04 : .04),
                placement.rotation[1],
                placement.rotation[2]
            ]
        )
    );
    return { roofCaps, chimneys };
}

function terrainEdgesForDistrict(districtId, count) {
    return TERRAIN_PATCHES
        .filter((placement) => placement.districtId === districtId)
        .slice(0, count)
        .map((placement, index) => detailPlacement(
            `retaining-edge-${districtId}-${index}`,
            districtId,
            surfaceOffset(
                placement.position,
                .075,
                (index % 2 ? -.12 : .12)
            ),
            [
                placement.scale[0] * (.46 + index % 2 * .08),
                .035 + index % 3 * .008,
                placement.scale[2] * .13
            ],
            [
                placement.rotation[0],
                placement.rotation[1] + index * .07,
                placement.rotation[2] + (index % 2 ? -.05 : .05)
            ]
        ));
}

function canopyDetailsForDistrict(
    districtId,
    forkCount,
    stoneCount
) {
    const placements = FOREST_BELTS
        .flatMap((belt) => belt.placements)
        .filter((placement) => placement.districtId === districtId);
    const forks = placements.slice(0, forkCount).map(
        (placement, index) => detailPlacement(
            `canopy-fork-${districtId}-${index}`,
            districtId,
            surfaceOffset(
                placement.position,
                .38 + index % 3 * .035,
                (index % 2 ? -.08 : .08)
            ),
            [
                .035 + index % 2 * .009,
                .22 + index % 3 * .035,
                .035 + (index + 1) % 2 * .009
            ],
            [
                placement.rotation[0] + (index % 2 ? -.38 : .38),
                placement.rotation[1] + index * .06,
                placement.rotation[2] + (index % 3 - 1) * .14
            ]
        )
    );
    const stones = placements.slice(-stoneCount).map(
        (placement, index) => detailPlacement(
            `under-canopy-stone-${districtId}-${index}`,
            districtId,
            surfaceOffset(
                placement.position,
                .07,
                (index % 3 - 1) * .14
            ),
            [
                .12 + index % 3 * .025,
                .065 + index % 2 * .018,
                .1 + (index + 1) % 3 * .02
            ],
            [
                placement.rotation[0] + index * .025,
                placement.rotation[1] + index * .13,
                placement.rotation[2]
            ]
        )
    );
    return { forks, stones };
}

function mountainStrataForDistrict(districtId, yawDegrees, latitudes) {
    return latitudes.map((latitude, index) => detailPlacement(
        `mountain-strata-${districtId}-${index}`,
        districtId,
        pointOnSurface(
            yawDegrees + (index - 3.5) * 2.2,
            latitude,
            3.7 + index % 2 * .055
        ),
        [
            .24 + index % 3 * .055,
            .022 + index % 2 * .008,
            .1 + (index + 1) % 3 * .022
        ],
        [
            index % 2 ? -.06 : .06,
            index * .075,
            (index - 3.5) * .035
        ]
    ));
}

function waterFoamAccents() {
    const waterCenters = [
        [2.72, 1.34, -1.7],
        [.12, .72, 3.26],
        [-2.76, -.42, 1.8],
        [1.92, -1.56, -2.2]
    ];
    return waterCenters.flatMap((position, waterIndex) =>
        [-.42, -.21, 0, .21, .42].map((offset, index) =>
            detailPlacement(
                `water-foam-accent-${waterIndex}-${index}`,
                "water-works",
                surfaceOffset(position, .09, offset),
                [
                    .09 + (index + waterIndex) % 3 * .025,
                    .012 + index % 2 * .005,
                    .026 + (index + 1) % 2 * .008
                ],
                [
                    index % 2 ? -.025 : .025,
                    waterIndex * .11 + offset * .16,
                    (index - 2) * .035
                ]
            )
        )
    );
}

const mountainVillageDetails = villageDetailsForDistrict(
    "mountain-village",
    5,
    3
);
const forestHamletDetails = villageDetailsForDistrict(
    "forest-hamlet",
    5,
    3
);
const backsideHouseDetails = villageDetailsForDistrict(
    "backside-houses",
    4,
    3
);
const waterVillageDetails = villageDetailsForDistrict(
    "water-works",
    4,
    3
);
const forestCanopyDetails = canopyDetailsForDistrict(
    "forest-hamlet",
    9,
    8
);
const rootCanopyDetails = canopyDetailsForDistrict(
    "root-gardens",
    9,
    8
);

function frozenDistrictDetails(details) {
    return Object.freeze(Object.fromEntries(
        Object.entries(details).map(([family, placements]) => [
            family,
            Object.freeze(placements)
        ])
    ));
}

export const DETAIL_PLACEMENTS_BY_DISTRICT = Object.freeze({
    "summit-shrine": frozenDistrictDetails({
        ReferenceTempleBeamEnds: Array.from(
            { length: 10 },
            (_, index) => detailPlacement(
                `temple-beam-end-${index}`,
                "summit-shrine",
                [
                    -.98 + index % 5 * .47,
                    index < 5 ? 4.7 : 4.48,
                    index < 5 ? 2.08 : 2.1
                ],
                [
                    .055 + index % 2 * .012,
                    .1 + index % 3 * .018,
                    .07 + (index + 1) % 2 * .012
                ],
                [0, -.08, index % 2 ? -.12 : .12]
            )
        ),
        ReferenceMountainStrata: mountainStrataForDistrict(
            "summit-shrine",
            0,
            [.72, .76, .8, .84, .7, .75, .79, .83]
        ),
        ReferenceTerrainRetainingEdges: terrainEdgesForDistrict(
            "summit-shrine",
            2
        )
    }),
    "mountain-village": frozenDistrictDetails({
        ReferenceVillageRoofCaps: mountainVillageDetails.roofCaps,
        ReferenceVillageChimneys: mountainVillageDetails.chimneys,
        ReferenceMountainStrata: mountainStrataForDistrict(
            "mountain-village",
            45,
            [.42, .48, .54, .58, .4, .46, .52, .56]
        ),
        ReferenceTerrainRetainingEdges: terrainEdgesForDistrict(
            "mountain-village",
            2
        )
    }),
    "utility-ridge": frozenDistrictDetails({
        ReferenceTerrainRetainingEdges: terrainEdgesForDistrict(
            "utility-ridge",
            2
        )
    }),
    "forest-hamlet": frozenDistrictDetails({
        ReferenceVillageRoofCaps: forestHamletDetails.roofCaps,
        ReferenceVillageChimneys: forestHamletDetails.chimneys,
        ReferenceCanopyForks: forestCanopyDetails.forks,
        ReferenceUnderCanopyStones: forestCanopyDetails.stones,
        ReferenceTerrainRetainingEdges: terrainEdgesForDistrict(
            "forest-hamlet",
            2
        )
    }),
    "root-gardens": frozenDistrictDetails({
        ReferenceCanopyForks: rootCanopyDetails.forks,
        ReferenceUnderCanopyStones: rootCanopyDetails.stones,
        ReferenceTerrainRetainingEdges: terrainEdgesForDistrict(
            "root-gardens",
            2
        )
    }),
    "backside-houses": frozenDistrictDetails({
        ReferenceVillageRoofCaps: backsideHouseDetails.roofCaps,
        ReferenceVillageChimneys: backsideHouseDetails.chimneys,
        ReferenceTerrainRetainingEdges: terrainEdgesForDistrict(
            "backside-houses",
            1
        )
    }),
    "industrial-yard": frozenDistrictDetails({
        ReferenceIndustrialBarrelBands: [
            ...Array.from({ length: 6 }, (_, index) => detailPlacement(
                `industrial-upper-band-${index}`,
                "industrial-yard",
                [-4.28 + index * .22, .62, 1.18],
                [
                    .035 + index % 2 * .008,
                    .68 + index % 2 * .035,
                    .045 + index % 3 * .008
                ],
                [index % 2 ? -.05 : .05, Math.PI / 2, index * .025]
            )),
            ...Array.from({ length: 6 }, (_, index) => detailPlacement(
                `industrial-lower-band-${index}`,
                "industrial-yard",
                [-3.9 + index * .17, -.78, 1.22],
                [
                    .032 + index % 2 * .007,
                    .56 + index % 3 * .025,
                    .042 + (index + 1) % 3 * .007
                ],
                [index % 2 ? .05 : -.05, Math.PI / 2, -index * .025]
            ))
        ],
        ReferenceIndustrialCableStays: Array.from(
            { length: 10 },
            (_, index) => detailPlacement(
                `industrial-cable-stay-${index}`,
                "industrial-yard",
                [
                    -4.68 + index % 5 * .48,
                    index < 5 ? .16 : -.98,
                    1.73 + index % 2 * .12
                ],
                [
                    .025 + index % 2 * .006,
                    .34 + index % 3 * .055,
                    .025 + (index + 1) % 2 * .006
                ],
                [
                    index % 2 ? -.1 : .1,
                    index * .04,
                    index < 5 ? -.42 : .42
                ]
            )
        ),
        ReferenceTerrainRetainingEdges: terrainEdgesForDistrict(
            "industrial-yard",
            1
        )
    }),
    "water-works": frozenDistrictDetails({
        ReferenceVillageRoofCaps: waterVillageDetails.roofCaps,
        ReferenceVillageChimneys: waterVillageDetails.chimneys,
        ReferenceWaterFoamAccents: waterFoamAccents(),
        ReferenceTerrainRetainingEdges: terrainEdgesForDistrict(
            "water-works",
            2
        )
    })
});
