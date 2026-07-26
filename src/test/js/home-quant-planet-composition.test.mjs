import test from "node:test";
import assert from "node:assert/strict";
import {
    PRODUCT_SCENES,
    createProductSelection,
    fitPerspectiveSphere,
    resolveAdjacentProduct,
    resolveSelectedProduct
} from "../../main/resources/static/js/home-quant-planet-composition.js";

test("defines the six original Quant product scenes and exact routes", () => {
    assert.deepEqual(PRODUCT_SCENES.map(({ route }) => route), [
        "/quant",
        "/stocks",
        "/stocks/heatmap?index=SP500",
        "/etfs",
        "/atelier",
        "/signals/backtest"
    ]);
    assert.deepEqual(resolveSelectedProduct(2).lines, ["MARKET", "HEATMAP"]);
    assert.equal(resolveSelectedProduct(5).supporting, "Backtest field");
});

test("fits a complete sphere against the stricter viewport field of view", () => {
    const landscape = fitPerspectiveSphere({
        center: { x: 0, y: .4, z: 0 },
        radius: 3.8,
        aspect: 16 / 9,
        verticalFovDegrees: 34,
        margin: 1.18
    });
    const portrait = fitPerspectiveSphere({
        center: { x: 0, y: .4, z: 0 },
        radius: 3.8,
        aspect: 375 / 812,
        verticalFovDegrees: 34,
        margin: 1.18
    });
    assert.deepEqual(landscape.target, { x: 0, y: .4, z: 0 });
    assert.ok(landscape.distance > 3.8);
    assert.ok(portrait.distance > landscape.distance);
});

test("snaps drag selection to one of six wrapped product scenes", () => {
    const state = createProductSelection({
        rotationAngle: 0,
        dragAngle: Math.PI * .34,
        selectedIndex: 0,
        snap: true,
        count: 6
    });
    assert.equal(Number.isInteger(state.selectedIndex), true);
    assert.equal(state.items.length, 6);
    assert.equal(resolveAdjacentProduct(0, -1).route, "/signals/backtest");
});

test("centers the snapped sector and gives its adjacent labels equal depth", () => {
    const state = createProductSelection({
        rotationAngle: 0,
        dragAngle: 0,
        selectedIndex: 2,
        snap: true,
        count: 6
    });

    assert.equal(state.selectedIndex, 2);
    assert.equal(state.frontIndex, 2);
    assert.equal(state.items[2].depth, 1);
    assert.equal(state.items[1].depth, state.items[3].depth);
});
