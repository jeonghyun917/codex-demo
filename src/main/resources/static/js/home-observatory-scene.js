import { clampUnit, createScenePose } from "./home-observatory-motion.js";

const TAU = Math.PI * 2;
const POINTER_LIMIT = 0.052;

function createStaticSceneController() {
    return {
        resize() {},
        setProgress() {},
        setPointer() {},
        setVisible() {},
        render() { return false; },
        getDiagnostics() { return { drawCalls: 0, triangles: 0 }; },
        dispose() {}
    };
}

function createBrushedMetalTexture(THREE) {
    const canvas = document.createElement("canvas");
    canvas.width = 256;
    canvas.height = 64;
    const context = canvas.getContext("2d");
    const image = context.createImageData(canvas.width, canvas.height);
    let seed = 0x4f425356;

    for (let y = 0; y < canvas.height; y += 1) {
        for (let x = 0; x < canvas.width; x += 1) {
            seed ^= seed << 13;
            seed ^= seed >>> 17;
            seed ^= seed << 5;
            const noise = (seed >>> 24) - 128;
            const grain = Math.max(70, Math.min(218,
                144 + noise * 0.22 + Math.sin(y * 0.72) * 13 + Math.sin(y * 2.4) * 5
            ));
            const offset = (y * canvas.width + x) * 4;
            image.data[offset] = grain;
            image.data[offset + 1] = grain;
            image.data[offset + 2] = grain;
            image.data[offset + 3] = 255;
        }
    }

    context.putImageData(image, 0, 0);
    const texture = new THREE.CanvasTexture(canvas);
    texture.wrapS = THREE.RepeatWrapping;
    texture.wrapT = THREE.RepeatWrapping;
    texture.repeat.set(1, 5);
    texture.colorSpace = THREE.NoColorSpace;
    texture.needsUpdate = true;
    return texture;
}

function createProductTexture(THREE) {
    const canvas = document.createElement("canvas");
    canvas.width = 512;
    canvas.height = 320;
    const context = canvas.getContext("2d");
    const gradient = context.createLinearGradient(0, 0, canvas.width, canvas.height);
    gradient.addColorStop(0, "#101722");
    gradient.addColorStop(1, "#05080d");
    context.fillStyle = gradient;
    context.fillRect(0, 0, canvas.width, canvas.height);

    context.strokeStyle = "rgba(160, 177, 201, 0.13)";
    context.lineWidth = 1;
    for (let x = 32; x < canvas.width; x += 48) {
        context.beginPath();
        context.moveTo(x, 0);
        context.lineTo(x, canvas.height);
        context.stroke();
    }
    for (let y = 28; y < canvas.height; y += 42) {
        context.beginPath();
        context.moveTo(0, y);
        context.lineTo(canvas.width, y);
        context.stroke();
    }

    const chart = [
        [28, 236], [76, 220], [119, 230], [164, 184], [208, 196],
        [252, 135], [298, 152], [346, 96], [393, 112], [444, 61], [486, 73]
    ];
    context.strokeStyle = "#6ea8ff";
    context.lineWidth = 4;
    context.lineJoin = "round";
    context.beginPath();
    chart.forEach(([x, y], index) => index ? context.lineTo(x, y) : context.moveTo(x, y));
    context.stroke();

    context.fillStyle = "rgba(110, 168, 255, 0.18)";
    context.lineTo(chart.at(-1)[0], canvas.height);
    context.lineTo(chart[0][0], canvas.height);
    context.closePath();
    context.fill();

    context.fillStyle = "#f5f7fa";
    chart.filter((_, index) => index % 2 === 0).forEach(([x, y]) => {
        context.beginPath();
        context.arc(x, y, 4, 0, TAU);
        context.fill();
    });

    const texture = new THREE.CanvasTexture(canvas);
    texture.colorSpace = THREE.SRGBColorSpace;
    texture.minFilter = THREE.LinearFilter;
    texture.magFilter = THREE.LinearFilter;
    texture.needsUpdate = true;
    return texture;
}

function createHousing(THREE, geometry, material, z, scale) {
    const mesh = new THREE.Mesh(geometry, material);
    mesh.rotation.x = Math.PI / 2;
    mesh.position.z = z;
    mesh.scale.setScalar(scale);
    return mesh;
}

