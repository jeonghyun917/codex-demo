const THREE_MODULE_URL = "/js/vendor/three.module.js";

const canvas = document.querySelector("[data-home-nature-3d]");

if (canvas) {
    const mobileSceneMedia = window.matchMedia("(max-width: 900px)");
    let sceneStarted = false;
    const startScene = () => {
        if (sceneStarted) {
            return;
        }
        sceneStarted = true;
        startQuantEngineScene(canvas).catch(() => startCanvasFallback(canvas));
    };

    if (mobileSceneMedia.matches) {
        startScene();
    }

    mobileSceneMedia.addEventListener("change", (event) => {
        if (event.matches) {
            startScene();
        }
    });
}

async function startQuantEngineScene(targetCanvas) {
    const THREE = await import(THREE_MODULE_URL);
    const renderer = new THREE.WebGLRenderer({
        canvas: targetCanvas,
        alpha: true,
        antialias: true,
        powerPreference: "high-performance"
    });

    renderer.setClearColor(0xffffff, 0);
    renderer.outputColorSpace = THREE.SRGBColorSpace;
    renderer.toneMapping = THREE.ACESFilmicToneMapping;
    renderer.toneMappingExposure = 1.16;

    const scene = new THREE.Scene();
    scene.fog = new THREE.FogExp2(0xf4f8fb, 0.014);

    const camera = new THREE.PerspectiveCamera(34, 1, 0.1, 90);
    camera.position.set(0, 0.8, 11.2);

    const root = new THREE.Group();
    root.position.set(0, -0.15, -5.45);
    scene.add(root);

    const ambient = new THREE.AmbientLight(0xffffff, 1.2);
    const keyLight = new THREE.DirectionalLight(0xffffff, 4.8);
    const cobaltLight = new THREE.PointLight(0x64b9ff, 12, 34);
    const warmLight = new THREE.PointLight(0xffe4bf, 8, 30);
    const rimLight = new THREE.PointLight(0xffffff, 9, 28);
    keyLight.position.set(-3.8, 5.8, 5.5);
    cobaltLight.position.set(4.2, 1.8, 4.2);
    warmLight.position.set(-4.6, -1.0, 3.8);
    rimLight.position.set(0, 3.9, 5.8);
    scene.add(ambient, keyLight, cobaltLight, warmLight, rimLight);

    const engine = createQuantEngine(THREE);
    const field = createSignalField(THREE);
    root.add(field.points, engine.group);

    const state = {
        width: 0,
        height: 0,
        visible: true,
        reduceMotion: window.matchMedia("(prefers-reduced-motion: reduce)").matches,
        start: performance.now(),
        pointer: new THREE.Vector2(0, 0),
        pointerTarget: new THREE.Vector2(0, 0),
        scroll: 0,
        scrollTarget: 0
    };

    const resize = () => {
        const rect = targetCanvas.getBoundingClientRect();
        const width = Math.max(1, Math.round(rect.width));
        const height = Math.max(1, Math.round(rect.height));
        if (width === state.width && height === state.height) {
            return;
        }
        state.width = width;
        state.height = height;
        renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 1.55));
        renderer.setSize(width, height, false);
        camera.aspect = width / height;
        camera.updateProjectionMatrix();
    };

    const updateScroll = () => {
        const stage = targetCanvas.closest("[data-home-showcase-stage]");
        if (!stage) {
            state.scrollTarget = 0;
            return;
        }
        const rect = stage.getBoundingClientRect();
        const travel = Math.max(1, rect.height - window.innerHeight);
        state.scrollTarget = clamp(-rect.top / travel, 0, 1);
    };

    const updatePointer = (event) => {
        const rect = targetCanvas.getBoundingClientRect();
        if (rect.width <= 0 || rect.height <= 0) {
            return;
        }
        state.pointerTarget.x = clamp(((event.clientX - rect.left) / rect.width) * 2 - 1, -1, 1);
        state.pointerTarget.y = clamp(((event.clientY - rect.top) / rect.height) * -2 + 1, -1, 1);
    };

    const observer = new IntersectionObserver((entries) => {
        state.visible = entries.some((entry) => entry.isIntersecting);
    }, { threshold: 0.01 });
    observer.observe(targetCanvas);

    window.addEventListener("resize", resize, { passive: true });
    window.addEventListener("scroll", updateScroll, { passive: true });
    window.addEventListener("pointermove", updatePointer, { passive: true });
    window.addEventListener("pointerleave", () => state.pointerTarget.set(0, 0), { passive: true });
    document.addEventListener("visibilitychange", () => {
        state.visible = !document.hidden;
    });

    const render = () => {
        resize();
        updateScroll();
        const time = state.reduceMotion ? 0 : (performance.now() - state.start) * 0.001;
        state.pointer.lerp(state.pointerTarget, 0.065);
        state.scroll += (state.scrollTarget - state.scroll) * 0.07;

        if (state.visible) {
            const mobile = state.width < 760;
            camera.position.x = state.pointer.x * 0.62 + Math.sin(time * 0.12) * 0.05;
            camera.position.y = 0.82 + state.pointer.y * 0.18 - state.scroll * 0.18;
            camera.position.z = mobile ? 13.6 - state.scroll * 0.9 : 11.2 - state.scroll * 1.15;
            camera.lookAt(state.pointer.x * 0.16, -0.68 - state.scroll * 0.16, -5.7);

            root.rotation.y = state.pointer.x * 0.03;
            root.rotation.x = -state.pointer.y * 0.014;
            root.position.x = mobile ? 0 : state.pointer.x * 0.08;
            root.position.y = (mobile ? -1.58 : -0.72) - state.scroll * 0.12;
            root.scale.setScalar(mobile ? 0.72 : 1.08);

            cobaltLight.position.x = 4.2 + state.pointer.x * 0.9;
            warmLight.position.x = -4.6 + state.pointer.x * 0.7;
            rimLight.intensity = 8.4 + Math.sin(time * 0.7) * 0.7;

            engine.animate(time, state.pointer, state.scroll);
            field.animate(time, state.pointer, state.scroll);
            renderer.render(scene, camera);
        }

        requestAnimationFrame(render);
    };

    render();
}

