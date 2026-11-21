const NOISE_GLSL = `
    float hash31(vec3 p) {
        p = fract(p * 0.1031);
        p += dot(p, p.yzx + 33.33);
        return fract((p.x + p.y) * p.z);
    }

    float noise3(vec3 p) {
        vec3 i = floor(p);
        vec3 f = fract(p);
        f = f * f * (3.0 - 2.0 * f);
        return mix(
            mix(mix(hash31(i), hash31(i + vec3(1.0, 0.0, 0.0)), f.x),
                mix(hash31(i + vec3(0.0, 1.0, 0.0)), hash31(i + vec3(1.0, 1.0, 0.0)), f.x), f.y),
            mix(mix(hash31(i + vec3(0.0, 0.0, 1.0)), hash31(i + vec3(1.0, 0.0, 1.0)), f.x),
                mix(hash31(i + vec3(0.0, 1.0, 1.0)), hash31(i + vec3(1.0, 1.0, 1.0)), f.x), f.y),
            f.z
        );
    }

    float fbm(vec3 p) {
        float value = 0.0;
        float amplitude = 0.52;
        for (int i = 0; i < 5; i++) {
            value += amplitude * noise3(p);
            p = p * 2.03 + vec3(7.1, 3.7, 5.4);
            amplitude *= 0.48;
        }
        return value;
    }
`;

const CORE_VERTEX_SHADER = `
    uniform float uTime;
    uniform vec2 uPointer;
    varying vec3 vNormalView;
    varying vec3 vViewPosition;
    varying vec3 vObjectPosition;
    varying float vNoise;
    ${NOISE_GLSL}

    void main() {
        vec3 flow = normal * 3.15 + vec3(uTime * 0.16, -uTime * 0.11, uTime * 0.08);
        flow.xy += uPointer * 0.55;
        float field = fbm(flow);
        float wave = sin((normal.y + field) * 9.0 + uTime * 0.72) * 0.5 + 0.5;
        float displacement = (field - 0.5) * 0.19 + wave * 0.022;
        vec3 transformed = position + normal * displacement;
        vec4 viewPosition = modelViewMatrix * vec4(transformed, 1.0);
        vNormalView = normalize(normalMatrix * normal);
        vViewPosition = viewPosition.xyz;
        vObjectPosition = transformed;
        vNoise = field;
        gl_Position = projectionMatrix * viewPosition;
    }
`;

const CORE_FRAGMENT_SHADER = `
    precision highp float;
    uniform float uTime;
    uniform vec2 uPointer;
    varying vec3 vNormalView;
    varying vec3 vViewPosition;
    varying vec3 vObjectPosition;
    varying float vNoise;

    void main() {
        vec3 viewDirection = normalize(-vViewPosition);
        float fresnel = pow(1.0 - abs(dot(vNormalView, viewDirection)), 2.65);
        float strata = sin(vObjectPosition.y * 16.0 + vNoise * 13.0 - uTime * 0.55) * 0.5 + 0.5;
        float ribbon = smoothstep(0.58, 0.86, strata);
        float caustic = pow(max(0.0, sin(vNoise * 31.0 + uTime * 1.1)), 8.0);
        vec3 abyss = vec3(0.007, 0.025, 0.064);
        vec3 blue = vec3(0.10, 0.52, 0.90);
        vec3 cyan = vec3(0.27, 0.88, 1.0);
        vec3 violet = vec3(0.43, 0.28, 0.98);
        vec3 amber = vec3(1.0, 0.48, 0.16);
        vec3 color = mix(abyss, mix(violet, blue, vNoise), 0.31 + fresnel * 0.42);
        color += cyan * fresnel * 0.92;
        color += violet * ribbon * 0.19;
        color += cyan * caustic * 0.22;
        color += amber * caustic * smoothstep(0.45, 0.9, vNoise) * 0.16;
        color += vec3(uPointer.x * 0.012, 0.0, -uPointer.y * 0.01);
        float alpha = clamp(0.1 + fresnel * 0.67 + ribbon * 0.09 + caustic * 0.08, 0.0, 0.82);
        gl_FragColor = vec4(color, alpha);
    }
`;