function createRadialInstances(THREE, {
    count, geometry, material, radius, angleOffset = 0, z = 0, tangential = true
}) {
    const instances = new THREE.InstancedMesh(geometry, material, count);
    const matrix = new THREE.Matrix4();
    const position = new THREE.Vector3();
    const quaternion = new THREE.Quaternion();
    const scale = new THREE.Vector3(1, 1, 1);
    const rotation = new THREE.Euler();

    for (let index = 0; index < count; index += 1) {
        const angle = angleOffset + index / count * TAU;
        position.set(Math.cos(angle) * radius, Math.sin(angle) * radius, z);
        rotation.set(0, 0, tangential ? angle : 0);
        quaternion.setFromEuler(rotation);
        matrix.compose(position, quaternion, scale);
        instances.setMatrixAt(index, matrix);
    }
    instances.instanceMatrix.needsUpdate = true;
    return instances;
}

function updateIris(THREE, iris, openness, rotationOffset) {
    const matrix = new THREE.Matrix4();
    const position = new THREE.Vector3();
    const quaternion = new THREE.Quaternion();
    const scale = new THREE.Vector3();
    const rotation = new THREE.Euler();
    const radius = 0.55 + openness * 0.45;

    for (let index = 0; index < iris.count; index += 1) {
        const angle = index / iris.count * TAU + rotationOffset;
        position.set(Math.cos(angle) * radius, Math.sin(angle) * radius, 0.16);
        rotation.set(0, 0, angle + 0.56 - openness * 0.28);
        quaternion.setFromEuler(rotation);
        scale.set(1, 1 - openness * 0.18, 1);
        matrix.compose(position, quaternion, scale);
        iris.setMatrixAt(index, matrix);
    }
    iris.instanceMatrix.needsUpdate = true;
}

