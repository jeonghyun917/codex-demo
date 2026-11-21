const ASSET_ROOT = "/assets/contact4";

export const NATURE_ASSETS = Object.freeze({
    grassModel: `${ASSET_ROOT}/grass_bermuda_01_2k.gltf`,
    grassAlpha: `${ASSET_ROOT}/grass_bermuda_01_alpha_2k.png`,
    lushGrassModel: `${ASSET_ROOT}/grass_medium_01_2k.gltf`,
    lushGrassAlpha: `${ASSET_ROOT}/grass_medium_01_alpha_2k.png`,
    wildflowerModel: `${ASSET_ROOT}/flower_empodium_2k.gltf`,
    wildflowerAlpha: `${ASSET_ROOT}/flower_empodium_alpha_2k.png`,
    dandelionModel: `${ASSET_ROOT}/dandelion_01_2k.gltf`,
    dandelionAlpha: `${ASSET_ROOT}/dandelion_01_alpha_2k.png`,
    meadowGrassModel: `${ASSET_ROOT}/grass_medium_02_4k.gltf`,
    meadowGrassAlpha: `${ASSET_ROOT}/grass_medium_02_alpha_4k.png`,
    mossModel: `${ASSET_ROOT}/moss_01_2k.gltf`,
    mossAlpha: `${ASSET_ROOT}/moss_01_alpha_2k.png`,
    groundLushDiffuse: `${ASSET_ROOT}/leafy_grass_diff_2k.jpg`,
    groundDiffuse: `${ASSET_ROOT}/leafy_grass_diff_2k.jpg`,
    groundNormal: `${ASSET_ROOT}/leafy_grass_nor_gl_2k.jpg`,
    groundRoughness: `${ASSET_ROOT}/leafy_grass_rough_2k.jpg`,
    groundAo: `${ASSET_ROOT}/leafy_grass_ao_2k.jpg`,
    groundDisplacement: `${ASSET_ROOT}/leafy_grass_disp_2k.jpg`,
    environment: `${ASSET_ROOT}/wildflower_field_2k.hdr`
});