const INNER_FRAGMENT_SHADER = `
    precision highp float;
    uniform float uTime;
    uniform vec2 uPointer;
    varying vec3 vNormalView;
    varying vec3 vViewPosition;
    varying vec3 vObjectPosition;
    varying float vNoise;

    void main() {
        vec3 viewDirection = normalize(-vViewPosition);
        float fresnel = pow(1.0 - abs(dot(vNormalView, viewDirection)), 3.1);
        float veinA = pow(max(0.0, sin(vNoise * 28.0 - uTime * 1.55)), 7.0);
        float veinB = pow(max(0.0, cos(vNoise * 19.0 + vObjectPosition.x * 2.4 + uTime)), 8.0);
        float chamber = smoothstep(0.74, 0.98, sin((vObjectPosition.x + vObjectPosition.y) * 9.0 - uTime) * 0.5 + 0.5);
        vec3 cyan = vec3(0.16, 0.74, 1.0);
        vec3 violet = vec3(0.47, 0.18, 0.94);
        vec3 amber = vec3(1.0, 0.38, 0.08);
        vec3 color = mix(violet, cyan, smoothstep(0.22, 0.85, vNoise));
        color *= 0.2 + veinA * 1.1 + veinB * 0.56 + fresnel * 0.3;
        color += amber * chamber * 0.18;
        color += vec3(0.05, 0.02, 0.0) * (uPointer.x + 1.0) * veinB;
        float alpha = clamp(0.06 + veinA * 0.3 + veinB * 0.21 + chamber * 0.1 + fresnel * 0.13, 0.0, 0.46);
        gl_FragColor = vec4(color, alpha);
    }
`;

const HALO_VERTEX_SHADER = `
    varying vec3 vNormalView;
    varying vec3 vViewPosition;
    void main() {
        vec4 viewPosition = modelViewMatrix * vec4(position, 1.0);
        vNormalView = normalize(normalMatrix * normal);
        vViewPosition = viewPosition.xyz;
        gl_Position = projectionMatrix * viewPosition;
    }
`;

const HALO_FRAGMENT_SHADER = `
    precision highp float;
    uniform float uTime;
    varying vec3 vNormalView;
    varying vec3 vViewPosition;
    void main() {
        vec3 viewDirection = normalize(-vViewPosition);
        float rim = pow(1.0 - abs(dot(vNormalView, viewDirection)), 4.6);
        float breath = 0.78 + sin(uTime * 0.62) * 0.13;
        vec3 color = mix(vec3(0.31, 0.19, 0.92), vec3(0.20, 0.79, 1.0), rim);
        gl_FragColor = vec4(color * rim * breath, rim * 0.17);
    }
`;

const BACKDROP_VERTEX_SHADER = `
    varying vec3 vDirection;
    void main() {
        vDirection = normalize(position);
        gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0);
    }
`;

const BACKDROP_FRAGMENT_SHADER = `
    precision highp float;
    uniform float uTime;
    uniform vec2 uPointer;
    varying vec3 vDirection;
    ${NOISE_GLSL}

    void main() {
        vec3 direction = normalize(vDirection);
        vec3 drift = direction * 2.7 + vec3(uTime * 0.006, -uTime * 0.004, uTime * 0.002);
        float cloudWide = fbm(drift);
        float cloudFine = fbm(direction * 6.8 + vec3(-uTime * 0.008, uTime * 0.004, 0.0));
        float cloud = smoothstep(0.48, 0.78, cloudWide * 0.74 + cloudFine * 0.36);
        float rift = smoothstep(0.58, 0.83, fbm(direction * 3.6 + vec3(8.0, 2.0, uTime * 0.003)));
        vec3 night = vec3(0.004, 0.011, 0.031);
        vec3 cobalt = vec3(0.018, 0.065, 0.14);
        vec3 violet = vec3(0.075, 0.035, 0.16);
        vec3 color = night + cobalt * cloud * 0.48 + violet * rift * 0.35;

        vec3 cell = floor(direction * 420.0);
        float starHash = hash31(cell);
        float star = step(0.9947, starHash) * pow(hash31(cell.yzx + 19.0), 7.0);
        float brightStar = step(0.9994, starHash) * 2.4;
        vec3 starColor = mix(vec3(0.38, 0.66, 1.0), vec3(1.0, 0.68, 0.38), hash31(cell.zxy));
        color += starColor * (star * 0.62 + brightStar);

        float horizon = pow(1.0 - abs(direction.y), 8.0);
        color += vec3(0.06, 0.22, 0.32) * horizon * cloud * 0.11;
        color += vec3(uPointer.x * 0.004, 0.0, uPointer.y * 0.003);
        gl_FragColor = vec4(color, 1.0);
    }
`;

