const TAU = Math.PI * 2;
const radians = (degrees) => degrees * Math.PI / 180;
const clamp = (value, minimum, maximum) =>
    Math.min(Math.max(Number(value) || 0, minimum), maximum);

export function wrapIndex(index, count = 6) {
    const length = Math.max(1, Math.trunc(Number(count)) || 1);
    return ((Math.trunc(Number(index)) || 0) % length + length) % length;
}

export const PRODUCT_SCENES = Object.freeze([
    Object.freeze({
        route: "/quant",
        lines: Object.freeze(["QUANT", "INTELLIGENCE"]),
        supporting: "Observe 쨌 Resolve 쨌 Act"
    }),
    Object.freeze({
        route: "/stocks",
        lines: Object.freeze(["STOCK", "SIGNALS"]),
        supporting: "Market field"
    }),
    Object.freeze({
        route: "/stocks/heatmap?index=SP500",
        lines: Object.freeze(["MARKET", "HEATMAP"]),
        supporting: "S&P 500 field"
    }),
    Object.freeze({
        route: "/etfs",
        lines: Object.freeze(["ETF", "RADAR"]),
        supporting: "Allocation field"
    }),
    Object.freeze({
        route: "/atelier",
        lines: Object.freeze(["STRATEGY", "ATELIER"]),
        supporting: "Research field"
    }),
    Object.freeze({
        route: "/signals/backtest",
        lines: Object.freeze(["SIGNAL", "LAB"]),
        supporting: "Backtest field"
    })
]);

export const REFERENCE_TIMING = Object.freeze({
    introDeadlineMs: 4000,
    worldRevolutionMs: 96000,
    productRevolutionMs: 28000,
    selectionSnapMs: 620,
    idleResumeMs: 900
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
    const horizontal = 2 * Math.atan(Math.tan(vertical / 2) * safeAspect);
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

export function resolveSelectedProduct(index) {
    return PRODUCT_SCENES[wrapIndex(index, PRODUCT_SCENES.length)];
}

export function resolveAdjacentProduct(index, direction) {
    return resolveSelectedProduct(
        wrapIndex(index, PRODUCT_SCENES.length) + (Math.trunc(direction) || 0)
    );
}

export function createProductSelection({
    rotationAngle = 0,
    dragAngle = 0,
    selectedIndex = 0,
    snap = false,
    count = PRODUCT_SCENES.length
} = {}) {
    const itemCount = Math.max(1, Math.trunc(Number(count)) || 1);
    const step = TAU / itemCount;
    const initialIndex = wrapIndex(selectedIndex, itemCount);
    const freeBaseAngle = (Number(rotationAngle) || 0)
        + clamp(dragAngle, -TAU * 2, TAU * 2)
        - initialIndex * step;
    const snappedIndex = wrapIndex(Math.round(-freeBaseAngle / step), itemCount);
    const baseAngle = snap ? -snappedIndex * step : freeBaseAngle;
    const items = Array.from({ length: itemCount }, (_, index) => {
        const angle = baseAngle + index * step;
        const depth = (Math.cos(angle) + 1) / 2;
        return {
            index,
            angle,
            depth,
            opacity: .35 + depth * .65,
            scale: .72 + depth * .40,
            zIndex: Math.round(depth * 100)
        };
    });
    const frontIndex = items.reduce((front, item) =>
        item.depth > items[front].depth ? item.index : front, 0);

    return {
        selectedIndex: snap ? snappedIndex : frontIndex,
        frontIndex,
        baseAngle,
        items
    };
}