export function createPhotorealMeadowLayer(THREE, options) {
    const {
        maps,
        grassScene,
        vegetationScenes,
        grassInstanceCount = 36,
        seed = 917
    } = options;
    const root = new THREE.Group();
    root.userData.kind = "photoreal-meadow-layer";

    configureTexture(THREE, maps.diffuse, true, 5, 4.25);
    configureTexture(THREE, maps.normal, false, 5, 4.25);
    configureTexture(THREE, maps.roughness, false, 5, 4.25);
    configureTexture(THREE, maps.ao, false, 5, 4.25);
    configureTexture(THREE, maps.displacement, false, 5, 4.25);

    const groundGeometry = new THREE.PlaneGeometry(42, 34, 192, 144);
    groundGeometry.rotateX(-Math.PI / 2);
    if (groundGeometry.attributes.uv) {
        groundGeometry.setAttribute("uv1", groundGeometry.attributes.uv.clone());
        groundGeometry.setAttribute("uv2", groundGeometry.attributes.uv.clone());
    }
    const groundMaterial = new THREE.MeshStandardMaterial({
        map: maps.diffuse,
        normalMap: maps.normal,
        normalScale: new THREE.Vector2(0.48, 0.48),
        roughnessMap: maps.roughness,
        roughness: 0.96,
        aoMap: maps.ao,
        aoMapIntensity: 0.68,
        displacementMap: maps.displacement,
        displacementScale: 0.11,
        displacementBias: -0.05,
        color: 0xe6efc7,
        envMapIntensity: 0.78,
        emissive: 0x10180b,
        emissiveIntensity: 0.035
    });
    const ground = new THREE.Mesh(groundGeometry, groundMaterial);
    ground.position.set(0, -1.51, -5.4);
    ground.receiveShadow = true;
    root.add(ground);

    const vegetation = new THREE.Group();
    vegetation.name = "scanned-vegetation";
    const random = mulberry32(seed);
    const families = Array.isArray(vegetationScenes) ? vegetationScenes : [{
        name: "bermuda-grass",
        scene: grassScene,
        count: grassInstanceCount,
        scale: [0.18, 0.5],
        zRange: [2.1, -23.4],
        wind: 1
    }];

    families.forEach((family, familyIndex) => {
        if (!family.scene) return;
        const familyGroup = new THREE.Group();
        familyGroup.name = family.name || `vegetation-${familyIndex}`;
        const sourceMeshes = [];
        const includedNames = (family.includeNames || []).map((name) => name.toLowerCase());
        const excludedNames = (family.excludeNames || []).map((name) => name.toLowerCase());
        family.scene.updateMatrixWorld(true);
        family.scene.traverse((object) => {
            const name = (object.name || "").toLowerCase();
            const included = !includedNames.length || includedNames.some((token) => name.includes(token));
            const excluded = excludedNames.some((token) => name.includes(token));
            if (included && !excluded && object.isMesh && object.geometry && object.material) sourceMeshes.push(object);
        });
        const totalCount = Math.max(1, family.count ?? grassInstanceCount);
        const scaleRange = family.scale || [0.18, 0.5];
        const zRange = family.zRange || [2.1, -23.4];
        const centerGap = THREE.MathUtils.clamp(family.centerGap ?? 0, 0, 0.72);
        const edgeBias = THREE.MathUtils.clamp(family.edgeBias ?? 0, 0, 1);
        const clusterCount = Math.max(0, Math.min(24, Math.round(family.clusterCount ?? 0)));
        const clusterSpread = THREE.MathUtils.clamp(family.clusterSpread ?? 0.12, 0.02, 0.42);
        const quietZone = THREE.MathUtils.clamp(family.quietZone ?? 0, 0, 0.5);
        const clusterCenters = Array.from({ length: clusterCount }, (_, index) => ({
            depth: THREE.MathUtils.clamp((index + 0.25 + random() * 0.5) / clusterCount, 0.02, 0.98),
            x: random() * 1.84 - 0.92
        }));
        familyGroup.userData.distribution = clusterCount ? {
            mode: "clustered",
            clusterCount,
            clusterSpread,
            quietZone
        } : {
            mode: "scattered",
            clusterCount: 0,
            clusterSpread,
            quietZone
        };

        sourceMeshes.forEach((source, meshIndex) => {
            const count = Math.floor(totalCount / sourceMeshes.length)
                + (meshIndex < totalCount % sourceMeshes.length ? 1 : 0);
            if (count === 0) return;
            let geometry = source.geometry.clone();
            geometry.applyMatrix4(source.matrixWorld);
            if ((family.minHeightRatio ?? 0) > 0) {
                geometry = cropGeometryByHeight(THREE, geometry, family.minHeightRatio);
            }
            geometry.computeBoundingBox();
            const sourceHeight = Math.max(0.001, geometry.boundingBox.max.y - geometry.boundingBox.min.y);
            const targetHeight = family.targetHeight ?? 0.65;
            const horizontalScale = Math.max(0.1, family.horizontalScale ?? 1);
            const normalizedScale = targetHeight / sourceHeight;
            const centerX = (geometry.boundingBox.min.x + geometry.boundingBox.max.x) * 0.5;
            const centerZ = (geometry.boundingBox.min.z + geometry.boundingBox.max.z) * 0.5;
            geometry.translate(-centerX, -geometry.boundingBox.min.y, -centerZ);
            geometry.scale(normalizedScale * horizontalScale, normalizedScale, normalizedScale * horizontalScale);
            geometry.computeBoundingBox();
            geometry.computeVertexNormals();
            const material = cloneGrassMaterial(
                THREE,
                source.material,
                familyIndex * 10 + meshIndex,
                family.wind ?? 1,
                family.alphaMap,
                family.alphaTest
            );
            const instances = new THREE.InstancedMesh(geometry, material, count);
            instances.castShadow = family.castShadow !== false;
            instances.receiveShadow = true;
            instances.frustumCulled = false;
            const matrix = new THREE.Matrix4();
            const quaternion = new THREE.Quaternion();
            const position = new THREE.Vector3();
            const scale = new THREE.Vector3();
            const tint = new THREE.Color();
            for (let index = 0; index < count; index++) {
                const cluster = clusterCenters.length
                    ? clusterCenters[Math.floor(random() * clusterCenters.length)]
                    : null;
                const jitterDepth = (random() + random() - 1) * clusterSpread;
                const depth = cluster
                    ? THREE.MathUtils.clamp(cluster.depth + jitterDepth, 0.02, 0.99)
                    : 0.04 + random() * 0.96;
                const z = THREE.MathUtils.lerp(zRange[0], zRange[1], depth);
                let rawX = cluster
                    ? THREE.MathUtils.clamp(cluster.x + (random() + random() - 1) * clusterSpread * 1.35, -1, 1)
                    : random() * 2 - 1;
                const quietWidth = quietZone * THREE.MathUtils.clamp(1 - depth / 0.62, 0, 1);
                if (Math.abs(rawX) < quietWidth) {
                    rawX = (Math.sign(rawX) || (random() < 0.5 ? -1 : 1))
                        * (quietWidth + random() * clusterSpread * 0.35);
                }
                const direction = Math.sign(rawX) || 1;
                const edgeWeighted = Math.pow(Math.abs(rawX), 1 - edgeBias * 0.62);
                const normalizedX = direction * (centerGap + (1 - centerGap) * edgeWeighted);
                const x = normalizedX * (4.8 + depth * 15.4);
                const baseSize = THREE.MathUtils.lerp(scaleRange[0], scaleRange[1], random());
                const size = baseSize * (0.78 + depth * 0.3);
                position.set(x, terrainHeight(x, z) - 1.48 + (family.yOffset ?? 0), z);
                quaternion.setFromEuler(new THREE.Euler(
                    (random() - 0.5) * 0.025,
                    random() * Math.PI * 2,
                    (random() - 0.5) * 0.04
                ));
                scale.set(size * (0.82 + random() * 0.28), size * (0.9 + random() * 0.22), size * (0.82 + random() * 0.28));
                matrix.compose(position, quaternion, scale);
                instances.setMatrixAt(index, matrix);
                const warmth = familyIndex === families.length - 1 ? 0.035 : 0;
                tint.setRGB(0.88 + random() * 0.1 + warmth, 0.91 + random() * 0.08, 0.82 + random() * 0.1 - warmth);
                instances.setColorAt(index, tint);
            }
            instances.instanceMatrix.needsUpdate = true;
            if (instances.instanceColor) instances.instanceColor.needsUpdate = true;
            familyGroup.add(instances);
        });
        vegetation.add(familyGroup);
    });
    root.add(vegetation);

    const update = ({ elapsed = 0, reducedMotion = false } = {}) => {
        const time = reducedMotion ? 1.75 : elapsed;
        vegetation.children.forEach((family, familyIndex) => family.children.forEach((mesh, meshIndex) => {
            const shader = mesh.material.userData.windShader;
            if (shader) shader.uniforms.uWindTime.value = time;
            mesh.position.x = Math.sin(time * 0.08 + familyIndex + meshIndex) * 0.008;
        }));
    };

    const dispose = () => {
        const geometries = new Set();
        const materials = new Set();
        root.traverse((object) => {
            if (object.geometry) geometries.add(object.geometry);
            if (Array.isArray(object.material)) object.material.forEach((material) => materials.add(material));
            else if (object.material) materials.add(object.material);
        });
        geometries.forEach((geometry) => geometry.dispose());
        materials.forEach((material) => material.dispose());
        Object.values(maps).forEach((texture) => texture?.dispose?.());
        families.forEach((family) => family.alphaMap?.dispose?.());
        root.clear();
    };

    return { root, ground, grass: vegetation.children[0] || vegetation, vegetation, update, dispose };
}

