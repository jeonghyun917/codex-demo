import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import * as THREE from "../../main/resources/static/js/vendor/three.module.js";
import {
    PRODUCT_SCENES,
    createProductSelection,
    fitPerspectiveSphere,
    resolveAdjacentProduct,
    resolveSelectedProduct
} from "../../main/resources/static/js/home-quant-planet-composition.js";
import {
    createQuantPlanetScene
} from "../../main/resources/static/js/home-quant-planet-scene.js";
import {
    createQuantPlanetWorld
} from "../../main/resources/static/js/home-quant-planet-world.js";

const localImports = (source) => Array.from(
    source.matchAll(/^import\s+[\s\S]*?\s+from\s+["']([^"']+)["'];?$/gm),
    ([, specifier]) => specifier
);

test("keeps the composition dependency graph acyclic", async () => {
    const compositionUrl = new URL(
        "../../main/resources/static/js/home-quant-planet-composition.js",
        import.meta.url
    );
    const motionUrl = new URL(
        "../../main/resources/static/js/home-quant-planet-motion.js",
        import.meta.url
    );
    const [compositionSource, motionSource] = await Promise.all([
        readFile(compositionUrl, "utf8"),
        readFile(motionUrl, "utf8")
    ]);
    const nodeFor = (specifier) => ({
        "./home-quant-planet-composition.js": "composition",
        "./home-quant-planet-motion.js": "motion"
    })[specifier];
    const dependencies = {
        composition: localImports(compositionSource).map(nodeFor).filter(Boolean),
        motion: localImports(motionSource).map(nodeFor).filter(Boolean)
    };

    assert.deepEqual(dependencies, {
        composition: [],
        motion: ["composition"]
    });
});

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

test("refits the complete world after landscape and portrait resizes", async (t) => {
    const renderedCameras = [];
    class InspectableCamera extends THREE.PerspectiveCamera {
        lookAt(...values) {
            const [first] = values;
            this.lookAtTarget = first?.isVector3
                ? first.clone()
                : new THREE.Vector3(...values);
            super.lookAt(...values);
        }
    }
    class TestRenderer {
        constructor() {
            this.shadowMap = {};
            this.info = { render: { calls: 0, triangles: 0 } };
        }
        setPixelRatio() {}
        setSize() {}
        render(scene, camera) {
            scene.updateMatrixWorld(true);
            renderedCameras.push({
                aspect: camera.aspect,
                position: camera.position.clone(),
                target: camera.lookAtTarget.clone()
            });
        }
        dispose() {}
    }
    const testThree = {
        ...THREE,
        PerspectiveCamera: InspectableCamera,
        WebGLRenderer: TestRenderer
    };
    const profile = {
        name: "high",
        webgl: true,
        outlineLevel: 0,
        foliageCount: 0,
        cloudCount: 0,
        shadows: false,
        pixelRatio: 1,
        planetScale: 1
    };
    const canvas = { clientWidth: 1440, clientHeight: 900 };
    const world = createQuantPlanetWorld(THREE, { profile });
    const envelope = world.getFramingEnvelope();
    world.dispose();
    const controller = await createQuantPlanetScene({
        canvas,
        profile,
        three: testThree
    });
    t.after(() => controller.dispose());

    controller.resize();
    assert.equal(controller.render(0), true);
    canvas.clientWidth = 375;
    canvas.clientHeight = 812;
    controller.resize();
    assert.equal(controller.render(0), true);

    const [landscape, portrait] = renderedCameras;
    assert.equal(landscape.aspect, 1440 / 900);
    assert.equal(portrait.aspect, 375 / 812);
    [landscape, portrait].forEach(({ target }) => {
        assert.deepEqual(target.toArray(), [
            envelope.center.x,
            envelope.center.y,
            envelope.center.z
        ]);
    });
    const landscapeDistance = landscape.position.distanceTo(landscape.target);
    const portraitDistance = portrait.position.distanceTo(portrait.target);
    assert.ok(portraitDistance > landscapeDistance);
    const verticalFov = 34 * Math.PI / 180;
    const portraitHorizontalFov = 2 * Math.atan(
        Math.tan(verticalFov / 2) * portrait.aspect
    );
    const framedRadius = portraitDistance
        * Math.sin(Math.min(verticalFov, portraitHorizontalFov) / 2);
    assert.ok(framedRadius >= envelope.radius * 1.18);
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
