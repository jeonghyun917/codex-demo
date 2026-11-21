import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

import * as THREE from "../../main/resources/static/js/vendor/three.module.js";

const assetModule = await import(
    "../../main/resources/static/js/contact-nature-assets.js"
).catch(() => null);
const productionNatureSource = await readFile(
    new URL("../../main/resources/static/js/contact-nature.js", import.meta.url),
    "utf8"
);

test("photoreal asset manifest stays local and includes the complete PBR set", () => {
    assert.ok(assetModule, "nature asset module must exist");
    const values = Object.values(assetModule.NATURE_ASSETS);

    assert.ok(values.every((url) => url.startsWith("/assets/contact4/")));
    assert.ok(assetModule.NATURE_ASSETS.grassModel.endsWith(".gltf"));
    assert.ok(assetModule.NATURE_ASSETS.environment.endsWith(".hdr"));
    assert.ok(assetModule.NATURE_ASSETS.groundLushDiffuse.includes("leafy_grass_diff_2k"));
    assert.ok(assetModule.NATURE_ASSETS.groundDiffuse.includes("leafy_grass_diff_2k"));
    assert.ok(assetModule.NATURE_ASSETS.groundNormal.includes("leafy_grass_nor_gl_2k"));
    assert.ok(assetModule.NATURE_ASSETS.groundRoughness.includes("leafy_grass_rough_2k"));
    assert.ok(assetModule.NATURE_ASSETS.groundAo.includes("leafy_grass_ao_2k"));
    assert.ok(assetModule.NATURE_ASSETS.groundDisplacement.includes("leafy_grass_disp_2k"));
    assert.ok(assetModule.NATURE_ASSETS.lushGrassModel.includes("grass_medium_01_2k"));
    assert.ok(assetModule.NATURE_ASSETS.lushGrassAlpha.includes("grass_medium_01_alpha_2k"));
    assert.ok(assetModule.NATURE_ASSETS.wildflowerModel.includes("flower_empodium_2k"));
    assert.ok(assetModule.NATURE_ASSETS.wildflowerAlpha.includes("flower_empodium_alpha_2k"));
    assert.ok(assetModule.NATURE_ASSETS.dandelionModel.includes("dandelion_01_2k"));
    assert.ok(assetModule.NATURE_ASSETS.dandelionAlpha.includes("dandelion_01_alpha_2k"));
    assert.ok(assetModule.NATURE_ASSETS.meadowGrassModel.includes("grass_medium_02_4k"));
    assert.ok(assetModule.NATURE_ASSETS.mossModel.includes("moss_01_2k"));
    assert.ok(assetModule.NATURE_ASSETS.grassAlpha.includes("alpha_2k"));
    assert.ok(assetModule.NATURE_ASSETS.meadowGrassAlpha.includes("alpha_4k"));
    assert.ok(assetModule.NATURE_ASSETS.mossAlpha.includes("alpha_2k"));
    assert.ok(assetModule.NATURE_ASSETS.environment.includes("wildflower_field_2k"));
});

test("production meadow uses scanned botanical families instead of procedural pinwheels", () => {
    assert.ok(productionNatureSource.includes("loadGltf(NATURE_ASSETS.dandelionModel)"));
    assert.ok(productionNatureSource.includes("loadGltf(NATURE_ASSETS.mossModel)"));
    assert.ok(productionNatureSource.includes("loadGltf(NATURE_ASSETS.lushGrassModel)"));
    assert.ok(productionNatureSource.includes("loadGltf(NATURE_ASSETS.meadowGrassModel)"));
    assert.ok(productionNatureSource.includes("loadGltf(NATURE_ASSETS.wildflowerModel)"));
    assert.ok(productionNatureSource.includes('name: "dandelion-colonies"'));
    assert.ok(productionNatureSource.includes('name: "dandelion-foreground"'));
    assert.ok(productionNatureSource.includes('name: "dandelion-sunlit-distance"'));
    assert.ok(productionNatureSource.includes('name: "dandelion-rosettes"'));
    assert.ok(productionNatureSource.includes('name: "moss-carpet"'));
    assert.ok(productionNatureSource.includes('name: "grass-foreground"'));
    assert.ok(productionNatureSource.includes('name: "grass-near"'));
    assert.ok(productionNatureSource.includes('name: "grass-mid"'));
    assert.ok(productionNatureSource.includes('name: "secondary-flowers"'));
    assert.ok(!productionNatureSource.includes("createVolumetricDandelionField"));
});