export function createVolumetricDandelionField(THREE, options = {}) {
    const {
        count = 220,
        petalsPerFlower = 22,
        seed = 917
    } = options;
    const flowerCount = Math.max(1, Math.round(count));
    const petalCount = Math.max(12, Math.round(petalsPerFlower));
    const random = mulberry32(seed);
    const root = new THREE.Group();
    root.name = "volumetric-dandelions";
    root.userData.kind = "volumetric-dandelion-field";
    root.userData.flowerCount = flowerCount;
    root.userData.windTime = 0;

    const stemGeometry = new THREE.CylinderGeometry(0.012, 0.018, 1, 7, 4, false);
    const sepalGeometry = new THREE.ConeGeometry(0.07, 0.14, 9, 2, false);
    const centerGeometry = new THREE.IcosahedronGeometry(0.048, 2);
    const petalGeometry = new THREE.SphereGeometry(1, 8, 4);
    [stemGeometry, sepalGeometry, centerGeometry, petalGeometry].forEach((geometry) => {
        geometry.computeBoundingBox();
        geometry.computeBoundingSphere();
    });

    const stemMaterial = createDandelionMaterial(THREE, {
        color: 0x5f8c31,
        emissive: 0x102309,
        emissiveIntensity: 0.12,
        roughness: 0.88,
        wind: 0.9,
        phaseOffset: 0.2
    });
    const sepalMaterial = createDandelionMaterial(THREE, {
        color: 0x5c812d,
        emissive: 0x142407,
        emissiveIntensity: 0.12,
        roughness: 0.9,
        wind: 0.78,
        phaseOffset: 1.1
    });
    const centerMaterial = createDandelionMaterial(THREE, {
        color: 0xffb312,
        emissive: 0xff8c00,
        emissiveIntensity: 0.66,
        roughness: 0.72,
        wind: 0.82,
        phaseOffset: 2.2
    });
    const petalMaterial = createDandelionMaterial(THREE, {
        color: 0xffe23d,
        emissive: 0xffc400,
        emissiveIntensity: 0.9,
        roughness: 0.76,
        wind: 0.86,
        phaseOffset: 3.4
    });

    const stems = new THREE.InstancedMesh(stemGeometry, stemMaterial, flowerCount);
    const sepals = new THREE.InstancedMesh(sepalGeometry, sepalMaterial, flowerCount);
    const centers = new THREE.InstancedMesh(centerGeometry, centerMaterial, flowerCount);
    const petals = new THREE.InstancedMesh(petalGeometry, petalMaterial, flowerCount * petalCount);
    [stems, sepals, centers, petals].forEach((mesh) => {
        mesh.castShadow = true;
        mesh.receiveShadow = true;
        mesh.frustumCulled = false;
    });
    petals.castShadow = false;
    stems.name = "dandelion-stems";
    sepals.name = "dandelion-sepals";
    centers.name = "dandelion-discs";
    petals.name = "dandelion-petals";

    const matrix = new THREE.Matrix4();
    const quaternion = new THREE.Quaternion();
    const position = new THREE.Vector3();
    const scale = new THREE.Vector3();
    const base = new THREE.Vector3();
    const head = new THREE.Vector3();
    const direction = new THREE.Vector3();
    const yAxis = new THREE.Vector3(0, 1, 0);
    const tint = new THREE.Color();
    let petalIndex = 0;

    for (let flower = 0; flower < flowerCount; flower++) {
        const depth = 0.025 + random() * 0.975;
        const z = THREE.MathUtils.lerp(1.8, -23.8, depth);
        const x = (random() * 2 - 1) * (5.2 + depth * 15.6);
        const height = THREE.MathUtils.lerp(0.42, 0.78, random()) * (1 - depth * 0.12);
        const leanX = (random() - 0.5) * height * 0.18;
        const leanZ = (random() - 0.5) * height * 0.12;
        const flowerScale = 0.82 + random() * 0.42;
        base.set(x, terrainHeight(x, z) - 1.46, z);
        head.set(x + leanX, base.y + height, z + leanZ);
        direction.copy(head).sub(base).normalize();
        quaternion.setFromUnitVectors(yAxis, direction);

        position.copy(base).add(head).multiplyScalar(0.5);
        scale.set(flowerScale, height, flowerScale);
        matrix.compose(position, quaternion, scale);
        stems.setMatrixAt(flower, matrix);
        tint.setHSL(0.245 + random() * 0.025, 0.46 + random() * 0.12, 0.33 + random() * 0.08);
        stems.setColorAt(flower, tint);

        position.copy(head).addScaledVector(direction, -0.055 * flowerScale);
        scale.setScalar(flowerScale * (0.88 + random() * 0.14));
        matrix.compose(position, quaternion, scale);
        sepals.setMatrixAt(flower, matrix);
        tint.setHSL(0.24 + random() * 0.025, 0.5, 0.31 + random() * 0.07);
        sepals.setColorAt(flower, tint);

        position.copy(head).addScaledVector(direction, 0.018 * flowerScale);
        scale.setScalar(flowerScale * (0.88 + random() * 0.18));
        matrix.compose(position, quaternion, scale);
        centers.setMatrixAt(flower, matrix);
        tint.setHSL(0.11 + random() * 0.012, 0.82, 0.43 + random() * 0.08);
        centers.setColorAt(flower, tint);

        const phase = random() * Math.PI * 2;
        for (let petal = 0; petal < petalCount; petal++) {
            const innerRing = petal % 3 === 0;
            const angle = phase + petal / petalCount * Math.PI * 2 + (random() - 0.5) * 0.16;
            const lift = innerRing ? 0.5 + random() * 0.14 : 0.15 + random() * 0.15;
            const length = (innerRing ? 0.11 : 0.17) * flowerScale * (0.88 + random() * 0.22);
            direction.set(Math.cos(angle), lift, Math.sin(angle)).normalize();
            quaternion.setFromUnitVectors(yAxis, direction);
            position.copy(head).addScaledVector(direction, length * 0.54);
            position.y += 0.012 * flowerScale;
            scale.set(
                0.014 * flowerScale * (0.82 + random() * 0.28),
                length * 0.5,
                0.0065 * flowerScale * (0.82 + random() * 0.24)
            );
            matrix.compose(position, quaternion, scale);
            petals.setMatrixAt(petalIndex, matrix);
            tint.setHSL(0.125 + random() * 0.018, 0.86 + random() * 0.09, 0.5 + random() * 0.1);
            petals.setColorAt(petalIndex, tint);
            petalIndex++;
        }
    }

    [stems, sepals, centers, petals].forEach((mesh) => {
        mesh.instanceMatrix.needsUpdate = true;
        if (mesh.instanceColor) mesh.instanceColor.needsUpdate = true;
        root.add(mesh);
    });

    const materials = [stemMaterial, sepalMaterial, centerMaterial, petalMaterial];
    const geometries = [stemGeometry, sepalGeometry, centerGeometry, petalGeometry];
    const update = ({ elapsed = 0, reducedMotion = false } = {}) => {
        const windTime = reducedMotion ? 1.75 : elapsed;
        root.userData.windTime = windTime;
        materials.forEach((material) => {
            const shader = material.userData.windShader;
            if (shader) shader.uniforms.uWindTime.value = windTime;
        });
    };
    const dispose = () => {
        geometries.forEach((geometry) => geometry.dispose());
        materials.forEach((material) => material.dispose());
        root.clear();
    };

    return { root, stems, sepals, centers, petals, update, dispose };
}