function createQuantEngine(THREE) {
    const group = new THREE.Group();
    group.position.set(0, -1.46, 0);

    const cobalt = new THREE.MeshPhysicalMaterial({
        color: 0x0e4f82,
        roughness: 0.18,
        metalness: 0.16,
        clearcoat: 1,
        clearcoatRoughness: 0.05,
        emissive: 0x05243e,
        emissiveIntensity: 0.08
    });
    const cobaltGlow = new THREE.MeshBasicMaterial({
        color: 0x3eb6ff,
        transparent: true,
        opacity: 0.62,
        depthWrite: false,
        blending: THREE.AdditiveBlending
    });
    const glass = new THREE.MeshPhysicalMaterial({
        color: 0xdff6ff,
        roughness: 0.02,
        metalness: 0.02,
        clearcoat: 1,
        clearcoatRoughness: 0.015,
        transparent: true,
        opacity: 0.42,
        transmission: 0.62,
        thickness: 0.9,
        ior: 1.42
    });
    const pearl = new THREE.MeshPhysicalMaterial({
        color: 0xffffff,
        roughness: 0.18,
        metalness: 0.02,
        clearcoat: 1,
        clearcoatRoughness: 0.04,
        sheen: 0.45
    });
    const chrome = new THREE.MeshPhysicalMaterial({
        color: 0xf2f7fb,
        roughness: 0.12,
        metalness: 0.78,
        clearcoat: 0.82,
        clearcoatRoughness: 0.12
    });
    const blueGlass = new THREE.MeshPhysicalMaterial({
        color: 0x2f7cff,
        roughness: 0.04,
        metalness: 0.02,
        clearcoat: 1,
        clearcoatRoughness: 0.02,
        transparent: true,
        opacity: 0.46,
        transmission: 0.28,
        thickness: 0.35,
        emissive: 0x0a58ff,
        emissiveIntensity: 0.16
    });
    const ribbonGlass = new THREE.MeshPhysicalMaterial({
        color: 0x2f82ff,
        roughness: 0.025,
        metalness: 0.04,
        clearcoat: 1,
        clearcoatRoughness: 0.02,
        transparent: true,
        opacity: 0.62,
        transmission: 0.36,
        thickness: 0.52,
        ior: 1.46,
        emissive: 0x0b5bff,
        emissiveIntensity: 0.2
    });
    const pedestalGlass = new THREE.MeshPhysicalMaterial({
        color: 0xf8fbff,
        roughness: 0.08,
        metalness: 0.04,
        clearcoat: 1,
        clearcoatRoughness: 0.04,
        transparent: true,
        opacity: 0.56,
        transmission: 0.34,
        thickness: 0.55
    });
    const darkChrome = new THREE.MeshPhysicalMaterial({
        color: 0x7d8892,
        roughness: 0.16,
        metalness: 0.92,
        clearcoat: 0.78,
        clearcoatRoughness: 0.1
    });

    const core = new THREE.Mesh(new THREE.SphereGeometry(1.52, 96, 64), glass);
    core.scale.set(1.0, 1.0, 1.0);
    group.add(core);

    const marbleTexture = createMarbleTexture(THREE);
    const marble = new THREE.MeshPhysicalMaterial({
        color: 0xffffff,
        map: marbleTexture,
        roughness: 0.26,
        metalness: 0.01,
        clearcoat: 1,
        clearcoatRoughness: 0.06,
        sheen: 0.28
    });
    const innerSphere = new THREE.Mesh(new THREE.SphereGeometry(1.02, 96, 64), marble);
    innerSphere.userData.baseScale = 1;
    group.add(innerSphere);

    const innerWire = new THREE.Mesh(new THREE.IcosahedronGeometry(0.9, 2), new THREE.MeshBasicMaterial({
        color: 0x7ed0ff,
        wireframe: true,
        transparent: true,
        opacity: 0.2,
        depthWrite: false
    }));
    group.add(innerWire);

    const rings = new THREE.Group();
    const ringSpecs = [
        { radius: 2.28, tube: 0.024, x: 1.56, y: 0.02, z: 0.02, material: chrome },
        { radius: 2.66, tube: 0.016, x: 1.5, y: 0.12, z: 0.12, material: cobaltGlow },
        { radius: 3.02, tube: 0.014, x: 1.5, y: -0.14, z: -0.1, material: chrome },
        { radius: 3.42, tube: 0.01, x: 1.62, y: 0.2, z: 0.18, material: cobaltGlow },
        { radius: 3.82, tube: 0.009, x: 1.46, y: 0.14, z: 0.28, material: chrome }
    ];
    const ringMeshes = ringSpecs.map((spec) => {
        const ring = new THREE.Mesh(new THREE.TorusGeometry(spec.radius, spec.tube, 16, 220), spec.material);
        ring.rotation.set(spec.x, spec.y, spec.z);
        ring.userData = {
            speed: 0.08 + spec.radius * 0.028,
            base: ring.rotation.clone(),
            radius: spec.radius
        };
        rings.add(ring);
        return ring;
    });
    group.add(rings);

    const mainBand = new THREE.Mesh(new THREE.TorusGeometry(2.68, 0.16, 30, 280), ribbonGlass);
    mainBand.rotation.set(1.56, -0.04, 0.02);
    mainBand.scale.set(1.3, 0.48, 1.0);
    mainBand.position.y = -0.08;
    mainBand.userData.base = mainBand.rotation.clone();
    group.add(mainBand);

    const mainBandGlow = new THREE.Mesh(new THREE.TorusGeometry(2.72, 0.03, 16, 280), cobaltGlow);
    mainBandGlow.rotation.set(1.56, -0.04, 0.02);
    mainBandGlow.scale.copy(mainBand.scale);
    mainBandGlow.position.copy(mainBand.position);
    mainBandGlow.userData.base = mainBandGlow.rotation.clone();
    group.add(mainBandGlow);

    const chromeBand = new THREE.Mesh(new THREE.TorusGeometry(3.0, 0.035, 16, 240), chrome);
    chromeBand.rotation.set(1.46, -0.1, 0.18);
    chromeBand.userData.base = chromeBand.rotation.clone();
    group.add(chromeBand);

    const ribbonSegments = createDataRibbonSegments(THREE);
    group.add(ribbonSegments.group);

    const orbiters = createOrbiters(THREE, cobalt, chrome, pearl);
    group.add(orbiters.group);

    const dataArcs = createDataArcs(THREE, cobaltGlow);
    group.add(dataArcs.group);

    const pedestal = new THREE.Group();
    pedestal.position.y = -2.24;
    const pedestalBase = new THREE.Mesh(new THREE.CylinderGeometry(3.96, 4.28, 0.18, 192), pedestalGlass);
    const pedestalTop = new THREE.Mesh(new THREE.CylinderGeometry(3.18, 3.48, 0.18, 192), pedestalGlass);
    const pedestalCrown = new THREE.Mesh(new THREE.CylinderGeometry(2.5, 2.86, 0.14, 192), pedestalGlass);
    const pedestalChrome = new THREE.Mesh(new THREE.TorusGeometry(3.18, 0.06, 20, 220), chrome);
    const pedestalOuterChrome = new THREE.Mesh(new THREE.TorusGeometry(3.92, 0.052, 18, 220), chrome);
    const pedestalInnerChrome = new THREE.Mesh(new THREE.TorusGeometry(2.48, 0.04, 16, 220), chrome);
    const pedestalLight = new THREE.Mesh(new THREE.TorusGeometry(3.48, 0.052, 16, 220), cobaltGlow);
    const pedestalInnerLight = new THREE.Mesh(new THREE.TorusGeometry(2.22, 0.038, 14, 200), cobaltGlow);
    pedestalBase.position.y = -0.12;
    pedestalTop.position.y = 0.12;
    pedestalCrown.position.y = 0.33;
    [pedestalChrome, pedestalOuterChrome, pedestalInnerChrome, pedestalLight, pedestalInnerLight].forEach((mesh) => {
        mesh.rotation.x = Math.PI / 2;
    });
    pedestalChrome.position.y = 0.26;
    pedestalOuterChrome.position.y = -0.02;
    pedestalInnerChrome.position.y = 0.4;
    pedestalLight.position.y = 0.06;
    pedestalInnerLight.position.y = 0.43;
    const ridges = createPedestalRidges(THREE, darkChrome);
    pedestal.add(
        pedestalBase,
        pedestalTop,
        pedestalCrown,
        pedestalOuterChrome,
        pedestalChrome,
        pedestalInnerChrome,
        pedestalLight,
        pedestalInnerLight,
        ridges.group
    );
    group.add(pedestal);

    const floor = new THREE.Mesh(
        new THREE.CircleGeometry(5.4, 160),
        new THREE.MeshBasicMaterial({
            color: 0xffffff,
            transparent: true,
            opacity: 0.28,
            depthWrite: false
        })
    );
    floor.rotation.x = -Math.PI / 2;
    floor.position.y = -2.33;
    floor.scale.set(1.42, 0.28, 1);
    group.add(floor);

    const shadow = new THREE.Mesh(
        new THREE.CircleGeometry(4.4, 140),
        new THREE.MeshBasicMaterial({
            color: 0x2e5570,
            transparent: true,
            opacity: 0.12,
            depthWrite: false
        })
    );
    shadow.rotation.x = -Math.PI / 2;
    shadow.position.y = -1.86;
    shadow.scale.set(1.1, 0.26, 1);
    group.add(shadow);

    return {
        group,
        animate(time, pointer, scroll) {
            group.rotation.y = pointer.x * 0.13 + Math.sin(time * 0.11) * 0.025;
            group.rotation.x = pointer.y * -0.05 + Math.sin(time * 0.08) * 0.015;
            group.position.y = -1.46 - scroll * 0.08 + Math.sin(time * 0.5) * 0.025;

            core.rotation.y = time * 0.16;
            core.rotation.x = Math.sin(time * 0.22) * 0.08;
            innerSphere.rotation.x = time * 0.42;
            innerSphere.rotation.y = -time * 0.34;
            innerSphere.scale.setScalar(1 + Math.sin(time * 1.4) * 0.025);
            innerWire.rotation.x = -time * 0.32;
            innerWire.rotation.y = time * 0.48;

            ringMeshes.forEach((ring, index) => {
                ring.rotation.x = ring.userData.base.x + Math.sin(time * 0.28 + index) * 0.05;
                ring.rotation.y = ring.userData.base.y + time * ring.userData.speed * (index % 2 ? -1 : 1) + pointer.x * 0.07;
                ring.rotation.z = ring.userData.base.z + time * ring.userData.speed * 0.65 + pointer.y * 0.04;
                ring.scale.setScalar(1 + Math.sin(time * 0.9 + index) * 0.012);
            });

            mainBand.rotation.x = mainBand.userData.base.x + Math.sin(time * 0.42) * 0.05;
            mainBand.rotation.y = mainBand.userData.base.y + time * 0.16 + pointer.x * 0.08;
            mainBand.rotation.z = mainBand.userData.base.z + time * 0.08;
            const bandPulse = 1 + Math.sin(time * 0.72) * 0.014;
            mainBand.scale.set(1.3 * bandPulse, 0.48 * bandPulse, bandPulse);
            mainBandGlow.rotation.copy(mainBand.rotation);
            mainBandGlow.scale.copy(mainBand.scale);
            mainBandGlow.position.copy(mainBand.position);
            mainBandGlow.material.opacity = 0.42 + Math.sin(time * 1.1) * 0.08;
            chromeBand.rotation.x = chromeBand.userData.base.x + Math.sin(time * 0.3) * 0.04;
            chromeBand.rotation.y = chromeBand.userData.base.y - time * 0.14;
            chromeBand.rotation.z = chromeBand.userData.base.z - time * 0.08 + pointer.y * 0.03;
            rings.rotation.y = time * 0.045;
            pedestal.rotation.y = pointer.x * 0.04 + Math.sin(time * 0.16) * 0.012;
            ridges.group.rotation.y = -time * 0.08;
            pedestalLight.material.opacity = 0.58 + Math.sin(time * 1.2) * 0.08;
            pedestalInnerLight.material.opacity = 0.5 + Math.sin(time * 1.4) * 0.07;
            orbiters.animate(time, pointer);
            dataArcs.animate(time, pointer);
            ribbonSegments.animate(time, pointer);
            floor.material.opacity = 0.16 + Math.sin(time * 0.7) * 0.018;
            shadow.scale.x = 1.08 + pointer.x * 0.02;
        }
    };
}