const PARTICLE_VERTEX_SHADER = `
    attribute float aScale;
    attribute vec3 aColor;
    varying vec3 vColor;
    void main() {
        vec4 viewPosition = modelViewMatrix * vec4(position, 1.0);
        vColor = aColor;
        gl_PointSize = aScale * (175.0 / max(1.0, -viewPosition.z));
        gl_Position = projectionMatrix * viewPosition;
    }
`;

const PARTICLE_FRAGMENT_SHADER = `
    precision highp float;
    varying vec3 vColor;
    void main() {
        float distanceToCenter = length(gl_PointCoord - vec2(0.5));
        if (distanceToCenter > 0.5) discard;
        float alpha = smoothstep(0.5, 0.03, distanceToCenter);
        float core = smoothstep(0.15, 0.0, distanceToCenter);
        gl_FragColor = vec4(vColor + core * 0.72, alpha * 0.78);
    }
`;

const SCAN_VERTEX_SHADER = `
    varying vec2 vUv;
    void main() {
        vUv = uv;
        gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0);
    }
`;

const SCAN_FRAGMENT_SHADER = `
    precision highp float;
    uniform float uTime;
    uniform vec2 uPointer;
    varying vec2 vUv;
    void main() {
        vec2 p = vUv - 0.5;
        float radius = length(p);
        if (radius > 0.495) discard;
        float radialEdge = 1.0 - smoothstep(0.42, 0.495, radius);
        float sweepPosition = fract(uTime * 0.052 + 0.18 + uPointer.y * 0.035);
        float sweep = exp(-pow((vUv.y - sweepPosition) * 28.0, 2.0));
        float echo = exp(-pow((vUv.y - fract(sweepPosition - 0.075)) * 52.0, 2.0));
        float rings = pow(max(0.0, sin(radius * 92.0 - uTime * 0.68)), 18.0);
        float spokes = pow(max(0.0, sin(atan(p.y, p.x) * 12.0 + uTime * 0.14)), 34.0);
        vec3 cyan = vec3(0.20, 0.82, 1.0);
        vec3 violet = vec3(0.49, 0.28, 1.0);
        vec3 amber = vec3(1.0, 0.34, 0.08);
        vec3 color = mix(violet, cyan, smoothstep(0.0, 0.48, radius));
        color += amber * echo * 0.32;
        float alpha = radialEdge * (sweep * 0.16 + echo * 0.07 + rings * 0.022 + spokes * 0.018);
        gl_FragColor = vec4(color, alpha);
    }
`;

