export const GLYPH_GRID = Object.freeze(
    Array.from({ length: 9 }, (_, index) => Object.freeze({
        column: index % 3,
        row: Math.floor(index / 3),
        left: (index % 3) / 3,
        top: Math.floor(index / 3) / 3,
        width: 1 / 3,
        height: 1 / 3
    }))
);

function validateImageData(imageData) {
    if (
        !imageData
        || !Number.isInteger(imageData.width)
        || !Number.isInteger(imageData.height)
        || imageData.width <= 0
        || imageData.height <= 0
        || !imageData.data
        || imageData.data.length < imageData.width * imageData.height * 4
    ) {
        throw new TypeError("Expected ImageData-shaped alpha pixels");
    }
}

function resolvePixelBounds(region, imageData) {
    return {
        left: Math.floor(region.left * imageData.width),
        top: Math.floor(region.top * imageData.height),
        right: Math.floor((region.left + region.width) * imageData.width),
        bottom: Math.floor((region.top + region.height) * imageData.height)
    };
}

function pointKey(point) {
    return `${point.x},${point.y}`;
}

function pointsEqual(first, second) {
    return first.x === second.x && first.y === second.y;
}

function direction(edge) {
    const dx = edge.end.x - edge.start.x;
    const dy = edge.end.y - edge.start.y;
    if (dx === 1) return 0;
    if (dy === 1) return 1;
    if (dx === -1) return 2;
    return 3;
}

function chooseNextEdge(current, candidates) {
    const currentDirection = direction(current);
    return candidates.slice().sort((first, second) => {
        const firstTurn = (direction(first) - currentDirection + 4) % 4;
        const secondTurn = (direction(second) - currentDirection + 4) % 4;
        return firstTurn - secondTurn;
    })[0];
}

function opaqueAt(imageData, bounds, x, y, alphaThreshold) {
    if (x < bounds.left || x >= bounds.right || y < bounds.top || y >= bounds.bottom) {
        return false;
    }
    return imageData.data[(y * imageData.width + x) * 4 + 3] >= alphaThreshold;
}

function traceOpaquePixelEdges(imageData, bounds, alphaThreshold) {
    const edges = [];
    for (let y = bounds.top; y < bounds.bottom; y += 1) {
        for (let x = bounds.left; x < bounds.right; x += 1) {
            if (!opaqueAt(imageData, bounds, x, y, alphaThreshold)) {
                continue;
            }
            if (!opaqueAt(imageData, bounds, x, y - 1, alphaThreshold)) {
                edges.push({ start: { x, y }, end: { x: x + 1, y } });
            }
            if (!opaqueAt(imageData, bounds, x + 1, y, alphaThreshold)) {
                edges.push({ start: { x: x + 1, y }, end: { x: x + 1, y: y + 1 } });
            }
            if (!opaqueAt(imageData, bounds, x, y + 1, alphaThreshold)) {
                edges.push({ start: { x: x + 1, y: y + 1 }, end: { x, y: y + 1 } });
            }
            if (!opaqueAt(imageData, bounds, x - 1, y, alphaThreshold)) {
                edges.push({ start: { x, y: y + 1 }, end: { x, y } });
            }
        }
    }

    const edgesByStart = new Map();
    edges.forEach((edge, index) => {
        const key = pointKey(edge.start);
        const group = edgesByStart.get(key) || [];
        group.push(index);
        edgesByStart.set(key, group);
    });

    const consumed = new Set();
    const loops = [];
    edges.forEach((initialEdge, initialIndex) => {
        if (consumed.has(initialIndex)) {
            return;
        }
        const loop = [initialEdge.start];
        let current = initialEdge;
        let currentIndex = initialIndex;
        while (true) {
            consumed.add(currentIndex);
            loop.push(current.end);
            if (pointsEqual(current.end, initialEdge.start)) {
                loops.push(loop);
                return;
            }
            const candidateIndexes = (edgesByStart.get(pointKey(current.end)) || [])
                .filter((index) => !consumed.has(index));
            if (candidateIndexes.length === 0) {
                throw new Error("Unable to close alpha contour");
            }
            const candidates = candidateIndexes.map((index) => edges[index]);
            currentIndex = candidateIndexes[candidates.indexOf(
                chooseNextEdge(current, candidates)
            )];
            current = edges[currentIndex];
        }
    });
    return loops;
}

function squaredDistanceToSegment(point, start, end) {
    const dx = end.x - start.x;
    const dy = end.y - start.y;
    if (dx === 0 && dy === 0) {
        const x = point.x - start.x;
        const y = point.y - start.y;
        return x * x + y * y;
    }
    const projection = Math.max(0, Math.min(1,
        ((point.x - start.x) * dx + (point.y - start.y) * dy) / (dx * dx + dy * dy)
    ));
    const x = start.x + projection * dx - point.x;
    const y = start.y + projection * dy - point.y;
    return x * x + y * y;
}

function simplifyOpenLoop(points, tolerance) {
    if (points.length <= 2) {
        return points;
    }
    let farthestIndex = 0;
    let farthestDistance = -1;
    for (let index = 1; index < points.length - 1; index += 1) {
        const distance = squaredDistanceToSegment(
            points[index],
            points[0],
            points.at(-1)
        );
        if (distance > farthestDistance) {
            farthestDistance = distance;
            farthestIndex = index;
        }
    }
    if (farthestDistance <= tolerance * tolerance) {
        return [points[0], points.at(-1)];
    }
    return [
        ...simplifyOpenLoop(points.slice(0, farthestIndex + 1), tolerance).slice(0, -1),
        ...simplifyOpenLoop(points.slice(farthestIndex), tolerance)
    ];
}