function createOrbiters(THREE, cobalt, chrome, pearl) {
    const group = new THREE.Group();
    const orbiters = [];
    const configs = [
        { r: 2.45, size: 0.11, speed: 0.48, phase: 0.2, material: cobalt, y: 0.64, zScale: 0.36 },
        { r: 2.95, size: 0.08, speed: -0.38, phase: 1.08, material: chrome, y: 0.2, zScale: 0.3 },
        { r: 3.3, size: 0.13, speed: 0.34, phase: 1.68, material: pearl, y: -0.06, zScale: 0.34 },
        { r: 3.68, size: 0.17, speed: -0.28, phase: 2.62, material: cobalt, y: -0.48, zScale: 0.3 },
        { r: 2.12, size: 0.07, speed: 0.68, phase: 3.14, material: chrome, y: 0.92, zScale: 0.4 },
        { r: 3.95, size: 0.07, speed: -0.22, phase: 3.72, material: pearl, y: 0.38, zScale: 0.26 },
        { r: 4.28, size: 0.08, speed: 0.2, phase: 4.24, material: cobalt, y: 0.02, zScale: 0.25 },
        { r: 2.72, size: 0.07, speed: -0.58, phase: 4.86, material: chrome, y: -0.78, zScale: 0.42 },
        { r: 3.48, size: 0.06, speed: 0.5, phase: 5.44, material: pearl, y: 0.98, zScale: 0.34 },
        { r: 4.64, size: 0.06, speed: -0.18, phase: 6.0, material: cobalt, y: 0.68, zScale: 0.22 }
    ];

    configs.forEach((config, index) => {
        const orbiter = new THREE.Mesh(new THREE.SphereGeometry(config.size, 32, 18), config.material);
        const halo = new THREE.Mesh(
            new THREE.TorusGeometry(config.size * 1.65, 0.006, 8, 48),
            new THREE.MeshBasicMaterial({
                color: index % 2 ? 0xffffff : 0x57c6ff,
                transparent: true,
                opacity: 0.38,
                depthWrite: false,
                blending: THREE.AdditiveBlending
            })
        );
        const holder = new THREE.Group();
        holder.add(orbiter, halo);
        holder.userData = config;
        orbiters.push(holder);
        group.add(holder);
    });

    return {
        group,
        animate(time, pointer) {
            orbiters.forEach((holder, index) => {
                const { r, speed, phase, y, zScale } = holder.userData;
                const angle = time * speed + phase + pointer.x * 0.16;
                holder.position.set(
                    Math.cos(angle) * r,
                    y + Math.sin(time * 0.8 + phase) * 0.08 + pointer.y * 0.08,
                    Math.sin(angle) * r * zScale
                );
                holder.rotation.x = time * (0.5 + index * 0.08);
                holder.rotation.y = -time * (0.65 + index * 0.07);
            });
        }
    };
}