export function createProceduralCoreWorld(THREE, {
    particleCount = 920,
    seed = 917
} = {}) {
    const random = createSeededRandom(seed);
    const root = new THREE.Group();
    root.userData.kind = "procedural-core-world";
    const coreAssembly = new THREE.Group();
    coreAssembly.userData.kind = "core-assembly";

    const sharedUniforms = () => ({
        uTime: { value: 0 },
        uPointer: { value: new THREE.Vector2() }
    });

    const backdrop = createProceduralBackdrop(THREE);
    const coreMaterial = new THREE.ShaderMaterial({
        uniforms: sharedUniforms(),
        vertexShader: CORE_VERTEX_SHADER,
        fragmentShader: CORE_FRAGMENT_SHADER,
        transparent: true,
        depthWrite: false,
        side: THREE.DoubleSide
    });
    const core = new THREE.Mesh(new THREE.SphereGeometry(2.02, 96, 72), coreMaterial);
    core.renderOrder = 5;

    const innerMaterial = new THREE.ShaderMaterial({
        uniforms: sharedUniforms(),
        vertexShader: CORE_VERTEX_SHADER,
        fragmentShader: INNER_FRAGMENT_SHADER,
        transparent: true,
        depthWrite: false,
        blending: THREE.AdditiveBlending,
        side: THREE.DoubleSide
    });
    const inner = new THREE.Mesh(new THREE.IcosahedronGeometry(1.67, 7), innerMaterial);
    inner.rotation.set(0.34, -0.28, 0.16);
    inner.renderOrder = 3;

    const haloMaterial = new THREE.ShaderMaterial({
        uniforms: { uTime: { value: 0 } },
        vertexShader: HALO_VERTEX_SHADER,
        fragmentShader: HALO_FRAGMENT_SHADER,
        transparent: true,
        depthWrite: false,
        blending: THREE.AdditiveBlending,
        side: THREE.BackSide
    });
    const halo = new THREE.Mesh(new THREE.SphereGeometry(2.34, 64, 48), haloMaterial);
    halo.renderOrder = 1;

    const chromaticShell = createChromaticShell(THREE);
    const filaments = createInternalFilaments(THREE, random);
    const chambers = createInnerChambers(THREE);
    const scanVolume = createScanVolume(THREE);
    const surfaceNetwork = createSurfaceNetwork(THREE);
    const rings = createOrbitSystem(THREE, random);
    const signalArcs = createSignalArcs(THREE, random);
    const telemetryTicks = createTelemetryTicks(THREE);
    const particles = createParticleField(THREE, random, particleCount);
    const constellationNetwork = createConstellationNetwork(THREE, random);
    const energyNodes = createEnergyNodes(THREE, random);
    const microNodes = createMicroNodes(THREE, random);

    coreAssembly.add(
        halo,
        inner,
        chambers,
        filaments,
        scanVolume,
        surfaceNetwork,
        core,
        chromaticShell,
        rings,
        signalArcs,
        telemetryTicks,
        energyNodes,
        microNodes
    );
    root.add(backdrop, constellationNetwork, particles, coreAssembly);

    const materialsWithTime = [
        coreMaterial,
        innerMaterial,
        haloMaterial,
        backdrop.material,
        chromaticShell.material,
        scanVolume.material
    ];
    let disposed = false;

    function update({ elapsed = 0, pointerX = 0, pointerY = 0 } = {}) {
        materialsWithTime.forEach((material) => {
            material.uniforms.uTime.value = elapsed;
            if (material.uniforms.uPointer) material.uniforms.uPointer.value.set(pointerX, pointerY);
        });

        coreAssembly.rotation.y = elapsed * 0.052 + pointerX * 0.14;
        coreAssembly.rotation.x = pointerY * 0.095 + Math.sin(elapsed * 0.21) * 0.025;
        backdrop.rotation.y = -elapsed * 0.0035;
        backdrop.rotation.x = Math.sin(elapsed * 0.025) * 0.012;
        inner.rotation.y = -elapsed * 0.11 + pointerX * 0.18;
        inner.rotation.z = elapsed * 0.035;
        filaments.rotation.y = -elapsed * 0.075 + pointerX * 0.08;
        filaments.rotation.z = elapsed * 0.018;
        chambers.rotation.y = elapsed * 0.045 - pointerX * 0.06;
        chambers.rotation.x = -elapsed * 0.021 + pointerY * 0.05;
        scanVolume.rotation.z = elapsed * 0.027 + pointerX * 0.035;
        surfaceNetwork.rotation.y = elapsed * 0.031;
        surfaceNetwork.rotation.x = -elapsed * 0.016;
        signalArcs.rotation.y = elapsed * 0.021;
        signalArcs.rotation.z = -elapsed * 0.014;
        telemetryTicks.rotation.y = elapsed * -0.018;
        telemetryTicks.rotation.z = elapsed * 0.012;
        constellationNetwork.rotation.y = elapsed * -0.0028;
        constellationNetwork.rotation.x = Math.sin(elapsed * 0.035) * 0.008;

        const corePulse = 1 + Math.sin(elapsed * 0.72) * 0.009;
        core.scale.setScalar(corePulse);
        chromaticShell.scale.setScalar(1 + Math.sin(elapsed * 0.43) * 0.006);
        halo.scale.setScalar(1 + Math.sin(elapsed * 0.55) * 0.014);

        rings.children.forEach((ring, index) => {
            ring.rotation.z = ring.userData.baseZ + elapsed * ring.userData.spinSpeed;
            ring.rotation.y = ring.userData.baseY + Math.sin(elapsed * 0.15 + index) * 0.052;
        });
        signalArcs.children.forEach((arc, index) => {
            arc.rotation.z = arc.userData.baseZ + elapsed * arc.userData.spinSpeed;
            arc.material.opacity = arc.userData.baseOpacity
                * (0.72 + Math.sin(elapsed * arc.userData.pulseSpeed + index) * 0.28);
        });
        filaments.children.forEach((filament, index) => {
            filament.material.opacity = filament.userData.baseOpacity
                * (0.72 + Math.sin(elapsed * filament.userData.pulseSpeed + index * 0.7) * 0.28);
        });
        microNodes.children.forEach((node, index) => {
            const pulse = 0.82 + Math.sin(elapsed * node.userData.pulseSpeed + index) * 0.18;
            node.scale.setScalar(pulse * node.userData.baseScale);
        });
        particles.rotation.y = elapsed * -0.011;
        particles.rotation.z = elapsed * 0.004;
        energyNodes.rotation.y = elapsed * 0.08;
        energyNodes.rotation.z = -elapsed * 0.027;
        microNodes.rotation.y = -elapsed * 0.043;
    }

    function dispose() {
        if (disposed) return;
        disposed = true;
        const geometries = new Set();
        const materials = new Set();
        root.traverse((object) => {
            if (object.geometry) geometries.add(object.geometry);
            if (Array.isArray(object.material)) object.material.forEach((material) => materials.add(material));
            else if (object.material) materials.add(object.material);
        });
        geometries.forEach((geometry) => geometry.dispose());
        materials.forEach((material) => material.dispose());
        root.clear();
    }

    return {
        root,
        coreAssembly,
        backdrop,
        core,
        inner,
        halo,
        chromaticShell,
        filaments,
        chambers,
        scanVolume,
        surfaceNetwork,
        rings,
        signalArcs,
        telemetryTicks,
        particles,
        constellationNetwork,
        energyNodes,
        microNodes,
        textures: [],
        update,
        dispose
    };
}

