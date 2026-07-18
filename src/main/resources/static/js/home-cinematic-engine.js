export function createQuantEngine(THREE, quality) {
    const group = new THREE.Group();
    group.position.set(0, -0.8, -4.4);
    const rings = [];
    const activationLights = [];
    const energyMeshes = [];

    const gunmetal = new THREE.MeshStandardMaterial({
        color: 0x151c28,
        metalness: 0.9,
        roughness: 0.24
    });
    const blackMetal = new THREE.MeshStandardMaterial({
        color: 0x05080d,
        metalness: 0.86,
        roughness: 0.32
    });
    const steel = new THREE.MeshStandardMaterial({
        color: 0x8793a4,
        metalness: 0.96,
        roughness: 0.16
    });
    const glass = new THREE.MeshPhysicalMaterial({
        color: 0x214f91,
        metalness: 0.02,
        roughness: 0.08,
        transmission: quality.name === "low" ? 0.04 : 0.62,
        thickness: 1.2,
        transparent: true,
        opacity: quality.name === "low" ? 0.34 : 0.5,
        depthWrite: false
    });
    const energy = new THREE.MeshBasicMaterial({
        color: 0x3478ff,
        transparent: true,
        opacity: 0.72,
        blending: THREE.AdditiveBlending,
        depthWrite: false
    });
    const energySoft = new THREE.MeshBasicMaterial({
        color: 0x87bcff,
        transparent: true,
        opacity: 0.2,
        blending: THREE.AdditiveBlending,
        side: THREE.DoubleSide,
        depthWrite: false
    });

    addPedestal(THREE, group, quality, gunmetal, blackMetal, steel, energy);
    addChamber(THREE, group, quality, gunmetal, steel, glass, energy, energySoft, energyMeshes);
    addStruts(THREE, group, quality, gunmetal, steel);
    addMechanicalRings(THREE, group, quality, steel, glass, rings);
    addServiceConduits(THREE, group, gunmetal, energy);

    [0x3478ff, 0x82bdff, 0x245dff].forEach((color, index) => {
        const light = new THREE.PointLight(color, 0, 10 + index * 3, 2);
        light.position.set(index === 0 ? -1.2 : index === 1 ? 1.8 : 0, index === 1 ? 1.1 : -0.4, 1.4 - index * 0.8);
        activationLights.push(light);
        group.add(light);
    });

    return {
        group,
        update(time, progress, state) {
            const activation = smoothstep(0.28, 0.68, progress);
            const intake = smoothstep(0.58, 0.94, progress);
            rings.forEach((ring, index) => {
                ring.rotation.y = ring.userData.baseY
                    + time * (0.12 + index * 0.08) * activation * (index % 2 ? -1 : 1);
                ring.rotation.z = ring.userData.baseZ
                    + Math.sin(time * 0.4 + index) * 0.04 * activation;
            });
            activationLights.forEach((light, index) => {
                light.intensity = activation * (5 + index * 1.5) + intake * 3;
            });
            energyMeshes.forEach((mesh, index) => {
                const pulse = 0.92 + activation * 0.16 + Math.sin(time * (2.1 + index * 0.28)) * 0.025 * activation;
                mesh.scale.setScalar(pulse + intake * 0.08);
                mesh.material.opacity = mesh.userData.baseOpacity + activation * mesh.userData.activationOpacity;
            });
            group.rotation.y = state.pointerX * 0.025;
            group.rotation.x = state.pointerY * -0.012;
        }
    };
}

function addPedestal(THREE, group, quality, gunmetal, blackMetal, steel, energy) {
    const layers = [
        [4.2, 4.55, 0.32, -1.92, blackMetal],
        [3.75, 4.1, 0.34, -1.62, gunmetal],
        [3.25, 3.58, 0.3, -1.32, steel],
        [2.7, 3.08, 0.26, -1.04, gunmetal]
    ];
    layers.forEach((layer) => {
        const mesh = new THREE.Mesh(
            new THREE.CylinderGeometry(layer[0], layer[1], layer[2], quality.segments),
            layer[4]
        );
        mesh.position.y = layer[3];
        mesh.castShadow = quality.shadows;
        mesh.receiveShadow = quality.shadows;
        group.add(mesh);
    });

    [3.72, 3.18, 2.62].forEach((radius, index) => {
        const trim = new THREE.Mesh(
            new THREE.TorusGeometry(radius, 0.06 + index * 0.012, 14, quality.segments * 2),
            index === 1 ? energy : steel
        );
        trim.rotation.x = Math.PI / 2;
        trim.position.y = -1.44 + index * 0.24;
        group.add(trim);
    });

    const ridgeGeometry = new THREE.BoxGeometry(0.05, 0.18, 0.2);
    for (let index = 0; index < 72; index += 1) {
        const angle = index / 72 * Math.PI * 2;
        const ridge = new THREE.Mesh(ridgeGeometry, steel);
        ridge.position.set(Math.cos(angle) * 3.93, -1.58, Math.sin(angle) * 3.93);
        ridge.rotation.y = -angle;
        group.add(ridge);
    }
}

