import assert from "node:assert/strict";
import test from "node:test";
import {
    GLYPH_GRID,
    extractGlyphContours,
    loadLocalImageData
} from "../../main/resources/static/js/home-quant-planet-glyph-contours.js";

function createImageData(width, height) {
    return {
        width,
        height,
        data: new Uint8ClampedArray(width * height * 4)
    };
}

function paintRectangle(image, left, top, width, height, alpha = 255) {
    for (let y = top; y < top + height; y += 1) {
        for (let x = left; x < left + width; x += 1) {
            image.data[(y * image.width + x) * 4 + 3] = alpha;
        }
    }
}

function createNineCellMask(width, height) {
    const image = createImageData(width, height);
    const cellWidth = width / 3;
    const cellHeight = height / 3;

    for (let row = 0; row < 3; row += 1) {
        for (let column = 0; column < 3; column += 1) {
            paintRectangle(
                image,
                column * cellWidth + 6,
                row * cellHeight + 8,
                18,
                14
            );
        }
    }
    return image;
}

function createDonutInFirstCell(width, height) {
    const image = createNineCellMask(width, height);
    paintRectangle(image, 12, 12, 6, 6, 0);
    return image;
}

function signedArea(loop) {
    return loop.slice(1).reduce((area, point, index) => {
        const previous = loop[index];
        return area + previous.x * point.y - point.x * previous.y;
    }, 0) / 2;
}

test("extracts exactly nine normalized glyph cells", () => {
    const image = createNineCellMask(90, 90);
    const glyphs = extractGlyphContours(image, {
        alphaThreshold: 32,
        simplifyTolerance: 0
    });

    assert.equal(glyphs.length, 9);
    assert.ok(glyphs.every((glyph) => glyph.length === 1));
    assert.ok(glyphs.flatMap((glyph) => glyph)
        .every(({ outer }) => signedArea(outer) > 0));
    assert.ok(Math.abs(signedArea(glyphs[0][0].outer) - .28) < 1e-12);
});

test("assigns enclosed transparent loops as holes", () => {
    const image = createDonutInFirstCell(90, 90);
    const [first] = extractGlyphContours(image, {
        alphaThreshold: 32,
        simplifyTolerance: 0
    });

    assert.equal(first.length, 1);
    assert.equal(first[0].holes.length, 1);
    assert.ok(signedArea(first[0].outer) > 0);
    assert.ok(signedArea(first[0].holes[0]) < 0);
    assert.ok(Math.abs(signedArea(first[0].holes[0]) + .04) < 1e-12);
});

test("simplifies pixel stairs while preserving closed contours", () => {
    const image = createNineCellMask(90, 90);
    const raw = extractGlyphContours(image, { simplifyTolerance: 0 });
    const simplified = extractGlyphContours(image, {
        simplifyTolerance: 1.5
    });
    assert.equal(raw[0][0].outer.length, 65);
    assert.equal(simplified[0][0].outer.length, 5);
    assert.ok(simplified[0][0].outer.length < raw[0][0].outer.length);
    assert.deepEqual(
        simplified[0][0].outer[0],
        simplified[0][0].outer.at(-1)
    );
});

test("rejects a grid cell without an alpha contour", () => {
    const image = createNineCellMask(90, 90);
    paintRectangle(image, 66, 68, 18, 14, 0);

    assert.throws(
        () => extractGlyphContours(image),
        /Missing alpha contour for glyph 9/
    );
});

test("exposes a frozen normalized three-by-three source grid", () => {
    assert.equal(GLYPH_GRID.length, 9);
    assert.deepEqual(GLYPH_GRID[8], {
        column: 2,
        row: 2,
        left: 2 / 3,
        top: 2 / 3,
        width: 1 / 3,
        height: 1 / 3
    });
    assert.equal(Object.isFrozen(GLYPH_GRID), true);
    assert.equal(Object.isFrozen(GLYPH_GRID[0]), true);
});

test("loads decoded local image pixels through a canvas", async () => {
    const imageData = createImageData(2, 1);
    const drawCalls = [];
    const canvas = {
        width: 0,
        height: 0,
        getContext(type, options) {
            assert.equal(type, "2d");
            assert.deepEqual(options, { alpha: true, willReadFrequently: true });
            return {
                drawImage(...args) {
                    drawCalls.push(args);
                },
                getImageData(left, top, width, height) {
                    assert.deepEqual([left, top, width, height], [0, 0, 2, 1]);
                    return imageData;
                }
            };
        }
    };
    const documentTarget = {
        createElement(tagName) {
            assert.equal(tagName, "canvas");
            return canvas;
        }
    };
    const previousImage = globalThis.Image;
    class FakeImage {
        naturalWidth = 2;
        naturalHeight = 1;

        decode() {
            return Promise.resolve();
        }
    }
    globalThis.Image = FakeImage;

    try {
        const loaded = await loadLocalImageData("/images/title.png", documentTarget);
        assert.equal(loaded, imageData);
        assert.equal(drawCalls.length, 1);
        assert.equal(drawCalls[0][0].src, "/images/title.png");
        assert.deepEqual(drawCalls[0].slice(1), [0, 0]);
        assert.equal(drawCalls[0][0].decoding, "async");
    } finally {
        globalThis.Image = previousImage;
    }
});