test("PBR meadow layer binds calibrated ground maps and instanced grass", () => {
    assert.ok(assetModule, "nature asset module must exist");
    const maps = {
        diffuse: new THREE.Texture(),
        normal: new THREE.Texture(),
        roughness: new THREE.Texture(),
        ao: new THREE.Texture(),
        displacement: new THREE.Texture()
    };
    const source = new THREE.Group();
    source.add(new THREE.Mesh(
        new THREE.PlaneGeometry(1, 1),
        new THREE.MeshStandardMaterial({ color: 0xffffff })
    ));

    const layer = assetModule.createPhotorealMeadowLayer(THREE, {
        maps,
        grassScene: source,
        grassInstanceCount: 12,
        seed: 917
    });

    assert.equal(layer.root.userData.kind, "photoreal-meadow-layer");
    assert.equal(layer.ground.material.type, "MeshStandardMaterial");
    assert.equal(layer.ground.material.map, maps.diffuse);
    assert.equal(layer.ground.material.normalMap, maps.normal);
    assert.equal(layer.ground.material.roughnessMap, maps.roughness);
    assert.equal(layer.ground.material.aoMap, maps.ao);
    assert.equal(layer.ground.material.displacementMap, maps.displacement);
    assert.ok(layer.grass.children.length >= 1);
    assert.equal(layer.grass.children[0].isInstancedMesh, true);
    assert.equal(layer.grass.children[0].count, 12);

    layer.dispose();
    assert.equal(layer.root.children.length, 0);
});

test("photoreal meadow layers multiple scanned plant families at independent densities", () => {
    assert.ok(assetModule, "nature asset module must exist");
    const maps = {
        diffuse: new THREE.Texture(),
        normal: new THREE.Texture(),
        roughness: new THREE.Texture(),
        ao: new THREE.Texture(),
        displacement: new THREE.Texture()
    };
    const makeSource = () => {
        const source = new THREE.Group();
        source.add(new THREE.Mesh(
            new THREE.PlaneGeometry(1, 1),
            new THREE.MeshStandardMaterial({ color: 0xffffff })
        ));
        return source;
    };
    const meadowAlpha = new THREE.Texture();
    const mossAlpha = new THREE.Texture();
    const meadowSource = makeSource();
    meadowSource.children[0].name = "fresh-green";
    const deadSource = new THREE.Mesh(
        new THREE.PlaneGeometry(1, 1),
        new THREE.MeshStandardMaterial({ color: 0xffffff })
    );
    deadSource.name = "dead-flat";
    meadowSource.add(deadSource);
    const unrelatedSource = new THREE.Mesh(
        new THREE.PlaneGeometry(1, 1),
        new THREE.MeshStandardMaterial({ color: 0xffffff })
    );
    unrelatedSource.name = "other-green";
    meadowSource.add(unrelatedSource);

    const layer = assetModule.createPhotorealMeadowLayer(THREE, {
        maps,
        vegetationScenes: [
            { name: "meadow-grass", scene: meadowSource, count: 7, scale: [0.3, 0.7], targetHeight: 0.8, horizontalScale: 2, minHeightRatio: 0.5, alphaMap: meadowAlpha, includeNames: ["fresh"], excludeNames: ["dead"] },
            { name: "moss", scene: makeSource(), count: 3, scale: [0.5, 1.1], alphaMap: mossAlpha }
        ],
        seed: 917
    });

    assert.equal(layer.vegetation.children.length, 2);
    assert.equal(layer.vegetation.children[0].name, "meadow-grass");
    assert.equal(layer.vegetation.children[0].children[0].count, 7);
    assert.equal(layer.vegetation.children[0].children.length, 1, "only explicitly selected fresh variants may be instanced");
    assert.equal(layer.vegetation.children[1].name, "moss");
    assert.equal(layer.vegetation.children[1].children[0].count, 3);
    assert.equal(layer.vegetation.children[0].children[0].material.alphaMap, meadowAlpha);
    assert.equal(layer.vegetation.children[1].children[0].material.alphaMap, mossAlpha);
    const normalized = layer.vegetation.children[0].children[0].geometry.boundingBox;
    assert.ok(Math.abs(normalized.min.y) < 0.00001, "scanned plant base must sit on the terrain");
    assert.ok(Math.abs(normalized.max.y - 0.8) < 0.00001, "scanned plant height must normalize to authored metres");
    assert.ok(Math.abs(normalized.max.x - normalized.min.x - 1.6) < 0.00001, "ground cover may spread horizontally without growing taller");
    assert.equal(layer.vegetation.children[0].children[0].geometry.attributes.position.count, 3, "height cropping must retain only the upper triangle of the sample plant");

    layer.dispose();
});