function createProceduralBackdrop(THREE) {
    const material = new THREE.ShaderMaterial({
        uniforms: {
            uTime: { value: 0 },
            uPointer: { value: new THREE.Vector2() }
        },
        vertexShader: BACKDROP_VERTEX_SHADER,
        fragmentShader: BACKDROP_FRAGMENT_SHADER,
        side: THREE.BackSide,
        depthWrite: false
    });
    const backdrop = new THREE.Mesh(new THREE.SphereGeometry(31, 48, 36), material);
    backdrop.renderOrder = -10;
    return backdrop;
}

function createChromaticShell(THREE) {
    const material = new THREE.ShaderMaterial({
        uniforms: { uTime: { value: 0 } },
        vertexShader: HALO_VERTEX_SHADER,
        fragmentShader: `
            precision highp float;
            uniform float uTime;
            varying vec3 vNormalView;
            varying vec3 vViewPosition;
            void main() {
                vec3 viewDirection = normalize(-vViewPosition);
                float rim = pow(1.0 - abs(dot(vNormalView, viewDirection)), 2.15);
                float bands = sin(rim * 36.0 - uTime * 0.42) * 0.5 + 0.5;
                vec3 violet = vec3(0.54, 0.18, 1.0);
                vec3 cyan = vec3(0.16, 0.88, 1.0);
                vec3 amber = vec3(1.0, 0.38, 0.06);
                vec3 color = mix(violet, cyan, rim);
                color = mix(color, amber, pow(bands, 12.0) * rim * 0.22);
                gl_FragColor = vec4(color, rim * 0.115);
            }
        `,
        transparent: true,
        depthWrite: false,
        blending: THREE.AdditiveBlending,
        side: THREE.DoubleSide
    });
    const shell = new THREE.Mesh(new THREE.SphereGeometry(2.09, 72, 54), material);
    shell.renderOrder = 6;
    return shell;
}

function createInternalFilaments(THREE, random) {
    const group = new THREE.Group();
    const palette = [0x72e7ff, 0x4f7dff, 0x9a6cff, 0xff8248];

    for (let index = 0; index < 14; index++) {
        const startAngle = random() * Math.PI * 2;
        const endAngle = startAngle + Math.PI * (0.75 + random() * 0.85);
        const yBias = (random() - 0.5) * 1.25;
        const points = [];
        for (let step = 0; step < 7; step++) {
            const t = step / 6;
            const angle = startAngle + (endAngle - startAngle) * t;
            const radius = 0.4 + Math.sin(t * Math.PI) * (0.95 + random() * 0.22);
            points.push(new THREE.Vector3(
                Math.cos(angle) * radius,
                yBias * (1 - t) + Math.sin(t * Math.PI * 2 + index) * 0.37,
                Math.sin(angle) * radius
            ));
        }
        const curve = new THREE.CatmullRomCurve3(points);
        const material = new THREE.MeshBasicMaterial({
            color: palette[index % palette.length],
            transparent: true,
            opacity: index % 4 === 3 ? 0.34 : 0.48,
            depthWrite: false,
            blending: THREE.AdditiveBlending
        });
        const filament = new THREE.Mesh(
            new THREE.TubeGeometry(curve, 48, index % 5 === 0 ? 0.016 : 0.009, 5, false),
            material
        );
        filament.userData.baseOpacity = material.opacity;
        filament.userData.pulseSpeed = 0.62 + random() * 1.15;
        group.add(filament);
    }
    group.renderOrder = 4;
    return group;
}

