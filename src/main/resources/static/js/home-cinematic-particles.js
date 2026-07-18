export function createDataVortex(THREE, quality) {
    const group = new THREE.Group();
    group.position.set(0, -0.25, -4.2);
    const count = quality.particleCount;
    const positions = new Float32Array(count * 3);
    const colors = new Float32Array(count * 3);
    const seeds = new Float32Array(count * 4);
    const color = new THREE.Color();

    for (let index = 0; index < count; index += 1) {
        const positionOffset = index * 3;
        const seedOffset = index * 4;
        const radius = 0.8 + Math.random() * 4.8;
        const phase = Math.random() * Math.PI * 2;
        const speed = 0.12 + Math.random() * 0.42;
        const depth = -12 + Math.random() * 18;
        seeds[seedOffset] = radius;
        seeds[seedOffset + 1] = phase;
        seeds[seedOffset + 2] = speed;
        seeds[seedOffset + 3] = depth;
        positions[positionOffset] = Math.cos(phase) * radius;
        positions[positionOffset + 1] = Math.sin(phase * 1.3) * radius * 0.38;
        positions[positionOffset + 2] = depth;

        color.setHSL(0.58 + Math.random() * 0.05, 0.78, 0.58 + Math.random() * 0.25);
        colors[positionOffset] = color.r;
        colors[positionOffset + 1] = color.g;
        colors[positionOffset + 2] = color.b;
    }

    const geometry = new THREE.BufferGeometry();
    geometry.setAttribute("position", new THREE.BufferAttribute(positions, 3));
    geometry.setAttribute("color", new THREE.BufferAttribute(colors, 3));
    const material = new THREE.PointsMaterial({
        size: quality.name === "low" ? 0.035 : 0.045,
        map: createParticleTexture(THREE),
        transparent: true,
        opacity: 0.58,
        vertexColors: true,
        alphaTest: 0.02,
        depthWrite: false,
        blending: THREE.AdditiveBlending
    });
    group.add(new THREE.Points(geometry, material));

    const dataRings = createDataRings(THREE, quality);
    const beams = createIntakeBeams(THREE);
    group.add(dataRings.group, beams.group);

    return {
        group,
        update(time, progress, state) {
            const intake = smoothstep(0.55, 0.94, progress);
            const reveal = 1 - smoothstep(0.78, 1, progress) * 0.64;
            const positionAttribute = geometry.attributes.position;
            for (let index = 0; index < count; index += 1) {
                const offset = index * 3;
                const seed = index * 4;
                const radius = seeds[seed];
                const phase = seeds[seed + 1];
                const speed = seeds[seed + 2];
                const depth = seeds[seed + 3];
                const angle = phase + time * speed * (1 + intake * 4);
                const intakeRadius = radius * (1 - intake * 0.72);
                positions[offset] = Math.cos(angle) * intakeRadius + state.pointerX * depth * 0.01;
                positions[offset + 1] = Math.sin(angle * 1.3) * intakeRadius * 0.38;
                positions[offset + 2] = depth + intake * ((time * speed * 3 + phase) % 8 - 4);
            }
            positionAttribute.needsUpdate = true;
            material.opacity = (0.2 + progress * 0.14 + intake * 0.12) * reveal;
            dataRings.update(time, progress, intake, reveal);
            beams.update(time, intake, reveal);
        }
    };
}

function createDataRings(THREE, quality) {
    const group = new THREE.Group();
    const rings = [];
    const material = new THREE.MeshBasicMaterial({
        color: 0x3b7dff,
        transparent: true,
        opacity: 0.18,
        blending: THREE.AdditiveBlending,
        depthWrite: false
    });
    const count = quality.name === "low" ? 8 : 16;
    for (let index = 0; index < count; index += 1) {
        const radius = 1.8 + index * 0.22;
        const ring = new THREE.Mesh(new THREE.TorusGeometry(radius, 0.012, 6, 72), material.clone());
        ring.position.z = -8 + index * 0.78;
        ring.rotation.x = Math.PI / 2;
        ring.userData.phase = index * 0.42;
        rings.push(ring);
        group.add(ring);
    }
    return {
        group,
        update(time, progress, intake, reveal) {
            rings.forEach((ring, index) => {
                ring.rotation.z = time * (0.08 + index * 0.004) * (index % 2 ? -1 : 1);
                ring.scale.setScalar(1 - intake * 0.42 + Math.sin(time + ring.userData.phase) * 0.02);
                ring.material.opacity = (0.035 + progress * 0.045 + intake * 0.11) * reveal;
                ring.position.z += intake * 0.004 * (index + 1);
                if (ring.position.z > 3) {
                    ring.position.z = -9;
                }
            });
        }
    };
}

function createIntakeBeams(THREE) {
    const group = new THREE.Group();
    const beams = [];
    const material = new THREE.MeshBasicMaterial({
        color: 0x6fa8ff,
        transparent: true,
        opacity: 0,
        blending: THREE.AdditiveBlending,
        depthWrite: false
    });
    for (let index = 0; index < 18; index += 1) {
        const angle = index / 18 * Math.PI * 2;
        const beam = new THREE.Mesh(new THREE.BoxGeometry(0.012, 0.012, 9), material.clone());
        beam.position.set(Math.cos(angle) * 2.8, Math.sin(angle) * 1.35, -4);
        beam.rotation.z = angle;
        beam.userData.phase = index * 0.34;
        beams.push(beam);
        group.add(beam);
    }
    return {
        group,
        update(time, intake, reveal) {
            beams.forEach((beam) => {
                beam.material.opacity = intake
                    * (0.038 + Math.sin(time * 2 + beam.userData.phase) * 0.014)
                    * reveal;
                beam.scale.z = 0.5 + intake * 0.8;
            });
        }
    };
}

function smoothstep(edge0, edge1, value) {
    const x = Math.min(1, Math.max(0, (value - edge0) / Math.max(0.0001, edge1 - edge0)));
    return x * x * (3 - 2 * x);
}

function createParticleTexture(THREE) {
    const canvas = document.createElement("canvas");
    canvas.width = 64;
    canvas.height = 64;
    const context = canvas.getContext("2d");
    const gradient = context.createRadialGradient(32, 32, 0, 32, 32, 30);
    gradient.addColorStop(0, "rgba(255,255,255,1)");
    gradient.addColorStop(0.25, "rgba(190,220,255,0.92)");
    gradient.addColorStop(0.65, "rgba(90,150,255,0.28)");
    gradient.addColorStop(1, "rgba(40,100,255,0)");
    context.fillStyle = gradient;
    context.fillRect(0, 0, 64, 64);
    const texture = new THREE.CanvasTexture(canvas);
    texture.colorSpace = THREE.SRGBColorSpace;
    return texture;
}
