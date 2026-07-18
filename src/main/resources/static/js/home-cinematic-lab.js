export function createLaboratory(THREE, quality) {
    const group = new THREE.Group();
    group.position.set(0, 0, -3);
    const foreground = new THREE.Group();
    const practicalLights = [];
    const lightStrips = [];

    const gunmetal = new THREE.MeshStandardMaterial({
        color: 0x111722,
        metalness: 0.82,
        roughness: 0.3
    });
    const blackMetal = new THREE.MeshStandardMaterial({
        color: 0x05070b,
        metalness: 0.76,
        roughness: 0.42
    });
    const floorMaterial = new THREE.MeshStandardMaterial({
        color: 0x070a10,
        metalness: 0.62,
        roughness: 0.48
    });
    const glass = new THREE.MeshPhysicalMaterial({
        color: 0x183157,
        metalness: 0.06,
        roughness: 0.18,
        transmission: quality.name === "low" ? 0 : 0.28,
        thickness: 0.7,
        transparent: true,
        opacity: 0.32,
        depthWrite: false
    });
    const blueLightMaterial = new THREE.MeshBasicMaterial({
        color: 0x3d7fff,
        transparent: true,
        opacity: 0.68
    });
    const warmLightMaterial = new THREE.MeshBasicMaterial({
        color: 0xffa460,
        transparent: true,
        opacity: 0.38
    });

    const floor = new THREE.Mesh(new THREE.PlaneGeometry(36, 44), floorMaterial);
    floor.rotation.x = -Math.PI / 2;
    floor.position.set(0, -2.35, -7);
    floor.receiveShadow = quality.shadows;
    group.add(floor);

    addFloorPanels(THREE, group, gunmetal, blueLightMaterial);
    addWallRibs(THREE, group, gunmetal, quality);
    addCeiling(THREE, group, gunmetal, blackMetal);
    addGlassPartitions(THREE, group, glass);
    addForegroundStructure(THREE, foreground, gunmetal, blackMetal);
    addFarWall(THREE, group, blackMetal, blueLightMaterial);

    const ambient = new THREE.HemisphereLight(0x527bb8, 0x010205, 0.78);
    const engineKey = new THREE.SpotLight(0x78a8ff, 11, 32, Math.PI * 0.22, 0.62, 1.4);
    engineKey.position.set(-5.5, 7.2, 7.5);
    engineKey.target.position.set(0, -0.4, -5);
    engineKey.castShadow = quality.shadows;
    if (engineKey.castShadow) {
        engineKey.shadow.mapSize.set(quality.name === "high" ? 1536 : 1024, quality.name === "high" ? 1536 : 1024);
    }
    group.add(ambient, engineKey, engineKey.target);

    [
        [-5.8, 2.6, 1.5, 0x4f86ff, 13],
        [5.8, 1.8, -5, 0x4f86ff, 12],
        [-4.4, 3.4, -11, 0xffa265, 9],
        [4.8, 4.0, -15, 0x5a8cff, 10]
    ].forEach((config) => {
        const light = new THREE.PointLight(config[3], 0.7, config[4], 2);
        light.position.set(config[0], config[1], config[2]);
        practicalLights.push(light);
        group.add(light);
    });

    for (let index = 0; index < 8; index += 1) {
        const strip = new THREE.Mesh(
            new THREE.BoxGeometry(0.07, 0.07, 4.6),
            index === 6 ? warmLightMaterial : blueLightMaterial
        );
        strip.position.set(index % 2 ? 7.15 : -7.15, 2.5 - (index % 3) * 1.6, 5 - Math.floor(index / 2) * 6.2);
        lightStrips.push(strip);
        group.add(strip);
    }

    group.add(foreground);

    return {
        group,
        update(time, progress, state) {
            foreground.position.x = state.pointerX * -0.14;
            foreground.position.z = progress * 1.1;
            foreground.rotation.y = state.pointerX * -0.008;
            practicalLights.forEach((light, index) => {
                const activation = smoothstep(0.06 + index * 0.035, 0.48, progress);
                light.intensity = 0.7 + activation * (2.4 + index * 0.28);
            });
            lightStrips.forEach((strip, index) => {
                strip.material.opacity = 0.34 + smoothstep(0.04 + index * 0.018, 0.42, progress) * 0.5;
            });
            engineKey.intensity = 10 + smoothstep(0.1, 0.58, progress) * 10 + Math.sin(time * 0.42) * 0.35;
        }
    };
}