function createInnerChambers(THREE) {
    const group = new THREE.Group();
    const specs = [
        { geometry: new THREE.DodecahedronGeometry(1.28, 0), color: 0x72e7ff, opacity: 0.13, scale: 1, rotation: [0.18, -0.2, 0.08] },
        { geometry: new THREE.IcosahedronGeometry(0.96, 1), color: 0x8e72ff, opacity: 0.11, scale: 1, rotation: [-0.3, 0.22, -0.14] },
        { geometry: new THREE.OctahedronGeometry(0.67, 1), color: 0x58a6ff, opacity: 0.14, scale: 1, rotation: [0.4, 0.14, 0.3] },
        { geometry: new THREE.TetrahedronGeometry(0.39, 1), color: 0xff8248, opacity: 0.16, scale: 1, rotation: [-0.18, -0.34, 0.22] }
    ];

    specs.forEach((spec, index) => {
        const geometry = new THREE.EdgesGeometry(spec.geometry, 12);
        spec.geometry.dispose();
        const material = new THREE.LineBasicMaterial({
            color: spec.color,
            transparent: true,
            opacity: spec.opacity,
            depthWrite: false,
            blending: THREE.AdditiveBlending
        });
        const chamber = new THREE.LineSegments(geometry, material);
        chamber.rotation.set(...spec.rotation);
        chamber.scale.setScalar(spec.scale);
        chamber.renderOrder = 3 + index;
        group.add(chamber);
    });
    return group;
}

function createScanVolume(THREE) {
    const material = new THREE.ShaderMaterial({
        uniforms: {
            uTime: { value: 0 },
            uPointer: { value: new THREE.Vector2() }
        },
        vertexShader: SCAN_VERTEX_SHADER,
        fragmentShader: SCAN_FRAGMENT_SHADER,
        transparent: true,
        depthWrite: false,
        depthTest: true,
        blending: THREE.AdditiveBlending,
        side: THREE.DoubleSide
    });
    const scan = new THREE.Mesh(new THREE.PlaneGeometry(3.9, 3.9, 1, 1), material);
    scan.rotation.set(Math.PI * 0.43, 0.12, -0.2);
    scan.renderOrder = 4;
    return scan;
}

function createSurfaceNetwork(THREE) {
    const geometry = new THREE.EdgesGeometry(new THREE.IcosahedronGeometry(1.93, 3), 18);
    const material = new THREE.LineBasicMaterial({
        color: 0x8de9ff,
        transparent: true,
        opacity: 0.105,
        depthWrite: false,
        blending: THREE.AdditiveBlending
    });
    const network = new THREE.LineSegments(geometry, material);
    network.rotation.set(-0.18, 0.24, 0.06);
    network.renderOrder = 7;
    return network;
}

function createOrbitSystem(THREE, random) {
    const group = new THREE.Group();
    const specs = [
        { radius: 2.72, tube: 0.009, color: 0x72e7ff, opacity: 0.38, x: 1.16, y: 0.12, z: 0.12, spinSpeed: 0.045 },
        { radius: 3.05, tube: 0.006, color: 0x8878ff, opacity: 0.29, x: 0.86, y: 0.54, z: -0.24, spinSpeed: -0.03 },
        { radius: 3.42, tube: 0.005, color: 0x72e7ff, opacity: 0.19, x: 1.42, y: -0.28, z: 0.66, spinSpeed: 0.025 },
        { radius: 3.8, tube: 0.004, color: 0xff8248, opacity: 0.17, x: 1.02, y: 0.68, z: -0.52, spinSpeed: -0.019 },
        { radius: 4.12, tube: 0.004, color: 0x547cff, opacity: 0.15, x: 1.34, y: 0.15, z: 0.9, spinSpeed: 0.015 }
    ];

    specs.forEach((spec) => {
        const material = new THREE.MeshBasicMaterial({
            color: spec.color,
            transparent: true,
            opacity: spec.opacity,
            depthWrite: false,
            blending: THREE.AdditiveBlending
        });
        const ring = new THREE.Mesh(new THREE.TorusGeometry(spec.radius, spec.tube, 6, 256), material);
        ring.rotation.set(spec.x, spec.y, spec.z);
        ring.scale.y = 0.72 + random() * 0.18;
        ring.userData.baseZ = spec.z;
        ring.userData.baseY = spec.y;
        ring.userData.spinSpeed = spec.spinSpeed;
        group.add(ring);
    });
    return group;
}

