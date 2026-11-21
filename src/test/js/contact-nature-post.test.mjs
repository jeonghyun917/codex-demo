import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

import * as THREE from "../../main/resources/static/js/vendor/three.module.js";

const postModule = await import(
    "../../main/resources/static/js/contact-nature-post.js"
).catch(() => null);
const productionNatureSource = await readFile(
    new URL("../../main/resources/static/js/contact-nature.js", import.meta.url),
    "utf8"
);

function makeRenderer() {
    const calls = [];
    let target = null;
    return {
        calls,
        setRenderTarget(next) {
            target = next;
            calls.push(["target", next]);
        },
        getRenderTarget() {
            return target;
        },
        render(scene, camera) {
            calls.push(["render", scene, camera]);
        }
    };
}

test("nature finishing pass owns depth, AO, focus and color-grade resources", () => {
    assert.ok(postModule, "nature post-processing module must exist");
    const renderer = makeRenderer();
    const post = postModule.createNaturePostProcessor(THREE, renderer, {
        width: 320,
        height: 180,
        pixelRatio: 1.5
    });

    assert.deepEqual(post.diagnostics(), {
        active: true,
        disposed: false,
        width: 480,
        height: 270,
        hasDepth: true
    });
    assert.ok(post.material.fragmentShader.includes("contactOcclusion"));
    assert.ok(post.material.fragmentShader.includes("focusBlur"));
    assert.ok(post.material.fragmentShader.includes("warmGrade"));
    assert.ok(post.material.uniforms.uExposure.value <= 1, "finishing pass must not overexpose the HDR meadow");
    assert.ok(post.material.uniforms.uGradeStrength.value <= 0.25, "color grade must preserve the source PBR range");
    assert.ok(!post.material.fragmentShader.includes("color / (color + vec3(0.035))"));

    const scene = new THREE.Scene();
    const camera = new THREE.PerspectiveCamera();
    post.render(scene, camera);
    assert.equal(renderer.calls.filter(([type]) => type === "render").length, 2);

    post.resize(200, 100, 1.25);
    assert.equal(post.diagnostics().width, 250);
    assert.equal(post.diagnostics().height, 125);
    post.dispose();
    assert.equal(post.diagnostics().disposed, true);
});

test("nature finishing pass degrades to one direct render", () => {
    assert.ok(postModule, "nature post-processing module must exist");
    const renderer = makeRenderer();
    const post = postModule.createNaturePostProcessor(THREE, renderer, {
        width: 320,
        height: 180,
        forceFallback: true
    });
    const scene = new THREE.Scene();
    const camera = new THREE.PerspectiveCamera();

    post.render(scene, camera);

    assert.equal(post.diagnostics().active, false);
    assert.equal(renderer.calls.filter(([type]) => type === "render").length, 1);
    post.dispose();
});

test("contact4 lifecycle resizes, renders and disposes the finishing pass", () => {
    assert.ok(productionNatureSource.includes("createNaturePostProcessor"));
    assert.ok(productionNatureSource.includes("postProcessor.resize("));
    assert.ok(productionNatureSource.includes("postProcessor.render(scene, camera)"));
    assert.ok(productionNatureSource.includes("postProcessor.dispose()"));
});