function addChamber(THREE, group, quality, gunmetal, steel, glass, energy, energySoft, energyMeshes) {
    const chamber = new THREE.Mesh(
        new THREE.SphereGeometry(1.56, quality.segments, Math.max(24, quality.segments / 2)),
        glass
    );
    chamber.castShadow = quality.shadows;
    group.add(chamber);

    const chamberFrame = new THREE.Mesh(
        new THREE.TorusGeometry(1.58, 0.075, 16, quality.segments * 2),
        steel
    );
    chamberFrame.rotation.x = Math.PI / 2;
    group.add(chamberFrame);

    const core = new THREE.Mesh(new THREE.IcosahedronGeometry(0.82, quality.name === "low" ? 2 : 4), energy);
    core.userData.baseOpacity = 0.36;
    core.userData.activationOpacity = 0.48;
    energyMeshes.push(core);
    group.add(core);

    const coreShell = new THREE.Mesh(new THREE.SphereGeometry(1.04, quality.segments, quality.segments / 2), energySoft);
    coreShell.userData.baseOpacity = 0.08;
    coreShell.userData.activationOpacity = 0.2;
    energyMeshes.push(coreShell);
    group.add(coreShell);

    const equator = new THREE.Mesh(new THREE.TorusGeometry(1.22, 0.035, 12, quality.segments * 2), energy);
    equator.userData.baseOpacity = 0.26;
    equator.userData.activationOpacity = 0.44;
    energyMeshes.push(equator);
    group.add(equator);

    [-1.78, 1.78].forEach((y) => {
        const collar = new THREE.Mesh(new THREE.CylinderGeometry(0.62, 0.82, 0.34, quality.segments), gunmetal);
        collar.position.y = y;
        group.add(collar);
    });
}

function addStruts(THREE, group, quality, gunmetal, steel) {
    const strutGeometry = new THREE.BoxGeometry(0.13, 3.25, 0.2);
    for (let index = 0; index < 6; index += 1) {
        const angle = index / 6 * Math.PI * 2;
        const holder = new THREE.Group();
        holder.position.set(Math.cos(angle) * 1.92, -0.05, Math.sin(angle) * 1.92);
        holder.rotation.y = -angle;

        const strut = new THREE.Mesh(strutGeometry, gunmetal);
        strut.rotation.z = 0.24;
        strut.castShadow = quality.shadows;
        holder.add(strut);

        const clampTop = new THREE.Mesh(new THREE.BoxGeometry(0.34, 0.2, 0.36), steel);
        clampTop.position.y = 1.5;
        const clampBottom = clampTop.clone();
        clampBottom.position.y = -1.5;
        holder.add(clampTop, clampBottom);
        group.add(holder);
    }
}

function addMechanicalRings(THREE, group, quality, steel, glass, rings) {
    [
        { radius: 2.25, tube: 0.16, x: 1.18, y: 0.12, z: 0.18, material: steel },
        { radius: 2.62, tube: 0.11, x: 0.32, y: 0.84, z: -0.22, material: glass },
        { radius: 3.02, tube: 0.07, x: 1.46, y: -0.18, z: 0.56, material: steel }
    ].forEach((specification) => {
        const ring = new THREE.Mesh(
            new THREE.TorusGeometry(specification.radius, specification.tube, 20, quality.segments * 2),
            specification.material
        );
        ring.rotation.set(specification.x, specification.y, specification.z);
        ring.userData.baseY = specification.y;
        ring.userData.baseZ = specification.z;
        ring.castShadow = quality.shadows;
        rings.push(ring);
        group.add(ring);
    });
}

function addServiceConduits(THREE, group, metal, energy) {
    for (let index = 0; index < 4; index += 1) {
        const side = index % 2 ? 1 : -1;
        const depth = index < 2 ? 0.8 : -0.8;
        const curve = new THREE.CatmullRomCurve3([
            new THREE.Vector3(side * 3.8, -1.45, depth),
            new THREE.Vector3(side * 3.1, -0.8, depth * 1.2),
            new THREE.Vector3(side * 1.9, -0.42, depth * 0.7)
        ]);
        const tube = new THREE.Mesh(new THREE.TubeGeometry(curve, 36, 0.06, 10, false), index === 0 ? energy : metal);
        group.add(tube);
    }
}

function smoothstep(edge0, edge1, value) {
    const x = Math.min(1, Math.max(0, (value - edge0) / Math.max(0.0001, edge1 - edge0)));
    return x * x * (3 - 2 * x);
}
