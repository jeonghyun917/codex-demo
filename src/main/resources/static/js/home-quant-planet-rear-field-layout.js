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

const AUTHORED_FAMILY_SEQUENCE = Object.freeze([
    5, 0, 4, 3, 4, 2, 3, 3, 3, 1, 0, 0, 5, 5,
    1, 3, 1, 1, 2, 0, 3, 0, 5, 1, 1, 0, 4, 1,
    3, 5, 4, 0, 1, 5, 0, 2, 2, 5, 5, 1, 4, 1
]);

const AUTHORED_BAND_SEQUENCE = Object.freeze([
    "far", "mid", "mid", "mid", "mid", "mid", "mid",
    "mid", "far", "far", "mid", "mid", "far", "mid",
    "mid", "far", "mid", "mid", "mid", "far", "mid",
    "mid", "mid", "mid", "mid", "far", "mid", "mid",
    "mid", "mid", "far", "far", "mid", "far", "mid",
    "mid", "far", "mid", "mid", "mid", "far", "mid"
]);

const AUTHORED_SIZE_SEQUENCE = Object.freeze([
    0, 2, 4, 5, 1, 3, 4, 1, 1, 0, 0, 5, 1, 5,
    1, 4, 4, 3, 2, 4, 3, 0, 0, 0, 1, 3, 4, 1,
    1, 5, 2, 5, 5, 4, 2, 3, 2, 0, 1, 4, 1, 4
]);

const AUTHORED_SPIN_PERIODS_MS = Object.freeze([
    8966, 14978, 15588, 14810, 15905, 9967, 15366,
    9588, 14031, 12061, 9223, 16241, 13662, 15881,
    11341, 15969, 8146, 13932, 15372, 9074, 14415,
    16527, 12787, 14090, 12963, 17094, 16768, 11878,
    12160, 13843, 16699, 13517, 16986, 14389, 8450,
    9692, 16461, 17381, 9746, 10365, 15828, 16221
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
    const band = AUTHORED_BAND_SEQUENCE[index];
    const thickness = band === "far" ? 2 : 1.4;
    const radius = (band === "far" ? 6.7 : 5.05) + radiusOffset;
    const verticalRatio = band === "far" ? .46 : .38;
    const rawVerticalOffset = verticalOffsetSeed
        * (band === "far" ? 2.5 : 2);
    const authoredX = Math.sin(phase) * radius;
    const authoredY = rawVerticalOffset
        + Math.cos(phase) * radius * verticalRatio;
    const verticalOffset = Math.abs(authoredX) < 1.6
        && authoredY < -1.2
        ? rawVerticalOffset + (-.95 - authoredY)
        : rawVerticalOffset;
    const sizeTier = AUTHORED_SIZE_SEQUENCE[index];
    return frozenPlacement({
        id: `rear-field-${String(index + 1).padStart(2, "0")}`,
        band,
        family: FAMILIES[AUTHORED_FAMILY_SEQUENCE[index]],
        phase,
        radius,
        verticalOffset,
        verticalRatio,
        rearDepth: band === "far" ? -7.1 : -5.15,
        depthBreath: thickness * .07,
        sizeTier,
        scale: SIZE_SCALES[sizeTier],
        rotation: [
            verticalOffsetSeed * .24,
            radiusOffset * 1.4,
            phase * .35
        ],
        spinPeriodMs: AUTHORED_SPIN_PERIODS_MS[index],
        colorIndex: index % 7
    });
}

const ALL_PLACEMENTS = Object.freeze(
    REAR_FIELD_SEEDS.map(placementFromSeed)
);

function smoothstep(edge0, edge1, value) {
    const unit = Math.min(
        1,
        Math.max(0, (value - edge0) / (edge1 - edge0))
    );
    return unit * unit * (3 - 2 * unit);
}

function enterQuietZoneOpacity(x, y) {
    const horizontalOutside = smoothstep(1.6, 2.1, Math.abs(x));
    const verticalOutside = smoothstep(-1.2, -.7, y);
    return Math.max(horizontalOutside, verticalOutside);
}

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
    const elapsedMs = reduceMotion
        ? 0
        : Math.max(0, Number.isFinite(pose.elapsedMs) ? pose.elapsedMs : 0);
    const period = REAR_FIELD_PERIODS_MS[placement.band];
    const phase = placement.phase + elapsedMs / period * Math.PI * 2;
    const seamDistance = Math.abs(Math.cos(phase));
    const depthBreath = reduceMotion
        ? 0
        : -Math.abs(Math.cos(phase)) * placement.depthBreath;
    const x = Math.sin(phase) * placement.radius;
    const y = placement.verticalOffset
        + Math.cos(phase) * placement.radius * placement.verticalRatio;
    return {
        position: [
            x,
            y,
            placement.rearDepth + depthBreath
        ],
        rotation: [
            placement.rotation[0],
            placement.rotation[1],
            placement.rotation[2]
                + elapsedMs / placement.spinPeriodMs * Math.PI * 2
        ],
        opacity: Math.min(1, Math.max(0, seamDistance * 3.2))
            * enterQuietZoneOpacity(x, y)
    };
}