function createSignalArcs(THREE, random) {
    const group = new THREE.Group();
    const colors = [0x72e7ff, 0x8878ff, 0x4e7cff, 0xff8248];
    for (let index = 0; index < 10; index++) {
        const radius = 2.45 + index * 0.19 + random() * 0.24;
        const start = random() * Math.PI * 1.8;
        const length = Math.PI * (0.12 + random() * 0.33);
        const curve = new THREE.EllipseCurve(0, 0, radius, radius * (0.55 + random() * 0.28), start, start + length);
        const points = curve.getPoints(42).map((point) => new THREE.Vector3(point.x, point.y, 0));
        const geometry = new THREE.BufferGeometry().setFromPoints(points);
        const material = new THREE.LineBasicMaterial({
            color: colors[index % colors.length],
            transparent: true,
            opacity: 0.22 + random() * 0.32,
            depthWrite: false,
            blending: THREE.AdditiveBlending
        });
        const arc = new THREE.Line(geometry, material);
        arc.rotation.set(random() * 1.5, random() * 1.2, random() * Math.PI);
        arc.userData.baseZ = arc.rotation.z;
        arc.userData.spinSpeed = (random() - 0.5) * 0.12;
        arc.userData.baseOpacity = material.opacity;
        arc.userData.pulseSpeed = 0.45 + random() * 1.4;
        group.add(arc);
    }
    return group;
}

function createTelemetryTicks(THREE) {
    const count = 96;
    const geometry = new THREE.BoxGeometry(0.012, 0.085, 0.012);
    const material = new THREE.MeshBasicMaterial({
        color: 0xb8f4ff,
        transparent: true,
        opacity: 0.34,
        depthWrite: false,
        blending: THREE.AdditiveBlending
    });
    const ticks = new THREE.InstancedMesh(geometry, material, count);
    const dummy = new THREE.Object3D();

    for (let index = 0; index < count; index++) {
        const band = index % 3;
        const step = Math.floor(index / 3);
        const angle = (step / 32) * Math.PI * 2 + band * 0.037;
        const radius = 2.2 + band * 0.12;
        const latitude = (band - 1) * 0.34;
        const ellipse = 0.78 + band * 0.055;
        dummy.position.set(
            Math.cos(angle) * radius,
            latitude + Math.sin(angle * 2.0 + band) * 0.035,
            Math.sin(angle) * radius * ellipse
        );
        dummy.rotation.set(Math.PI * 0.5, -angle, angle + Math.PI * 0.5);
        const major = step % 8 === 0 ? 2.1 : step % 4 === 0 ? 1.45 : 0.78;
        dummy.scale.set(1, major, 1);
        dummy.updateMatrix();
        ticks.setMatrixAt(index, dummy.matrix);
    }
    ticks.instanceMatrix.needsUpdate = true;
    ticks.renderOrder = 7;
    return ticks;
}

function createConstellationNetwork(THREE, random) {
    const nodes = [];
    for (let index = 0; index < 42; index++) {
        const radius = 8.5 + random() * 7.5;
        const theta = random() * Math.PI * 2;
        const phi = Math.acos(2 * random() - 1);
        nodes.push(new THREE.Vector3(
            radius * Math.sin(phi) * Math.cos(theta),
            radius * Math.cos(phi) * 0.6,
            radius * Math.sin(phi) * Math.sin(theta)
        ));
    }

    const positions = [];
    nodes.forEach((node, index) => {
        const next = nodes[(index + 1) % nodes.length];
        positions.push(node.x, node.y, node.z, next.x, next.y, next.z);
        if (index % 4 === 0) {
            const skip = nodes[(index + 7) % nodes.length];
            positions.push(node.x, node.y, node.z, skip.x, skip.y, skip.z);
        }
    });

    const geometry = new THREE.BufferGeometry();
    geometry.setAttribute("position", new THREE.Float32BufferAttribute(positions, 3));
    const material = new THREE.LineBasicMaterial({
        color: 0x4f7dff,
        transparent: true,
        opacity: 0.045,
        depthWrite: false,
        blending: THREE.AdditiveBlending
    });
    const network = new THREE.LineSegments(geometry, material);
    network.renderOrder = -2;
    return network;
}