function createDandelionMaterial(THREE, { color, emissive, emissiveIntensity, roughness, wind, phaseOffset }) {
    const material = new THREE.MeshStandardMaterial({
        color,
        emissive,
        emissiveIntensity,
        roughness,
        metalness: 0,
        vertexColors: true,
        side: THREE.DoubleSide,
        envMapIntensity: 0.82
    });
    material.onBeforeCompile = (shader) => {
        shader.uniforms.uWindTime = { value: 0 };
        shader.vertexShader = shader.vertexShader
            .replace("void main() {", "uniform float uWindTime;\nvoid main() {")
            .replace("#include <begin_vertex>", `
                #include <begin_vertex>
                float flowerPhase = instanceMatrix[3].x * 0.33 + instanceMatrix[3].z * 0.19 + ${phaseOffset.toFixed(2)};
                float flowerWind = sin(uWindTime * 0.72 + flowerPhase) * 0.035
                    + sin(uWindTime * 1.37 + flowerPhase * 1.71) * 0.012;
                transformed.x += flowerWind * ${wind.toFixed(3)};
                transformed.z += flowerWind * 0.32 * ${wind.toFixed(3)};
            `);
        material.userData.windShader = shader;
    };
    material.customProgramCacheKey = () => `contact4-volumetric-dandelion-${phaseOffset}`;
    return material;
}