test("scanned variant budgets stay exact and remove authored lineup offsets", () => {
    const maps = {
        diffuse: new THREE.Texture(),
        normal: new THREE.Texture(),
        roughness: new THREE.Texture(),
        ao: new THREE.Texture(),
        displacement: new THREE.Texture()
    };
    const source = new THREE.Group();
    for (let index = 0; index < 5; index++) {
        const mesh = new THREE.Mesh(
            new THREE.BoxGeometry(0.3, 1, 0.2),
            new THREE.MeshStandardMaterial({ color: 0xffffff })
        );
        mesh.name = `variant-${index}`;
        mesh.position.set(index * 2.5 + 3, 0.5, -index * 1.7 - 2);
        source.add(mesh);
    }

    const layer = assetModule.createPhotorealMeadowLayer(THREE, {
        maps,
        vegetationScenes: [{ name: "five-stage-plant", scene: source, count: 25, targetHeight: 0.8 }],
        seed: 917
    });
    const variants = layer.vegetation.children[0].children;
    const totalInstances = variants.reduce((sum, mesh) => sum + mesh.count, 0);

    assert.equal(variants.length, 5);
    assert.equal(totalInstances, 25, "family count must be a total budget, not a per-variant multiplier");
    variants.forEach((mesh) => {
        const box = mesh.geometry.boundingBox;
        assert.ok(Math.abs((box.min.x + box.max.x) * 0.5) < 0.00001, "variant X must be centered");
        assert.ok(Math.abs((box.min.z + box.max.z) * 0.5) < 0.00001, "variant Z must be centered");
        assert.ok(Math.abs(box.min.y) < 0.00001, "variant base must sit on terrain");
    });

    layer.dispose();
});

test("clustered vegetation is deterministic and spans foreground through background", () => {
    const makeMaps = () => ({
        diffuse: new THREE.Texture(),
        normal: new THREE.Texture(),
        roughness: new THREE.Texture(),
        ao: new THREE.Texture(),
        displacement: new THREE.Texture()
    });
    const source = new THREE.Group();
    source.add(new THREE.Mesh(
        new THREE.BoxGeometry(0.2, 0.8, 0.12),
        new THREE.MeshStandardMaterial({ color: 0xffffff })
    ));
    const options = {
        vegetationScenes: [{
            name: "clustered-field",
            scene: source,
            count: 36,
            clusterCount: 9,
            clusterSpread: 0.1,
            quietZone: 0.14,
            zRange: [1.5, -22]
        }],
        seed: 917
    };
    const first = assetModule.createPhotorealMeadowLayer(THREE, { maps: makeMaps(), ...options });
    const second = assetModule.createPhotorealMeadowLayer(THREE, { maps: makeMaps(), ...options });
    const firstGroup = first.vegetation.children[0];
    const firstMesh = firstGroup.children[0];
    const secondMesh = second.vegetation.children[0].children[0];
    const matrixA = new THREE.Matrix4();
    const matrixB = new THREE.Matrix4();
    const position = new THREE.Vector3();
    const depths = [];

    assert.deepEqual(firstGroup.userData.distribution, {
        mode: "clustered",
        clusterCount: 9,
        clusterSpread: 0.1,
        quietZone: 0.14
    });
    for (let index = 0; index < firstMesh.count; index++) {
        firstMesh.getMatrixAt(index, matrixA);
        secondMesh.getMatrixAt(index, matrixB);
        assert.deepEqual(matrixA.elements, matrixB.elements);
        position.setFromMatrixPosition(matrixA);
        depths.push(position.z);
    }
    assert.ok(depths.some((z) => z > -5), "clusters must reach the foreground");
    assert.ok(depths.some((z) => z <= -5 && z > -15), "clusters must fill the midground");
    assert.ok(depths.some((z) => z <= -15), "clusters must continue into the background");

    first.dispose();
    second.dispose();
});