function createDataArcs(THREE, material) {
    const group = new THREE.Group();
    const arcs = [];
    for (let i = 0; i < 54; i += 1) {
        const bar = new THREE.Mesh(new THREE.BoxGeometry(0.014, 0.16 + (i % 7) * 0.03, 0.014), material);
        const radius = 2.9 + (i % 9) * 0.13;
        const angle = (i / 54) * Math.PI * 2;
        bar.position.set(Math.cos(angle) * radius, -1.12 + (i % 5) * 0.09, Math.sin(angle) * radius * 0.3);
        bar.rotation.z = -angle;
        bar.userData = { angle, radius, phase: i * 0.35 };
        arcs.push(bar);
        group.add(bar);
    }

    return {
        group,
        animate(time, pointer) {
            arcs.forEach((bar) => {
                const angle = bar.userData.angle + time * 0.08;
                const radius = bar.userData.radius + Math.sin(time * 0.8 + bar.userData.phase) * 0.08;
                bar.position.x = Math.cos(angle) * radius + pointer.x * 0.05;
                bar.position.z = Math.sin(angle) * radius * 0.32;
                bar.scale.y = 0.82 + Math.sin(time * 1.6 + bar.userData.phase) * 0.28;
            });
        }
    };
}

function createDataRibbonSegments(THREE) {
    const group = new THREE.Group();
    const cards = [
        { text: "Date 20D 63D 126D", x: -1.08, y: -0.06, z: 1.34, rot: -0.18 },
        { text: "ALPHA 2.4  PROB 58", x: 0.08, y: -0.12, z: 1.42, rot: -0.05 },
        { text: "BETA 0.92  RISK OK", x: 1.2, y: -0.08, z: 1.24, rot: 0.16 },
        { text: "REGIME NEUTRAL", x: 0.72, y: -0.42, z: 1.5, rot: 0.08 }
    ];

    const meshes = cards.map((card, index) => {
        const mesh = new THREE.Mesh(
            new THREE.PlaneGeometry(1.04, 0.25),
            new THREE.MeshBasicMaterial({
                map: createRibbonTexture(THREE, card.text),
                transparent: true,
                opacity: 0.72,
                depthWrite: false,
                blending: THREE.AdditiveBlending
            })
        );
        mesh.position.set(card.x, card.y, card.z);
        mesh.rotation.set(-0.18, card.rot, -0.08 + card.rot * 0.2);
        mesh.userData = {
            base: mesh.position.clone(),
            phase: index * 0.64,
            rotation: mesh.rotation.clone()
        };
        group.add(mesh);
        return mesh;
    });

    return {
        group,
        animate(time, pointer) {
            meshes.forEach((mesh) => {
                mesh.position.y = mesh.userData.base.y + Math.sin(time * 0.78 + mesh.userData.phase) * 0.018 + pointer.y * 0.03;
                mesh.position.x = mesh.userData.base.x + pointer.x * 0.06;
                mesh.rotation.y = mesh.userData.rotation.y + pointer.x * 0.035;
                mesh.material.opacity = 0.62 + Math.sin(time * 1.1 + mesh.userData.phase) * 0.08;
            });
        }
    };
}