export async function createQuantCoreScene({ canvas, profile } = {}) {
    if (!canvas || !profile?.webgl || profile.name === "static") {
        return createStaticSceneController();
    }

    const THREE = await import("/js/vendor/three.module.js");
    const renderer = new THREE.WebGLRenderer({
        canvas,
        alpha: true,
        antialias: profile.name === "high",
        powerPreference: "high-performance"
    });
    renderer.outputColorSpace = THREE.SRGBColorSpace;
    renderer.toneMapping = THREE.ACESFilmicToneMapping;
    renderer.toneMappingExposure = 1.05;
    renderer.setPixelRatio(profile.pixelRatio);
    renderer.setClearColor(0x000000, 0);
    renderer.shadowMap.enabled = Boolean(profile.shadows);

    const scene = new THREE.Scene();
    scene.background = null;
    const camera = new THREE.PerspectiveCamera(32, 1, 0.1, 40);
    camera.position.set(0, 0.3, 8.8);

    const brushedMetalTexture = createBrushedMetalTexture(THREE);
    const productTexture = createProductTexture(THREE);
    const housingMaterial = new THREE.MeshPhysicalMaterial({
        color: 0x9298a0,
        metalness: 0.92,
        roughness: 0.24,
        clearcoat: 0.22,
        clearcoatRoughness: 0.3,
        roughnessMap: brushedMetalTexture
    });
    const graphiteMaterial = new THREE.MeshPhysicalMaterial({
        color: 0x15191e,
        metalness: 0.88,
        roughness: 0.29,
        clearcoat: 0.16,
        clearcoatRoughness: 0.4,
        roughnessMap: brushedMetalTexture
    });
    const bezelMaterial = new THREE.MeshPhysicalMaterial({
        color: 0xb7bcc2,
        metalness: 0.95,
        roughness: 0.2,
        clearcoat: 0.32,
        clearcoatRoughness: 0.22,
        roughnessMap: brushedMetalTexture
    });
    const lensMaterial = new THREE.MeshPhysicalMaterial({
        color: 0x0a111d,
        metalness: 0,
        roughness: 0.08,
        transmission: 0.72,
        thickness: 0.8,
        ior: 1.46,
        transparent: true,
        opacity: 0.82
    });
    const signalMaterial = new THREE.MeshStandardMaterial({
        color: 0x6ea8ff,
        emissive: 0x2458a8,
        emissiveIntensity: 2.2,
        metalness: 0.1,
        roughness: 0.2
    });
    const knurlMaterial = new THREE.MeshStandardMaterial({
        color: 0x4a5058,
        metalness: 0.94,
        roughness: 0.33
    });
    const irisMaterial = new THREE.MeshPhysicalMaterial({
        color: 0x242930,
        metalness: 0.86,
        roughness: 0.26,
        clearcoat: 0.12
    });
    const calibrationMaterial = new THREE.MeshStandardMaterial({
        color: 0xaeb8c5,
        emissive: 0x2458a8,
        emissiveIntensity: 0.25,
        metalness: 0.72,
        roughness: 0.27,
        transparent: true,
        opacity: 0.22
    });
    const productMaterial = new THREE.MeshBasicMaterial({
        map: productTexture,
        transparent: true,
        opacity: 0,
        depthWrite: false,
        toneMapped: false
    });

    const QuantCore = new THREE.Group();
    QuantCore.name = "QuantCore";
    QuantCore.scale.setScalar(profile.coreScale);
    scene.add(QuantCore);

    const segments = Math.max(48, profile.segments || 48);
    const frontHousing = createHousing(
        THREE,
        new THREE.CylinderGeometry(2.15, 2.15, 0.42, segments, 1, true),
        housingMaterial,
        0.02,
        1
    );
    const centerHousing = createHousing(
        THREE,
        new THREE.CylinderGeometry(1.88, 1.88, 0.26, segments, 1, true),
        graphiteMaterial,
        -0.1,
        1
    );
    const rearHousing = createHousing(
        THREE,
        new THREE.CylinderGeometry(1.65, 1.65, 0.34, segments, 1, true),
        bezelMaterial,
        -0.24,
        1
    );
    QuantCore.add(frontHousing, centerHousing, rearHousing);

    const knurl = createRadialInstances(THREE, {
        count: 56,
        geometry: new THREE.BoxGeometry(0.085, 0.23, 0.34),
        material: knurlMaterial,
        radius: 2.19,
        z: 0.03
    });
    knurl.name = "KnurledOuterRing";
    QuantCore.add(knurl);

    const iris = createRadialInstances(THREE, {
        count: 12,
        geometry: new THREE.BoxGeometry(1.18, 0.42, 0.045),
        material: irisMaterial,
        radius: 0.58,
        z: 0.16
    });
    iris.name = "IrisBlades";
    QuantCore.add(iris);

    const lens = new THREE.Mesh(
        new THREE.CylinderGeometry(1.16, 1.16, 0.11, segments),
        lensMaterial
    );
    lens.rotation.x = Math.PI / 2;
    lens.position.z = 0.22;
    lens.name = "OpticalLens";
    QuantCore.add(lens);

    const aperture = new THREE.Mesh(
        new THREE.TorusGeometry(0.72, 0.055, 12, segments),
        signalMaterial
    );
    aperture.position.z = 0.31;
    aperture.name = "SignalAperture";
    QuantCore.add(aperture);

    const calibrationTicks = createRadialInstances(THREE, {
        count: 48,
        geometry: new THREE.BoxGeometry(0.025, 0.17, 0.035),
        material: calibrationMaterial,
        radius: 1.48,
        z: 0.34
    });
    calibrationTicks.name = "CalibrationTicks";
    QuantCore.add(calibrationTicks);

    const productPlane = new THREE.Mesh(
        new THREE.PlaneGeometry(2.65, 1.66),
        productMaterial
    );
    productPlane.position.set(2.3, -0.04, -0.28);
    productPlane.rotation.y = -0.12;
    productPlane.visible = false;
    productPlane.name = "ProductRepresentation";
    QuantCore.add(productPlane);

    const keyLight = new THREE.DirectionalLight(0xffe9d0, 4.2);
    keyLight.position.set(-3.5, 5.2, 6);
    const rimLight = new THREE.DirectionalLight(0x9bc5ff, 3.4);
    rimLight.position.set(4.8, 1.8, -3.8);
    const fillLight = new THREE.HemisphereLight(0x9badc7, 0x040506, 0.72);
    const cobaltLight = new THREE.PointLight(0x377dff, 9, 7, 2);
    cobaltLight.position.set(0.1, 0.1, 1.8);
    scene.add(keyLight, rimLight, fillLight, cobaltLight);

    let progress = 0;
    let visible = true;
    let disposed = false;
    let dirty = true;
    let width = 0;
    let height = 0;
    const pointerTarget = { x: 0, y: 0 };
    const pointerDamped = { x: 0, y: 0 };
    const diagnostics = { drawCalls: 0, triangles: 0 };

    function resize() {
        if (disposed) {
            return;
        }
        const nextWidth = Math.max(1, Math.round(canvas.clientWidth || canvas.width || 1));
        const nextHeight = Math.max(1, Math.round(canvas.clientHeight || canvas.height || 1));
        if (nextWidth === width && nextHeight === height) {
            return;
        }
        width = nextWidth;
        height = nextHeight;
        camera.aspect = width / height;
        camera.updateProjectionMatrix();
        renderer.setSize(width, height, false);
        dirty = true;
    }

    function setProgress(value) {
        const next = clampUnit(Number(value));
        if (next !== progress) {
            progress = next;
            dirty = true;
        }
    }

    function setPointer(pointer = {}) {
        if (!profile.pointerEnabled) {
            return;
        }
        const x = Math.max(-1, Math.min(1, Number(pointer.x) || 0));
        const y = Math.max(-1, Math.min(1, Number(pointer.y) || 0));
        if (x !== pointerTarget.x || y !== pointerTarget.y) {
            pointerTarget.x = x;
            pointerTarget.y = y;
            dirty = true;
        }
    }

    function setVisible(value) {
        const next = Boolean(value);
        if (next !== visible) {
            visible = next;
            dirty = dirty || next;
        }
    }

    function render(time) {
        void time;
        if (disposed || !visible || document.hidden) {
            return false;
        }

        pointerDamped.x += (pointerTarget.x - pointerDamped.x) * 0.085;
        pointerDamped.y += (pointerTarget.y - pointerDamped.y) * 0.085;
        const pointerMoving = Math.abs(pointerTarget.x - pointerDamped.x) > 0.0005
            || Math.abs(pointerTarget.y - pointerDamped.y) > 0.0005;
        if (!dirty && !pointerMoving) {
            return false;
        }

        resize();
        const pose = createScenePose(progress, pointerDamped);
        camera.position.z = pose.cameraZ;
        camera.position.y = pose.cameraY;
        camera.lookAt(0, 0, 0);

        QuantCore.position.x = -0.35 * pose.productOpacity;
        QuantCore.rotation.y = Math.max(-POINTER_LIMIT,
            Math.min(POINTER_LIMIT, pointerDamped.x * POINTER_LIMIT));
        QuantCore.rotation.x = Math.max(-POINTER_LIMIT,
            Math.min(POINTER_LIMIT, -pointerDamped.y * POINTER_LIMIT));

        frontHousing.position.z = 0.02 + pose.housingOpen * 0.23;
        rearHousing.position.z = -0.24 - pose.housingOpen * 0.23;
        updateIris(THREE, iris, pose.housingOpen, pose.irisRotation);
        calibrationMaterial.opacity = 0.22 + pose.metricOpacity * 0.78;
        calibrationMaterial.emissiveIntensity = 0.25 + pose.metricOpacity * 0.9;
        productMaterial.opacity = pose.productOpacity * 0.9;
        productPlane.visible = pose.productOpacity > 0.01;

        renderer.render(scene, camera);
        diagnostics.drawCalls = renderer.info.render.calls;
        diagnostics.triangles = renderer.info.render.triangles;
        dirty = pointerMoving;
        return true;
    }

    function getDiagnostics() {
        return { ...diagnostics };
    }

    function dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        const geometries = new Set();
        const materials = new Set();
        const textures = new Set();
        scene.traverse((object) => {
            if (object.geometry) {
                geometries.add(object.geometry);
            }
            const values = Array.isArray(object.material) ? object.material : [object.material];
            values.filter(Boolean).forEach((material) => {
                materials.add(material);
                Object.values(material).forEach((value) => {
                    if (value?.isTexture) {
                        textures.add(value);
                    }
                });
            });
        });
        textures.forEach((texture) => texture.dispose());
        materials.forEach((material) => material.dispose());
        geometries.forEach((geometry) => geometry.dispose());
        renderer.dispose();
        renderer.forceContextLoss?.();
    }

    resize();
    return {
        resize,
        setProgress,
        setPointer,
        setVisible,
        render,
        getDiagnostics,
        dispose
    };
}