function configureTexture(THREE, texture, color, repeatX, repeatY) {
    texture.wrapS = THREE.RepeatWrapping;
    texture.wrapT = THREE.RepeatWrapping;
    texture.repeat.set(repeatX, repeatY);
    texture.anisotropy = 8;
    if (color) texture.colorSpace = THREE.SRGBColorSpace;
    texture.needsUpdate = true;
}

function cropGeometryByHeight(THREE, geometry, ratio) {
    const minHeightRatio = THREE.MathUtils.clamp(ratio, 0, 0.94);
    const source = geometry.index ? geometry.toNonIndexed() : geometry.clone();
    source.computeBoundingBox();
    const position = source.attributes.position;
    const minY = source.boundingBox.min.y;
    const maxY = source.boundingBox.max.y;
    const threshold = THREE.MathUtils.lerp(minY, maxY, minHeightRatio);
    const keptTriangles = [];

    for (let vertex = 0; vertex + 2 < position.count; vertex += 3) {
        const centroidY = (
            position.getY(vertex)
            + position.getY(vertex + 1)
            + position.getY(vertex + 2)
        ) / 3;
        if (centroidY >= threshold) keptTriangles.push(vertex);
    }
    if (!keptTriangles.length) return geometry;

    const cropped = new THREE.BufferGeometry();
    Object.entries(source.attributes).forEach(([name, attribute]) => {
        const values = [];
        keptTriangles.forEach((start) => {
            for (let vertex = start; vertex < start + 3; vertex++) {
                for (let component = 0; component < attribute.itemSize; component++) {
                    values.push(attribute.array[vertex * attribute.itemSize + component]);
                }
            }
        });
        const AttributeArray = attribute.array.constructor;
        cropped.setAttribute(name, new THREE.BufferAttribute(
            new AttributeArray(values),
            attribute.itemSize,
            attribute.normalized
        ));
    });
    cropped.computeBoundingBox();
    cropped.computeBoundingSphere();
    return cropped;
}

