export function createQuantEngine(THREE, quality) {
    const group = new THREE.Group();
    group.position.set(0, -0.15, -4.4);

    const rotors = [];
    const activationLights = [];
    const emissiveMaterials = [];
    const materials = createMaterials(THREE, quality);

    addPedestal(THREE, group, quality, materials);
    addTurbineHousing(THREE, group, quality, materials);
    addInternalRotor(THREE, group, quality, materials, rotors);
    addObservationLens(THREE, group, quality, materials, emissiveMaterials);
    addServiceDetails(THREE, group, quality, materials, emissiveMaterials);

    [
        [-1.25, 0.2, 1.15, 0x4d86ff, 2.8],
        [1.4, 0.05, 0.9, 0x7eb5ff, 2.2],
        [0, -0.95, 0.4, 0x245dff, 1.6],
        [-2.45, 1.7, 0.3, 0xff9a60, 2.8]
    ].forEach((configuration) => {
        const light = new THREE.PointLight(configuration[3], 0, configuration[4], 2.2);
        light.position.set(configuration[0], configuration[1], configuration[2]);
        activationLights.push(light);
        group.add(light);
    });

    return {
        group,
        update(time, progress) {
            const activation = smoothstep(0.26, 0.68, progress);
            const intake = smoothstep(0.62, 0.96, progress);

            rotors.forEach((rotor, index) => {
                const direction = index % 2 === 0 ? -1 : 1;
                rotor.rotation.z = rotor.userData.baseRotation
                    + time * rotor.userData.speed * activation * direction;
            });

            activationLights.forEach((light, index) => {
                light.intensity = activation * (1.4 + index * 0.38) + intake * 0.8;
            });

            emissiveMaterials.forEach((material, index) => {
                const pulse = Math.sin(time * (1.35 + index * 0.16) + index) * 0.08 * activation;
                material.emissiveIntensity = material.userData.idleIntensity
                    + activation * material.userData.activeIntensity
                    + intake * material.userData.intakeIntensity
                    + pulse;
            });
        }
    };
}

function createMaterials(THREE, quality) {
    const housing = new THREE.MeshPhysicalMaterial({
        color: 0x1c2530,
        metalness: 0.82,
        roughness: 0.34,
        clearcoat: 0.35,
        clearcoatRoughness: 0.38,
        envMapIntensity: 0.82
    });
    const blackMetal = new THREE.MeshStandardMaterial({
        color: 0x0a1018,
        metalness: 0.62,
        roughness: 0.48,
        envMapIntensity: 0.72
    });
    const steel = new THREE.MeshStandardMaterial({
        color: 0x7f8a98,
        metalness: 0.96,
        roughness: 0.23,
        envMapIntensity: 1.08
    });
    const brushedSteel = new THREE.MeshStandardMaterial({
        color: 0xa4afbb,
        metalness: 0.94,
        roughness: 0.34,
        envMapIntensity: 1.02
    });
    const rotorMetal = new THREE.MeshStandardMaterial({
        color: 0x334050,
        metalness: 0.9,
        roughness: 0.26,
        envMapIntensity: 0.9
    });
    const glass = new THREE.MeshPhysicalMaterial({
        color: 0x234a72,
        metalness: 0,
        roughness: 0.08,
        transmission: quality.name === "low" ? 0.12 : 0.78,
        thickness: 0.42,
        ior: 1.46,
        transparent: true,
        opacity: quality.name === "low" ? 0.22 : 0.32,
        depthWrite: false,
        clearcoat: 0.86,
        clearcoatRoughness: 0.12
    });
    const blueStatus = createEmissiveMaterial(THREE, 0x0c1a31, 0x3478ff, 0.22, 1.65, 0.68);
    const blueCore = createEmissiveMaterial(THREE, 0x0a1627, 0x2f6fcc, 0.12, 0.85, 0.25);
    const amberStatus = createEmissiveMaterial(THREE, 0x26170b, 0xff9c4a, 0.12, 0.9, 0.22);

    return {
        housing,
        blackMetal,
        steel,
        brushedSteel,
        rotorMetal,
        glass,
        blueStatus,
        blueCore,
        amberStatus
    };
}

function createEmissiveMaterial(THREE, color, emissive, idleIntensity, activeIntensity, intakeIntensity) {
    const material = new THREE.MeshStandardMaterial({
        color,
        emissive,
        emissiveIntensity: idleIntensity,
        metalness: 0.34,
        roughness: 0.32
    });
    material.userData.idleIntensity = idleIntensity;
    material.userData.activeIntensity = activeIntensity;
    material.userData.intakeIntensity = intakeIntensity;
    return material;
}