function createPedestalRidges(THREE, material) {
    const group = new THREE.Group();
    const count = 72;
    const ridgeGeometry = new THREE.BoxGeometry(0.032, 0.12, 0.16);
    for (let i = 0; i < count; i += 1) {
        const angle = (i / count) * Math.PI * 2;
        const ridge = new THREE.Mesh(ridgeGeometry, material);
        ridge.position.set(Math.cos(angle) * 3.03, 0.09, Math.sin(angle) * 3.03);
        ridge.rotation.y = -angle;
        ridge.scale.y = 0.62 + (i % 3) * 0.16;
        group.add(ridge);
    }
    return { group };
}

function createRibbonTexture(THREE, text) {
    const c = document.createElement("canvas");
    c.width = 720;
    c.height = 180;
    const ctx = c.getContext("2d");
    ctx.clearRect(0, 0, c.width, c.height);
    const gradient = ctx.createLinearGradient(0, 0, c.width, c.height);
    gradient.addColorStop(0, "rgba(38, 129, 255, 0.08)");
    gradient.addColorStop(0.35, "rgba(70, 158, 255, 0.82)");
    gradient.addColorStop(1, "rgba(255, 255, 255, 0.08)");
    roundRect(ctx, 18, 28, c.width - 36, c.height - 56, 32);
    ctx.fillStyle = gradient;
    ctx.fill();
    ctx.strokeStyle = "rgba(255, 255, 255, 0.66)";
    ctx.lineWidth = 3;
    ctx.stroke();
    ctx.globalAlpha = 0.72;
    ctx.strokeStyle = "rgba(255, 255, 255, 0.6)";
    ctx.lineWidth = 1;
    for (let x = 58; x < c.width - 70; x += 72) {
        ctx.beginPath();
        ctx.moveTo(x, 50);
        ctx.lineTo(x + 28, 50);
        ctx.moveTo(x + 10, 70);
        ctx.lineTo(x + 50, 70);
        ctx.moveTo(x + 2, 94);
        ctx.lineTo(x + 38, 94);
        ctx.stroke();
    }
    ctx.globalAlpha = 1;
    ctx.fillStyle = "rgba(255, 255, 255, 0.92)";
    ctx.font = "700 25px Inter, Arial, sans-serif";
    ctx.textAlign = "center";
    ctx.textBaseline = "middle";
    ctx.fillText(text, c.width / 2, c.height / 2 + 3);

    const texture = new THREE.CanvasTexture(c);
    texture.colorSpace = THREE.SRGBColorSpace;
    texture.anisotropy = 4;
    return texture;
}

