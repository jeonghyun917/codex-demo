function frozenVector(values) {
    return Object.freeze([...values]);
}

function frozenPlacement(placement) {
    return Object.freeze({
        ...placement,
        scale: frozenVector(placement.scale),
        rotation: frozenVector(placement.rotation)
    });
}

export const REAR_FIELD_PERIODS_MS = Object.freeze({
    mid: 48000,
    far: 64000
});

export const REAR_FIELD_PROFILES = Object.freeze({
    high: Object.freeze({ name: "high", count: 42 }),
    balanced: Object.freeze({ name: "balanced", count: 30 }),
    low: Object.freeze({ name: "low", count: 18 })
});

const SIZE_SCALES = Object.freeze([
    [.08, .06, .05],
    [.13, .1, .08],
    [.19, .14, .11],
    [.28, .21, .16],
    [.43, .32, .24],
    [.68, .5, .38]
].map(frozenVector));

const FAMILIES = Object.freeze([
    "shard",
    "satellite",
    "beacon",
    "panel",
    "ring",
    "signal"
]);

const REAR_FIELD_SEEDS = Object.freeze([
    [0.12, .03, -.82], [.41, -.08, .47], [.76, .11, -.31],
    [1.04, -.13, .68], [1.39, .07, -.54], [1.78, -.04, .16],
    [2.06, .15, .83], [2.38, -.1, -.7], [2.71, .05, .26],
    [3.02, -.15, -.21], [3.36, .12, .61], [3.69, -.03, -.9],
    [4.01, .09, .39], [4.34, -.11, -.43], [4.66, .02, .74],
    [4.97, -.14, -.14], [5.28, .13, .52], [5.61, -.06, -.63],
    [5.94, .08, .08], [.24, -.12, .92], [.58, .1, -.36],
    [.91, -.05, .57], [1.23, .14, -.76], [1.56, -.09, .22],
    [1.9, .04, .7], [2.21, -.15, -.48], [2.55, .12, .12],
    [2.87, -.02, -.88], [3.19, .1, .44], [3.53, -.11, -.27],
    [3.86, .06, .8], [4.18, -.13, -.59], [4.5, .03, .32],
    [4.82, -.08, -.08], [5.15, .15, .65], [5.47, -.04, -.79],
    [5.8, .11, .18], [6.09, -.1, -.51], [.33, .05, .86],
    [.67, -.14, -.18], [1.11, .08, .55], [2.46, -.07, -.66]
].map(frozenVector));

function placementFromSeed(seed, index) {
    const [phase, radiusOffset, verticalOffsetSeed] = seed;
    const band = index % 3 === 0 ? "far" : "mid";
    const thickness = band === "far" ? 2 : 1.4;
    const sizeTier = index % SIZE_SCALES.length;
    return frozenPlacement({
        id: `rear-field-${String(index + 1).padStart(2, "0")}`,
        band,
        family: FAMILIES[index % FAMILIES.length],
        phase,
        radius: (band === "far" ? 6.7 : 5.05) + radiusOffset,
        verticalOffset: verticalOffsetSeed * (band === "far" ? 2.5 : 2),
        verticalRatio: band === "far" ? .46 : .38,
        rearDepth: band === "far" ? -7.1 : -5.15,
        depthBreath: thickness * .07,
        sizeTier,
        scale: SIZE_SCALES[sizeTier],
        rotation: [
            verticalOffsetSeed * .24,
            radiusOffset * 1.4,
            phase * .35
        ],
        spinPeriodMs: 28000 + index % 5 * 6000,
        colorIndex: index % 7
    });
}

const ALL_PLACEMENTS = Object.freeze(
    REAR_FIELD_SEEDS.map(placementFromSeed)
);

export function createRearFieldLayout(profile = {}) {
    const name = typeof profile?.name === "string"
        ? profile.name
        : "balanced";
    const selected = REAR_FIELD_PROFILES[name]
        || REAR_FIELD_PROFILES.balanced;
    return Object.freeze(ALL_PLACEMENTS.slice(0, selected.count));
}

export function sampleRearFieldTransform(placement, pose = {}) {
    const reduceMotion = Boolean(pose.reduceMotion);
    const worldAngle = reduceMotion
        ? 0
        : (Number.isFinite(pose.worldAngle) ? pose.worldAngle : 0);
    const period = REAR_FIELD_PERIODS_MS[placement.band];
    const phase = placement.phase + worldAngle * 36000 / period;
    const seamDistance = Math.abs(Math.cos(phase));
    const depthBreath = reduceMotion
        ? 0
        : -Math.abs(Math.cos(phase)) * placement.depthBreath;
    return {
        position: [
            Math.sin(phase) * placement.radius,
            placement.verticalOffset
                + Math.cos(phase) * placement.radius * placement.verticalRatio,
            placement.rearDepth + depthBreath
        ],
        rotation: [
            placement.rotation[0],
            placement.rotation[1],
            placement.rotation[2]
                + worldAngle * 36000 / placement.spinPeriodMs
        ],
        opacity: Math.min(1, Math.max(0, seamDistance * 3.2))
    };
}