function addPedestal(THREE, group, quality, materials) {
    [
        [3.45, 3.72, 0.28, -2.08, materials.blackMetal],
        [3.1, 3.36, 0.34, -1.78, materials.housing],
        [2.72, 2.96, 0.26, -1.48, materials.steel],
        [2.46, 2.64, 0.34, -1.2, materials.housing]
    ].forEach((layer) => {
        const mesh = new THREE.Mesh(
            new THREE.CylinderGeometry(layer[0], layer[1], layer[2], quality.segments),
            layer[4]
        );
        mesh.position.y = layer[3];
        setShadow(mesh, quality);
        group.add(mesh);
    });

    [3.12, 2.7, 2.36].forEach((radius, index) => {
        const trim = new THREE.Mesh(
            new THREE.TorusGeometry(radius, 0.035 + index * 0.012, 12, quality.segments * 2),
            index === 1 ? materials.blueStatus : materials.brushedSteel
        );
        trim.rotation.x = Math.PI / 2;
        trim.position.y = -1.68 + index * 0.25;
        group.add(trim);
    });

    const treadGeometry = new THREE.BoxGeometry(0.055, 0.12, 0.22);
    const treadCount = quality.name === "low" ? 24 : 40;
    for (let index = 0; index < treadCount; index += 1) {
        const angle = index / treadCount * Math.PI * 2;
        const tread = new THREE.Mesh(treadGeometry, materials.brushedSteel);
        tread.position.set(Math.cos(angle) * 3.22, -1.78, Math.sin(angle) * 3.22);
        tread.rotation.y = -angle;
        group.add(tread);
    }
}

function addTurbineHousing(THREE, group, quality, materials) {
    const barrel = new THREE.Mesh(
        new THREE.CylinderGeometry(2.42, 2.48, 1.3, quality.segments, 1, true),
        materials.housing
    );
    barrel.rotation.x = Math.PI / 2;
    barrel.position.z = -0.42;
    setShadow(barrel, quality);
    group.add(barrel);

    const rearCasing = new THREE.Mesh(
        new THREE.TorusGeometry(2.24, 0.24, 18, quality.segments * 2),
        materials.blackMetal
    );
    rearCasing.position.z = -1.02;
    setShadow(rearCasing, quality);
    group.add(rearCasing);

    const frontCasing = new THREE.Mesh(
        new THREE.TorusGeometry(2.13, 0.31, 22, quality.segments * 2),
        materials.housing
    );
    frontCasing.position.z = 0.22;
    setShadow(frontCasing, quality);
    group.add(frontCasing);

    const innerGasket = new THREE.Mesh(
        new THREE.TorusGeometry(1.77, 0.13, 16, quality.segments * 2),
        materials.blackMetal
    );
    innerGasket.position.z = 0.42;
    group.add(innerGasket);

    const machinedLip = new THREE.Mesh(
        new THREE.TorusGeometry(1.7, 0.055, 12, quality.segments * 2),
        materials.brushedSteel
    );
    machinedLip.position.z = 0.51;
    group.add(machinedLip);

    const pylonGeometry = createBeveledPanelGeometry(THREE, 0.72, 4.55, 1.04, 0.16);
    [-1, 1].forEach((side) => {
        const pylon = new THREE.Mesh(pylonGeometry, materials.housing);
        pylon.position.set(side * 2.88, -0.05, -0.5);
        setShadow(pylon, quality);
        group.add(pylon);

        const inset = new THREE.Mesh(
            createBeveledPanelGeometry(THREE, 0.28, 3.72, 1.08, 0.08),
            materials.blackMetal
        );
        inset.position.set(side * 2.88, -0.05, -0.48);
        group.add(inset);

        const foot = new THREE.Mesh(
            createBeveledPanelGeometry(THREE, 1.18, 0.58, 1.25, 0.11),
            materials.housing
        );
        foot.position.set(side * 2.55, -1.58, -0.44);
        foot.rotation.z = side * -0.16;
        setShadow(foot, quality);
        group.add(foot);
    });

    const boltGeometry = new THREE.CylinderGeometry(0.052, 0.052, 0.11, 12);
    for (let index = 0; index < 18; index += 1) {
        const angle = index / 18 * Math.PI * 2;
        const bolt = new THREE.Mesh(boltGeometry, materials.brushedSteel);
        bolt.rotation.x = Math.PI / 2;
        bolt.position.set(Math.cos(angle) * 2.14, Math.sin(angle) * 2.14, 0.55);
        group.add(bolt);
    }

    const finGeometry = new THREE.BoxGeometry(0.16, 0.42, 0.82);
    const finCount = quality.name === "low" ? 10 : 16;
    for (let index = 0; index < finCount; index += 1) {
        const angle = index / finCount * Math.PI * 2;
        const fin = new THREE.Mesh(finGeometry, materials.blackMetal);
        fin.position.set(Math.cos(angle) * 2.56, Math.sin(angle) * 2.56, -0.43);
        fin.rotation.z = angle - Math.PI / 2;
        group.add(fin);
    }
}