function createMarbleTexture(THREE) {
    const c = document.createElement("canvas");
    c.width = 512;
    c.height = 512;
    const ctx = c.getContext("2d");
    const base = ctx.createRadialGradient(170, 120, 10, 260, 260, 360);
    base.addColorStop(0, "#ffffff");
    base.addColorStop(0.52, "#f4f1ea");
    base.addColorStop(1, "#d9d4cc");
    ctx.fillStyle = base;
    ctx.fillRect(0, 0, c.width, c.height);
    ctx.globalAlpha = 0.18;
    for (let i = 0; i < 22; i += 1) {
        ctx.beginPath();
        ctx.strokeStyle = i % 2 ? "#aeb7bf" : "#ffffff";
        ctx.lineWidth = 2 + (i % 4);
        const y = 40 + i * 21;
        ctx.moveTo(-30, y);
        ctx.bezierCurveTo(120, y - 48, 250, y + 54, 560, y - 24);
        ctx.stroke();
    }
    ctx.globalAlpha = 0.12;
    ctx.fillStyle = "#c7ccd1";
    ctx.beginPath();
    ctx.ellipse(340, 336, 110, 54, -0.42, 0, Math.PI * 2);
    ctx.fill();
    ctx.globalAlpha = 1;

    const texture = new THREE.CanvasTexture(c);
    texture.colorSpace = THREE.SRGBColorSpace;
    texture.anisotropy = 8;
    return texture;
}