function addFloorPanels(THREE, group, metal, light) {
    const panelGeometry = new THREE.BoxGeometry(3.5, 0.07, 3.5);
    for (let x = -2; x <= 2; x += 1) {
        for (let z = 0; z < 10; z += 1) {
            const panel = new THREE.Mesh(panelGeometry, metal);
            panel.position.set(x * 3.62, -2.31, 8 - z * 3.64);
            group.add(panel);
        }
    }
    [-2.05, 2.05].forEach((x) => {
        const runway = new THREE.Mesh(new THREE.BoxGeometry(0.055, 0.025, 35), light);
        runway.position.set(x, -2.25, -6.4);
        group.add(runway);
    });
}

function addWallRibs(THREE, group, material, quality) {
    const verticalGeometry = new THREE.BoxGeometry(0.34, 7.8, 0.5);
    const braceGeometry = new THREE.BoxGeometry(2.8, 0.28, 0.42);
    for (let index = 0; index < 11; index += 1) {
        const z = 7 - index * 3.4;
        [-1, 1].forEach((side) => {
            const vertical = new THREE.Mesh(verticalGeometry, material);
            vertical.position.set(side * 7.65, 1.05, z);
            vertical.rotation.z = side * -0.075;
            vertical.castShadow = quality.shadows;
            group.add(vertical);

            const brace = new THREE.Mesh(braceGeometry, material);
            brace.position.set(side * 6.5, 4.15, z);
            brace.rotation.z = side * 0.38;
            group.add(brace);
        });
    }
}

function addCeiling(THREE, group, metal, blackMetal) {
    const conduitGeometry = new THREE.CylinderGeometry(0.1, 0.1, 38, 14);
    [-4.8, -2.8, 2.8, 4.8].forEach((x, index) => {
        const conduit = new THREE.Mesh(conduitGeometry, index % 2 ? metal : blackMetal);
        conduit.rotation.x = Math.PI / 2;
        conduit.position.set(x, 4.55, -7);
        group.add(conduit);
    });
    for (let index = 0; index < 9; index += 1) {
        const beam = new THREE.Mesh(new THREE.BoxGeometry(15.6, 0.25, 0.38), metal);
        beam.position.set(0, 4.42, 5 - index * 4.2);
        group.add(beam);
    }
}

function addGlassPartitions(THREE, group, glass) {
    [-1, 1].forEach((side) => {
        for (let index = 0; index < 3; index += 1) {
            const partition = new THREE.Mesh(new THREE.BoxGeometry(3.3, 4.7, 0.08), glass);
            partition.position.set(side * 5.55, 0.25, 1.8 - index * 6.6);
            partition.rotation.y = side * -0.14;
            group.add(partition);
        }
    });
}

function addForegroundStructure(THREE, foreground, metal, blackMetal) {
    const railGeometry = new THREE.BoxGeometry(0.2, 0.24, 24);
    [-3.15, 3.15].forEach((x) => {
        const rail = new THREE.Mesh(railGeometry, metal);
        rail.position.set(x, -1.5, 4.2);
        foreground.add(rail);
    });

    [-8.6, 8.6].forEach((x) => {
        const pillar = new THREE.Mesh(new THREE.BoxGeometry(1.05, 10, 1.05), blackMetal);
        pillar.position.set(x, 0.5, 7.6);
        foreground.add(pillar);
    });

    const cableMaterial = new THREE.MeshStandardMaterial({
        color: 0x07090d,
        metalness: 0.48,
        roughness: 0.6
    });
    for (let index = 0; index < 5; index += 1) {
        const points = [
            new THREE.Vector3(-8 + index * 0.18, 4.8, 8),
            new THREE.Vector3(-6.8, 3.6 - index * 0.15, 1),
            new THREE.Vector3(-5.4, 3.9 - index * 0.1, -7)
        ];
        const curve = new THREE.CatmullRomCurve3(points);
        const cable = new THREE.Mesh(new THREE.TubeGeometry(curve, 36, 0.035, 8, false), cableMaterial);
        foreground.add(cable);
    }
}

function addFarWall(THREE, group, blackMetal, lightMaterial) {
    const wall = new THREE.Mesh(new THREE.BoxGeometry(17, 9, 0.5), blackMetal);
    wall.position.set(0, 0.6, -25);
    group.add(wall);

    const portal = new THREE.Mesh(new THREE.TorusGeometry(4.2, 0.18, 18, 96), lightMaterial);
    portal.position.set(0, 0.3, -24.65);
    group.add(portal);
}

function smoothstep(edge0, edge1, value) {
    const x = Math.min(1, Math.max(0, (value - edge0) / Math.max(0.0001, edge1 - edge0)));
    return x * x * (3 - 2 * x);
}