function addInternalRotor(THREE, group, quality, materials, rotors) {
    const backplate = new THREE.Mesh(
        new THREE.CylinderGeometry(1.62, 1.62, 0.28, quality.segments),
        materials.blackMetal
    );
    backplate.rotation.x = Math.PI / 2;
    backplate.position.z = -0.04;
    group.add(backplate);

    const stator = new THREE.Group();
    stator.position.z = 0.06;
    const statorBladeGeometry = createRotorBladeGeometry(THREE, 0.12, 0.025);
    const statorCount = quality.name === "low" ? 10 : 14;
    for (let index = 0; index < statorCount; index += 1) {
        const blade = new THREE.Mesh(statorBladeGeometry, materials.rotorMetal);
        blade.rotation.z = index / statorCount * Math.PI * 2;
        stator.add(blade);
    }
    stator.userData.baseRotation = 0;
    stator.userData.speed = 0.08;
    rotors.push(stator);
    group.add(stator);

    const rotor = new THREE.Group();
    rotor.position.z = 0.22;
    const rotorBladeGeometry = createRotorBladeGeometry(THREE, 0.15, -0.04);
    const rotorCount = quality.name === "low" ? 9 : 12;
    for (let index = 0; index < rotorCount; index += 1) {
        const blade = new THREE.Mesh(rotorBladeGeometry, materials.brushedSteel);
        blade.rotation.z = index / rotorCount * Math.PI * 2;
        rotor.add(blade);
    }

    const rotorRim = new THREE.Mesh(
        new THREE.TorusGeometry(1.43, 0.045, 10, quality.segments * 2),
        materials.blueStatus
    );
    rotor.add(rotorRim);
    rotor.userData.baseRotation = 0.12;
    rotor.userData.speed = 0.16;
    rotors.push(rotor);
    group.add(rotor);

    const hub = new THREE.Mesh(
        new THREE.CylinderGeometry(0.5, 0.6, 0.42, quality.segments),
        materials.housing
    );
    hub.rotation.x = Math.PI / 2;
    hub.position.z = 0.31;
    setShadow(hub, quality);
    group.add(hub);

    const hubFace = new THREE.Mesh(
        new THREE.CylinderGeometry(0.36, 0.44, 0.12, quality.segments),
        materials.brushedSteel
    );
    hubFace.rotation.x = Math.PI / 2;
    hubFace.position.z = 0.57;
    group.add(hubFace);
}

function addObservationLens(THREE, group, quality, materials, emissiveMaterials) {
    const lens = new THREE.Mesh(
        new THREE.SphereGeometry(1.62, quality.segments, Math.max(18, quality.segments / 2)),
        materials.glass
    );
    lens.position.z = 0.58;
    lens.scale.z = 0.16;
    group.add(lens);

    const retainer = new THREE.Mesh(
        new THREE.TorusGeometry(1.62, 0.055, 12, quality.segments * 2),
        materials.steel
    );
    retainer.position.z = 0.67;
    group.add(retainer);

    const coreWindow = new THREE.Mesh(
        new THREE.CircleGeometry(0.25, quality.segments),
        materials.blueCore
    );
    coreWindow.position.z = 0.79;
    group.add(coreWindow);

    const apertureRing = new THREE.Mesh(
        new THREE.TorusGeometry(0.48, 0.024, 10, quality.segments * 2),
        materials.blueStatus
    );
    apertureRing.position.z = 0.76;
    group.add(apertureRing);

    const pupil = new THREE.Mesh(
        new THREE.CircleGeometry(0.105, quality.segments),
        materials.blackMetal
    );
    pupil.position.z = 0.815;
    group.add(pupil);

    const coreLens = new THREE.Mesh(
        new THREE.SphereGeometry(0.31, quality.segments, Math.max(14, quality.segments / 2)),
        materials.glass
    );
    coreLens.position.z = 0.82;
    coreLens.scale.z = 0.2;
    group.add(coreLens);

    const coreRetainer = new THREE.Mesh(
        new THREE.TorusGeometry(0.31, 0.026, 10, quality.segments * 2),
        materials.brushedSteel
    );
    coreRetainer.position.z = 0.875;
    group.add(coreRetainer);

    emissiveMaterials.push(materials.blueCore, materials.blueStatus);
}

