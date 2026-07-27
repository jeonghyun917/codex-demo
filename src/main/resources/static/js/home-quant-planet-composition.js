const radians = (degrees) => degrees * Math.PI / 180;

export const REFERENCE_HERO = Object.freeze({
    titleAssetUrl: "/img/home-messenger-reference-title.png",
    introDeadlineMs: 3999,
    worldRevolutionMs: 36000,
    planetDiameterScale: 1.2,
    glyphYawRadians: Math.PI / 9,
    glyphPitchRadians: Math.PI / 15,
    glyphVariationRadians: Math.PI / 45,
    glyphResponsePerSecond: 8.5
});

export function fitPerspectiveSphere({
    center = { x: 0, y: 0, z: 0 },
    radius = 1,
    aspect = 1,
    verticalFovDegrees = 34,
    margin = 1.18
} = {}) {
    const safeRadius = Math.max(.001, Number(radius) || 1);
    const safeAspect = Math.max(.1, Number(aspect) || 1);
    const vertical = radians(verticalFovDegrees);
    const horizontal = 2 * Math.atan(
        Math.tan(vertical / 2) * safeAspect
    );
    const limitingFovRadians = Math.min(vertical, horizontal);
    const framedRadius = safeRadius * Math.max(1, Number(margin) || 1);
    return {
        target: {
            x: Number(center.x) || 0,
            y: Number(center.y) || 0,
            z: Number(center.z) || 0
        },
        radius: safeRadius,
        distance: framedRadius / Math.sin(limitingFovRadians / 2),
        limitingFovRadians
    };
}

export function fitReferenceWorld({
    center = { x: 0, y: 0, z: 0 },
    radius = 1,
    aspect = 1,
    verticalFovDegrees = 34,
    visualScale = 1
} = {}) {
    const coverage = .98;
    const margin = 1 / coverage;
    const safeRadius = Math.max(.001, Number(radius) || 1);
    const requestedVisualScale = Math.max(
        1,
        Number(visualScale) || 1
    );
    const fittedRadius = safeRadius / requestedVisualScale;
    const fit = fitPerspectiveSphere({
        center,
        radius: fittedRadius,
        aspect,
        verticalFovDegrees,
        margin
    });
    const apparentRadiusRadians = Math.asin(Math.min(
        1,
        safeRadius / fit.distance
    ));
    const projectedDiameterRatio = Math.tan(apparentRadiusRadians)
        / Math.tan(fit.limitingFovRadians / 2);
    return {
        ...fit,
        radius: safeRadius,
        fittedRadius,
        coverage,
        margin,
        visualScale: requestedVisualScale,
        projectedDiameterRatio
    };
}