test("botanical wind fixes roots and gives flower heads delayed gust inertia", () => {
    const maps = {
        diffuse: new THREE.Texture(),
        normal: new THREE.Texture(),
        roughness: new THREE.Texture(),
        ao: new THREE.Texture(),
        displacement: new THREE.Texture()
    };
    const source = new THREE.Group();
    source.add(new THREE.Mesh(
        new THREE.BoxGeometry(0.2, 1, 0.12),
        new THREE.MeshStandardMaterial({ color: 0xffffff })
    ));
    const layer = assetModule.createPhotorealMeadowLayer(THREE, {
        maps,
        vegetationScenes: [{ name: "wind-plant", scene: source, count: 4, targetHeight: 0.8, wind: 0.9 }],
        seed: 917
    });
    const material = layer.vegetation.children[0].children[0].material;
    const shader = {
        uniforms: {},
        vertexShader: "void main() {\n#include <begin_vertex>\n}"
    };

    material.onBeforeCompile(shader);

    assert.ok(shader.uniforms.uWindTime);
    assert.ok(shader.uniforms.uWindDirection);
    assert.ok(shader.uniforms.uGustScale);
    assert.ok(shader.vertexShader.includes("rootAnchor"));
    assert.ok(shader.vertexShader.includes("headInertia"));
    assert.ok(shader.vertexShader.includes("worldWave"));
    layer.update({ elapsed: 4.25, reducedMotion: false });
    assert.equal(shader.uniforms.uWindTime.value, 4.25);
    layer.update({ elapsed: 9, reducedMotion: true });
    assert.equal(shader.uniforms.uWindTime.value, 1.75);

    layer.dispose();
});

test("an explicitly empty vegetation list keeps the meadow floor free of stray weed meshes", () => {
    const maps = {
        diffuse: new THREE.Texture(),
        normal: new THREE.Texture(),
        roughness: new THREE.Texture(),
        ao: new THREE.Texture(),
        displacement: new THREE.Texture()
    };
    const fallbackGrass = new THREE.Group();
    fallbackGrass.add(new THREE.Mesh(
        new THREE.PlaneGeometry(1, 1),
        new THREE.MeshStandardMaterial({ color: 0xffffff })
    ));
    const layer = assetModule.createPhotorealMeadowLayer(THREE, {
        maps,
        grassScene: fallbackGrass,
        vegetationScenes: [],
        seed: 917
    });

    assert.equal(layer.vegetation.children.length, 0);
    layer.dispose();
});

test("volumetric dandelions use real stem, sepal, disc and petal geometry", () => {
    assert.equal(typeof assetModule.createVolumetricDandelionField, "function");

    const field = assetModule.createVolumetricDandelionField(THREE, {
        count: 8,
        petalsPerFlower: 18,
        seed: 917
    });

    assert.equal(field.root.userData.kind, "volumetric-dandelion-field");
    assert.equal(field.root.userData.flowerCount, 8);
    assert.equal(field.stems.count, 8);
    assert.equal(field.sepals.count, 8);
    assert.equal(field.centers.count, 8);
    assert.equal(field.petals.count, 8 * 18);
    assert.ok(field.stems.geometry.parameters.height > 0, "stems must be cylindrical volumes");
    assert.ok(field.centers.geometry.boundingBox.max.z - field.centers.geometry.boundingBox.min.z > 0, "flower discs must have depth");
    assert.ok(field.petals.geometry.boundingBox.max.z - field.petals.geometry.boundingBox.min.z > 0, "petals must have thickness");
    assert.ok(field.petals.material.emissive.getHex() !== 0, "yellow petals need a warm fill color in backlight");
    assert.ok(field.petals.material.emissive.r >= 0.9, "petal fill must preserve the yellow channel under the meadow HDRI");
    assert.ok(field.petals.material.emissiveIntensity >= 0.7, "yellow petals must remain readable in backlight");
    assert.equal(field.petals.castShadow, false, "dense petal rings must not crush one another into black silhouettes");

    field.update({ elapsed: 2.5, reducedMotion: false });
    assert.equal(field.root.userData.windTime, 2.5);
    field.dispose();
    assert.equal(field.root.children.length, 0);
});