function createQuantPanels(THREE) {
    const group = new THREE.Group();
    const configs = [
        { label: "SIGNAL", value: "79 / 100", x: -5.2, y: 1.22, z: -0.8, side: -1 },
        { label: "20D ALPHA", value: "+2.4%", x: 4.95, y: 1.05, z: -0.9, side: 1 },
        { label: "UPSIDE", value: "58%", x: -4.7, y: -1.22, z: -0.45, side: -1 },
        { label: "RISK", value: "MEDIUM", x: 4.6, y: -1.28, z: -0.55, side: 1 }
    ];

    const panels = configs.map((config, index) => {
        const mesh = new THREE.Mesh(
            new THREE.PlaneGeometry(1.82, 0.68),
            new THREE.MeshBasicMaterial({
                map: createPanelTexture(THREE, config.label, config.value),
                transparent: true,
                opacity: 0.84,
                depthWrite: false
            })
        );
        mesh.position.set(config.x, config.y, config.z);
        mesh.rotation.y = config.side < 0 ? 0.22 : -0.22;
        mesh.userData = {
            base: mesh.position.clone(),
            rotation: mesh.rotation.y,
            side: config.side,
            phase: index * 0.7
        };
        group.add(mesh);
        return mesh;
    });

    return {
        group,
        animate(time, pointer, scroll) {
            panels.forEach((mesh) => {
                const side = mesh.userData.side;
                mesh.position.x = mesh.userData.base.x + pointer.x * 0.22 * side;
                mesh.position.y = mesh.userData.base.y + pointer.y * 0.1 - scroll * 0.08 + Math.sin(time * 0.42 + mesh.userData.phase) * 0.035;
                mesh.rotation.y = mesh.userData.rotation + pointer.x * side * 0.05;
                mesh.material.opacity = 0.78 + Math.sin(time * 0.8 + mesh.userData.phase) * 0.04;
            });
        }
    };
}

function createSignalField(THREE) {
    const count = 880;
    const positions = new Float32Array(count * 3);
    const colors = new Float32Array(count * 3);
    const seeds = [];
    const color = new THREE.Color();
    for (let i = 0; i < count; i += 1) {
        const radius = THREE.MathUtils.randFloat(3.0, 11.0);
        const angle = Math.random() * Math.PI * 2;
        const x = Math.cos(angle) * radius;
        const y = THREE.MathUtils.randFloat(-3.8, 4.8);
        const z = Math.sin(angle) * radius * 0.55 + THREE.MathUtils.randFloat(-5.5, 1.8);
        positions[i * 3] = x;
        positions[i * 3 + 1] = y;
        positions[i * 3 + 2] = z;
        color.setHSL(0.56 + Math.random() * 0.08, 0.52, 0.68 + Math.random() * 0.16);
        colors[i * 3] = color.r;
        colors[i * 3 + 1] = color.g;
        colors[i * 3 + 2] = color.b;
        seeds.push({ x, y, z, angle, radius, phase: Math.random() * Math.PI * 2, speed: 0.06 + Math.random() * 0.16 });
    }

    const geometry = new THREE.BufferGeometry();
    geometry.setAttribute("position", new THREE.BufferAttribute(positions, 3));
    geometry.setAttribute("color", new THREE.BufferAttribute(colors, 3));
    const material = new THREE.PointsMaterial({
        size: 0.032,
        transparent: true,
        opacity: 0.46,
        vertexColors: true,
        depthWrite: false,
        blending: THREE.AdditiveBlending
    });
    const points = new THREE.Points(geometry, material);
    points.frustumCulled = false;

    return {
        points,
        animate(time, pointer, scroll) {
            const attr = geometry.attributes.position;
            for (let i = 0; i < seeds.length; i += 1) {
                const seed = seeds[i];
                const angle = seed.angle + time * seed.speed * 0.18;
                attr.setX(i, Math.cos(angle) * seed.radius + pointer.x * seed.z * -0.012);
                attr.setY(i, seed.y + pointer.y * 0.12 + Math.cos(time * seed.speed + seed.phase) * 0.06);
                attr.setZ(i, Math.sin(angle) * seed.radius * 0.55 + seed.z * 0.22 + scroll * 0.6);
            }
            attr.needsUpdate = true;
            material.opacity = 0.36 + scroll * 0.08;
        }
    };
}