function createParticleField(THREE, random, particleCount) {
    const positions = new Float32Array(particleCount * 3);
    const scales = new Float32Array(particleCount);
    const colors = new Float32Array(particleCount * 3);
    const cyan = new THREE.Color(0x72e7ff);
    const violet = new THREE.Color(0x8878ff);
    const amber = new THREE.Color(0xff9a54);
    const mixed = new THREE.Color();

    for (let index = 0; index < particleCount; index++) {
        const band = index % 5;
        const radius = 2.65 + Math.pow(random(), 0.72) * (band === 0 ? 5.8 : 4.4);
        const theta = random() * Math.PI * 2;
        const latitude = (random() - 0.5) * (band === 0 ? 3.4 : 2.1);
        const offset = index * 3;
        positions[offset] = Math.cos(theta) * radius;
        positions[offset + 1] = latitude + Math.sin(theta * 2.0) * 0.2;
        positions[offset + 2] = Math.sin(theta) * radius * (0.46 + random() * 0.5);
        scales[index] = 0.01 + random() * (index % 29 === 0 ? 0.11 : 0.033);

        if (index % 29 === 0) mixed.copy(amber);
        else mixed.copy(violet).lerp(cyan, random());
        colors[offset] = mixed.r;
        colors[offset + 1] = mixed.g;
        colors[offset + 2] = mixed.b;
    }

    const geometry = new THREE.BufferGeometry();
    geometry.setAttribute("position", new THREE.BufferAttribute(positions, 3));
    geometry.setAttribute("aScale", new THREE.BufferAttribute(scales, 1));
    geometry.setAttribute("aColor", new THREE.BufferAttribute(colors, 3));
    const material = new THREE.ShaderMaterial({
        vertexShader: PARTICLE_VERTEX_SHADER,
        fragmentShader: PARTICLE_FRAGMENT_SHADER,
        transparent: true,
        depthWrite: false,
        blending: THREE.AdditiveBlending,
        vertexColors: true
    });
    return new THREE.Points(geometry, material);
}

function createEnergyNodes(THREE, random) {
    const group = new THREE.Group();
    const geometry = new THREE.SphereGeometry(0.028, 10, 8);
    const cyanMaterial = new THREE.MeshBasicMaterial({
        color: 0x72e7ff,
        transparent: true,
        opacity: 0.9,
        depthWrite: false,
        blending: THREE.AdditiveBlending
    });
    const amberMaterial = new THREE.MeshBasicMaterial({
        color: 0xff8248,
        transparent: true,
        opacity: 0.86,
        depthWrite: false,
        blending: THREE.AdditiveBlending
    });

    for (let index = 0; index < 18; index++) {
        const angle = (index / 18) * Math.PI * 2;
        const radius = 2.72 + (index % 4) * 0.36;
        const node = new THREE.Mesh(geometry, index % 7 === 0 ? amberMaterial : cyanMaterial);
        node.position.set(
            Math.cos(angle) * radius,
            Math.sin(angle * 2.0) * (0.4 + random() * 0.65),
            Math.sin(angle) * radius * 0.5
        );
        node.scale.setScalar(index % 6 === 0 ? 2.2 : 1);
        group.add(node);
    }
    return group;
}

function createMicroNodes(THREE, random) {
    const group = new THREE.Group();
    const geometry = new THREE.OctahedronGeometry(0.028, 0);
    const materials = [
        new THREE.MeshBasicMaterial({ color: 0x9ef1ff, transparent: true, opacity: 0.86, depthWrite: false, blending: THREE.AdditiveBlending }),
        new THREE.MeshBasicMaterial({ color: 0x8f72ff, transparent: true, opacity: 0.72, depthWrite: false, blending: THREE.AdditiveBlending }),
        new THREE.MeshBasicMaterial({ color: 0xff8248, transparent: true, opacity: 0.8, depthWrite: false, blending: THREE.AdditiveBlending })
    ];
    for (let index = 0; index < 24; index++) {
        const node = new THREE.Mesh(geometry, materials[index % materials.length]);
        const angle = random() * Math.PI * 2;
        const phi = Math.acos(2 * random() - 1);
        const radius = 1.73 + random() * 0.18;
        node.position.set(
            radius * Math.sin(phi) * Math.cos(angle),
            radius * Math.cos(phi),
            radius * Math.sin(phi) * Math.sin(angle)
        );
        node.rotation.set(random() * Math.PI, random() * Math.PI, random() * Math.PI);
        node.userData.baseScale = index % 8 === 0 ? 1.8 : 0.9 + random() * 0.6;
        node.userData.pulseSpeed = 0.7 + random() * 1.8;
        group.add(node);
    }
    return group;
}

function createSeededRandom(seed) {
    let value = seed >>> 0;
    return () => {
        value += 0x6d2b79f5;
        let result = value;
        result = Math.imul(result ^ (result >>> 15), result | 1);
        result ^= result + Math.imul(result ^ (result >>> 7), result | 61);
        return ((result ^ (result >>> 14)) >>> 0) / 4294967296;
    };
}