function cloneGrassMaterial(THREE, sourceMaterial, meshIndex, windStrength = 1, alphaMap, alphaTest = 0.2) {
    const material = sourceMaterial.clone();
    material.side = THREE.DoubleSide;
    material.alphaMap = alphaMap || material.alphaMap;
    material.alphaTest = Math.max(material.alphaTest || 0, alphaTest);
    material.transparent = false;
    material.roughness = Math.max(0.68, material.roughness ?? 0.82);
    material.envMapIntensity = 0.86;
    if (material.emissive) {
        material.emissive.setHex(0x10190b);
        material.emissiveIntensity = 0.08;
    }
    material.onBeforeCompile = (shader) => {
        shader.uniforms.uWindTime = { value: 0 };
        shader.uniforms.uWindDirection = { value: new THREE.Vector2(0.92, 0.38).normalize() };
        shader.uniforms.uGustScale = { value: 0.052 * windStrength };
        shader.vertexShader = shader.vertexShader
            .replace("void main() {", `
                uniform float uWindTime;
                uniform vec2 uWindDirection;
                uniform float uGustScale;
                void main() {
            `)
            .replace("#include <begin_vertex>", `
                #include <begin_vertex>
                float instancePhase = instanceMatrix[3].x * 0.47 + instanceMatrix[3].z * 0.21 + ${meshIndex.toFixed(1)};
                float rootAnchor = smoothstep(0.015, 0.22, max(position.y, 0.0));
                float headInertia = smoothstep(0.62, 1.12, max(position.y, 0.0));
                float worldWave = sin(
                    uWindTime * 0.62
                    + dot(instanceMatrix[3].xz, uWindDirection) * 0.31
                    + instancePhase * 0.18
                );
                float turbulence = sin(uWindTime * 1.43 + instancePhase * 1.71) * 0.34
                    + sin(uWindTime * 2.07 + instancePhase * 0.79) * 0.13;
                float delayedHead = sin(uWindTime * 0.51 + instancePhase * 0.21 - 0.48) * headInertia;
                float breeze = (worldWave + turbulence * 0.42 + delayedHead * 0.28)
                    * uGustScale * rootAnchor;
                transformed.x += breeze * uWindDirection.x;
                transformed.z += breeze * uWindDirection.y;
                transformed.y -= abs(breeze) * 0.055 * rootAnchor;
            `);
        material.userData.windShader = shader;
    };
    material.customProgramCacheKey = () => `contact4-photoreal-grass-${meshIndex}`;
    return material;
}

function terrainHeight(x, z) {
    return Math.sin(x * 0.23) * 0.24 + Math.sin(z * 0.19 + 1.7) * 0.31 + Math.sin((x + z) * 0.11) * 0.16;
}

function mulberry32(seed) {
    let value = seed >>> 0;
    return () => {
        value |= 0;
        value = value + 0x6d2b79f5 | 0;
        let result = Math.imul(value ^ value >>> 15, 1 | value);
        result = result + Math.imul(result ^ result >>> 7, 61 | result) ^ result;
        return ((result ^ result >>> 14) >>> 0) / 4294967296;
    };
}