function createPanelTexture(THREE, label, value) {
    const c = document.createElement("canvas");
    c.width = 680;
    c.height = 260;
    const ctx = c.getContext("2d");
    ctx.clearRect(0, 0, c.width, c.height);

    const gradient = ctx.createLinearGradient(0, 0, c.width, c.height);
    gradient.addColorStop(0, "rgba(255, 255, 255, 0.88)");
    gradient.addColorStop(0.48, "rgba(232, 248, 255, 0.78)");
    gradient.addColorStop(1, "rgba(255, 255, 255, 0.62)");
    roundRect(ctx, 28, 24, c.width - 56, c.height - 48, 42);
    ctx.fillStyle = gradient;
    ctx.fill();
    ctx.strokeStyle = "rgba(42, 137, 194, 0.26)";
    ctx.lineWidth = 3;
    ctx.stroke();

    ctx.fillStyle = "rgba(14, 79, 130, 0.74)";
    ctx.font = "800 30px Inter, Arial, sans-serif";
    ctx.textAlign = "left";
    ctx.textBaseline = "top";
    ctx.fillText(label, 66, 56);

    ctx.fillStyle = "#11293d";
    ctx.font = "900 54px Inter, Arial, sans-serif";
    ctx.fillText(value, 66, 116);

    ctx.globalAlpha = 0.8;
    ctx.strokeStyle = "#2a89c2";
    ctx.lineWidth = 8;
    ctx.beginPath();
    ctx.arc(510, 132, 44, 0, Math.PI * 1.6);
    ctx.stroke();
    ctx.globalAlpha = 0.22;
    ctx.strokeStyle = "#9bdfff";
    ctx.lineWidth = 18;
    ctx.beginPath();
    ctx.arc(510, 132, 54, Math.PI * 0.2, Math.PI * 1.55);
    ctx.stroke();
    ctx.globalAlpha = 1;

    const texture = new THREE.CanvasTexture(c);
    texture.colorSpace = THREE.SRGBColorSpace;
    texture.anisotropy = 4;
    return texture;
}

function roundRect(ctx, x, y, width, height, radius) {
    const r = Math.min(radius, width / 2, height / 2);
    ctx.beginPath();
    ctx.moveTo(x + r, y);
    ctx.arcTo(x + width, y, x + width, y + height, r);
    ctx.arcTo(x + width, y + height, x, y + height, r);
    ctx.arcTo(x, y + height, x, y, r);
    ctx.arcTo(x, y, x + width, y, r);
    ctx.closePath();
}

function clamp(value, min, max) {
    return Math.min(max, Math.max(min, value));
}

function startCanvasFallback(targetCanvas) {
    const ctx = targetCanvas.getContext("2d");
    let width = 0;
    let height = 0;
    const pointer = { x: 0, y: 0, tx: 0, ty: 0 };

    const resize = () => {
        const rect = targetCanvas.getBoundingClientRect();
        const ratio = Math.min(window.devicePixelRatio || 1, 1.5);
        width = Math.max(1, Math.round(rect.width));
        height = Math.max(1, Math.round(rect.height));
        targetCanvas.width = Math.round(width * ratio);
        targetCanvas.height = Math.round(height * ratio);
        ctx.setTransform(ratio, 0, 0, ratio, 0, 0);
    };

    const updatePointer = (event) => {
        const rect = targetCanvas.getBoundingClientRect();
        pointer.tx = clamp(((event.clientX - rect.left) / Math.max(1, rect.width)) * 2 - 1, -1, 1);
        pointer.ty = clamp(((event.clientY - rect.top) / Math.max(1, rect.height)) * -2 + 1, -1, 1);
    };

    const draw = (now) => {
        resize();
        const t = now * 0.001;
        pointer.x += (pointer.tx - pointer.x) * 0.08;
        pointer.y += (pointer.ty - pointer.y) * 0.08;

        const gradient = ctx.createRadialGradient(width * 0.5, height * 0.36, 10, width * 0.5, height * 0.5, width * 0.72);
        gradient.addColorStop(0, "#ffffff");
        gradient.addColorStop(0.42, "#edf8ff");
        gradient.addColorStop(1, "#e7dccb");
        ctx.fillStyle = gradient;
        ctx.fillRect(0, 0, width, height);

        ctx.save();
        ctx.translate(width * (0.5 + pointer.x * 0.03), height * (0.5 - pointer.y * 0.02));
        ctx.rotate(pointer.x * 0.05 + Math.sin(t * 0.2) * 0.01);
        const radius = Math.min(width, height) * 0.17;

        ctx.strokeStyle = "#0e4f82";
        ctx.lineWidth = 3;
        for (let i = 0; i < 4; i += 1) {
            ctx.save();
            ctx.rotate(t * (0.18 + i * 0.05) + i);
            ctx.beginPath();
            ctx.ellipse(0, 0, radius * (1.45 + i * 0.18), radius * (0.48 + i * 0.06), 0, 0, Math.PI * 2);
            ctx.stroke();
            ctx.restore();
        }

        const core = ctx.createRadialGradient(-radius * 0.25, -radius * 0.35, 10, 0, 0, radius);
        core.addColorStop(0, "#ffffff");
        core.addColorStop(0.58, "#dff6ff");
        core.addColorStop(1, "#0e4f82");
        ctx.fillStyle = core;
        ctx.beginPath();
        ctx.arc(0, 0, radius * 0.72, 0, Math.PI * 2);
        ctx.fill();
        ctx.restore();

        requestAnimationFrame(draw);
    };

    window.addEventListener("resize", resize, { passive: true });
    window.addEventListener("pointermove", updatePointer, { passive: true });
    requestAnimationFrame(draw);
}