function closeLoop(loop) {
    if (loop.length === 0 || pointsEqual(loop[0], loop.at(-1))) {
        return loop;
    }
    return [...loop, loop[0]];
}

function simplifyClosedLoop(loop, tolerance) {
    if (tolerance <= 0 || loop.length <= 4) {
        return closeLoop(loop);
    }
    const ring = pointsEqual(loop[0], loop.at(-1)) ? loop.slice(0, -1) : loop;
    let firstIndex = 0;
    let secondIndex = 1;
    let greatestDistance = -1;
    for (let first = 0; first < ring.length; first += 1) {
        for (let second = first + 1; second < ring.length; second += 1) {
            const dx = ring[first].x - ring[second].x;
            const dy = ring[first].y - ring[second].y;
            const distance = dx * dx + dy * dy;
            if (distance > greatestDistance) {
                greatestDistance = distance;
                firstIndex = first;
                secondIndex = second;
            }
        }
    }
    const firstPath = [];
    for (let index = firstIndex; index <= secondIndex; index += 1) {
        firstPath.push(ring[index]);
    }
    const secondPath = [];
    for (let index = secondIndex; index < ring.length; index += 1) {
        secondPath.push(ring[index]);
    }
    for (let index = 0; index <= firstIndex; index += 1) {
        secondPath.push(ring[index]);
    }
    return closeLoop([
        ...simplifyOpenLoop(firstPath, tolerance).slice(0, -1),
        ...simplifyOpenLoop(secondPath, tolerance)
    ]);
}

function normalizeLoop(loop, bounds) {
    const width = bounds.right - bounds.left;
    const height = bounds.bottom - bounds.top;
    return loop.map((point) => ({
        x: (point.x - bounds.left) / width - .5,
        y: .5 - (point.y - bounds.top) / height
    }));
}

function signedArea(loop) {
    return loop.slice(1).reduce((area, point, index) => {
        const previous = loop[index];
        return area + previous.x * point.y - point.x * previous.y;
    }, 0) / 2;
}

function pointInLoop(point, loop) {
    let contained = false;
    for (let index = 0, previous = loop.length - 1; index < loop.length; previous = index++) {
        const currentPoint = loop[index];
        const previousPoint = loop[previous];
        const crosses = (currentPoint.y > point.y) !== (previousPoint.y > point.y);
        if (crosses && point.x < (previousPoint.x - currentPoint.x)
            * (point.y - currentPoint.y) / (previousPoint.y - currentPoint.y) + currentPoint.x) {
            contained = !contained;
        }
    }
    return contained;
}

function oriented(loop, positive) {
    const area = signedArea(loop);
    if ((positive && area > 0) || (!positive && area < 0)) {
        return loop;
    }
    return closeLoop(loop.slice(0, -1).reverse());
}

function classifyOuterAndHoleLoops(loops) {
    const contours = loops.map((loop) => ({ loop, area: Math.abs(signedArea(loop)) }))
        .sort((first, second) => second.area - first.area);
    const resolved = [];
    const glyphs = [];

    contours.forEach((contour) => {
        const containers = resolved.filter((candidate) =>
            pointInLoop(contour.loop[0], candidate.loop)
        );
        const isOuter = containers.length % 2 === 0;
        const resolvedContour = { ...contour, isOuter, glyph: null };
        if (isOuter) {
            resolvedContour.glyph = { outer: oriented(contour.loop, true), holes: [] };
            glyphs.push(resolvedContour.glyph);
        } else {
            const enclosingOuter = containers.filter((candidate) => candidate.isOuter).at(-1);
            enclosingOuter.glyph.holes.push(oriented(contour.loop, false));
        }
        resolved.push(resolvedContour);
    });
    return glyphs;
}

export function extractGlyphContours(imageData, {
    alphaThreshold = 32,
    simplifyTolerance = 1.4
} = {}) {
    validateImageData(imageData);
    return GLYPH_GRID.map((region, index) => {
        const bounds = resolvePixelBounds(region, imageData);
        const loops = traceOpaquePixelEdges(imageData, bounds, alphaThreshold)
            .map((loop) => closeLoop(simplifyClosedLoop(loop, simplifyTolerance)))
            .map((loop) => normalizeLoop(loop, bounds));
        const glyph = classifyOuterAndHoleLoops(loops);
        if (glyph.length === 0) {
            throw new Error(`Missing alpha contour for glyph ${index + 1}`);
        }
        return glyph;
    });
}

export async function loadLocalImageData(
    assetUrl,
    documentTarget = globalThis.document
) {
    const image = new Image();
    image.decoding = "async";
    image.src = assetUrl;
    await image.decode();
    const canvas = documentTarget.createElement("canvas");
    canvas.width = image.naturalWidth;
    canvas.height = image.naturalHeight;
    const context = canvas.getContext("2d", {
        alpha: true,
        willReadFrequently: true
    });
    if (!context) throw new Error("2D canvas is unavailable");
    context.drawImage(image, 0, 0);
    return context.getImageData(0, 0, canvas.width, canvas.height);
}