function addServiceDetails(THREE, group, quality, materials, emissiveMaterials) {
    [-1, 1].forEach((side) => {
        const curve = new THREE.CatmullRomCurve3([
            new THREE.Vector3(side * 3.55, -1.72, -0.86),
            new THREE.Vector3(side * 3.16, -1.24, -1.02),
            new THREE.Vector3(side * 2.34, -0.92, -0.7)
        ]);
        const conduit = new THREE.Mesh(
            new THREE.TubeGeometry(curve, 30, 0.055, 9, false),
            materials.blackMetal
        );
        group.add(conduit);
    });

    const servicePanel = new THREE.Mesh(
        createBeveledPanelGeometry(THREE, 0.8, 1.05, 0.26, 0.12),
        materials.blackMetal
    );
    servicePanel.position.set(2.72, -0.62, 0.28);
    servicePanel.rotation.z = -0.04;
    group.add(servicePanel);

    const lightGeometry = new THREE.BoxGeometry(0.08, 0.22, 0.04);
    [materials.blueStatus, materials.blueStatus, materials.amberStatus].forEach((material, index) => {
        const indicator = new THREE.Mesh(lightGeometry, material);
        indicator.position.set(2.72, -0.31 - index * 0.28, 0.43);
        group.add(indicator);
    });
    emissiveMaterials.push(materials.amberStatus);

    const ventGeometry = new THREE.BoxGeometry(0.08, 0.34, 0.48);
    const ventCount = quality.name === "low" ? 5 : 8;
    for (let index = 0; index < ventCount; index += 1) {
        const vent = new THREE.Mesh(ventGeometry, materials.brushedSteel);
        vent.position.set(-2.7, -0.78 + index * 0.2, 0.22);
        group.add(vent);
    }
}

function createRotorBladeGeometry(THREE, depth, sweep) {
    const shape = new THREE.Shape();
    shape.moveTo(0.48, -0.11);
    shape.bezierCurveTo(0.72, -0.2 + sweep, 1.16, -0.2 + sweep, 1.48, 0.02);
    shape.lineTo(1.32, 0.22);
    shape.bezierCurveTo(1.02, 0.08 + sweep, 0.74, 0.08 + sweep, 0.48, 0.11);
    shape.closePath();

    const geometry = new THREE.ExtrudeGeometry(shape, {
        depth,
        steps: 1,
        bevelEnabled: true,
        bevelSegments: 2,
        bevelSize: 0.018,
        bevelThickness: 0.018
    });
    geometry.translate(0, 0, -depth / 2);
    return geometry;
}

function createBeveledPanelGeometry(THREE, width, height, depth, corner) {
    const halfWidth = width / 2;
    const halfHeight = height / 2;
    const shape = new THREE.Shape();
    shape.moveTo(-halfWidth + corner, -halfHeight);
    shape.lineTo(halfWidth - corner, -halfHeight);
    shape.lineTo(halfWidth, -halfHeight + corner);
    shape.lineTo(halfWidth, halfHeight - corner);
    shape.lineTo(halfWidth - corner, halfHeight);
    shape.lineTo(-halfWidth + corner, halfHeight);
    shape.lineTo(-halfWidth, halfHeight - corner);
    shape.lineTo(-halfWidth, -halfHeight + corner);
    shape.closePath();

    const geometry = new THREE.ExtrudeGeometry(shape, {
        depth,
        steps: 1,
        bevelEnabled: true,
        bevelSegments: 2,
        bevelSize: Math.min(0.045, corner * 0.32),
        bevelThickness: Math.min(0.045, depth * 0.18)
    });
    geometry.translate(0, 0, -depth / 2);
    return geometry;
}

function setShadow(mesh, quality) {
    mesh.castShadow = quality.shadows;
    mesh.receiveShadow = quality.shadows;
}

function smoothstep(edge0, edge1, value) {
    const x = Math.min(1, Math.max(0, (value - edge0) / Math.max(0.0001, edge1 - edge0)));
    return x * x * (3 - 2 * x);
}
