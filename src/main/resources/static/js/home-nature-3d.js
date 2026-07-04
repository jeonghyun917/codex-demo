const THREE_MODULE_URL = "/js/vendor/three.module.js";

const canvas = document.querySelector("[data-home-nature-3d]");

if (canvas) {
    startPorcelainScene(canvas).catch(() => startCanvasFallback(canvas));
}

async function startPorcelainScene(targetCanvas) {
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
    renderer.toneMappingExposure = 0.96;

    const scene = new THREE.Scene();
    scene.fog = new THREE.FogExp2(0xf3ecdf, 0.028);

    const camera = new THREE.PerspectiveCamera(36, 1, 0.1, 90);
    camera.position.set(0, 1.25, 13.4);

    const root = new THREE.Group();
    root.position.set(0, -0.05, -2.0);
    scene.add(root);

    const ambient = new THREE.AmbientLight(0xffffff, 1.04);
    const windowLight = new THREE.DirectionalLight(0xffffff, 4.2);
    const blueFill = new THREE.PointLight(0xb7d5ea, 9, 38);
    const warmFill = new THREE.PointLight(0xffe0b5, 11, 36);
    const rim = new THREE.PointLight(0xffffff, 7, 28);
    windowLight.position.set(-3.2, 5.8, 5.8);
    blueFill.position.set(5.2, 2.4, 4.8);
    warmFill.position.set(-4.8, -0.4, 4.4);
    rim.position.set(0, 4.2, 7.4);
    scene.add(ambient, windowLight, blueFill, warmFill, rim);

    const tableware = createPorcelainTableware(THREE);
    const labels = createMinimalPorcelainLabels(THREE);
    const dust = createDaylightDust(THREE);
    root.add(dust.points, tableware.group, labels.group);

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
        state.pointer.lerp(state.pointerTarget, 0.06);
        state.scroll += (state.scrollTarget - state.scroll) * 0.07;

        if (state.visible) {
            camera.position.x = state.pointer.x * 0.72 + Math.sin(time * 0.12) * 0.06;
            camera.position.y = 1.25 + state.pointer.y * 0.18 - state.scroll * 0.18;
            camera.position.z = 13.4 - state.scroll * 1.55;
            camera.lookAt(state.pointer.x * 0.22, -0.42 - state.scroll * 0.26, -6.1);

            root.rotation.y = state.pointer.x * 0.026;
            root.rotation.x = -state.pointer.y * 0.014;
            root.position.y = -0.05 - state.scroll * 0.22;
            root.scale.setScalar(state.width < 560 ? 0.94 : 1.26);

            warmFill.position.x = -4.8 + state.pointer.x * 0.9;
            blueFill.position.x = 5.2 + state.pointer.x * 0.8;
            rim.intensity = 6.3 + Math.sin(time * 0.8) * 0.55;

            tableware.animate(time, state.pointer, state.scroll);
            labels.animate(time, state.pointer, state.scroll);
            dust.animate(time, state.pointer, state.scroll);
            renderer.render(scene, camera);
        }

        requestAnimationFrame(render);
    };

    render();
}

function createPorcelainTableware(THREE) {
    const group = new THREE.Group();
    group.position.set(0, -0.15, -6.1);

    const porcelainTexture = createPorcelainTexture(THREE);
    const marbleTexture = createMarbleTexture(THREE);
    const porcelain = new THREE.MeshPhysicalMaterial({
        color: 0xffffff,
        map: porcelainTexture,
        roughness: 0.24,
        metalness: 0.02,
        clearcoat: 0.92,
        clearcoatRoughness: 0.07,
        sheen: 0.25
    });
    const cobalt = new THREE.MeshPhysicalMaterial({
        color: 0x174c78,
        roughness: 0.18,
        metalness: 0.08,
        clearcoat: 0.9,
        clearcoatRoughness: 0.06
    });
    const glass = new THREE.MeshPhysicalMaterial({
        color: 0xdff0f8,
        roughness: 0.02,
        metalness: 0.02,
        clearcoat: 1,
        clearcoatRoughness: 0.02,
        transparent: true,
        opacity: 0.34,
        transmission: 0.42,
        thickness: 0.9,
        ior: 1.42
    });
    const silver = new THREE.MeshPhysicalMaterial({
        color: 0xd8dde0,
        roughness: 0.2,
        metalness: 0.78,
        clearcoat: 0.8,
        clearcoatRoughness: 0.12
    });
    const marble = new THREE.MeshPhysicalMaterial({
        color: 0xf7f1e8,
        map: marbleTexture,
        roughness: 0.3,
        metalness: 0.01,
        clearcoat: 0.58,
        clearcoatRoughness: 0.18
    });
    const shadowMaterial = new THREE.MeshBasicMaterial({
        color: 0x5f5549,
        transparent: true,
        opacity: 0.18,
        depthWrite: false
    });

    const slab = new THREE.Mesh(new THREE.CylinderGeometry(4.75, 5.05, 0.18, 160), marble);
    slab.position.set(0, -1.82, 0);
    slab.scale.set(1.28, 0.42, 0.58);
    group.add(slab);

    const shadow = new THREE.Mesh(new THREE.CircleGeometry(4.8, 128), shadowMaterial);
    shadow.rotation.x = -Math.PI / 2;
    shadow.position.set(0.15, -1.73, 0.08);
    shadow.scale.set(1.2, 0.43, 1);
    group.add(shadow);

    const dinnerPlate = new THREE.Mesh(new THREE.CylinderGeometry(2.38, 2.5, 0.12, 160), porcelain);
    dinnerPlate.position.set(0, -1.48, 0);
    dinnerPlate.scale.set(1, 1, 0.72);
    group.add(dinnerPlate);

    const plateRim = new THREE.Mesh(new THREE.TorusGeometry(2.32, 0.045, 18, 180), porcelain);
    plateRim.rotation.x = Math.PI / 2;
    plateRim.position.set(0, -1.38, 0);
    plateRim.scale.set(1, 0.72, 1);
    group.add(plateRim);

    const blueRim = new THREE.Mesh(new THREE.TorusGeometry(2.18, 0.014, 12, 180), cobalt);
    blueRim.rotation.x = Math.PI / 2;
    blueRim.position.set(0, -1.31, 0);
    blueRim.scale.set(1, 0.72, 1);
    group.add(blueRim);

    const bowl = new THREE.Mesh(
        new THREE.LatheGeometry([
            new THREE.Vector2(0.28, -0.58),
            new THREE.Vector2(1.05, -0.48),
            new THREE.Vector2(1.42, -0.08),
            new THREE.Vector2(1.34, 0.44),
            new THREE.Vector2(0.62, 0.68),
            new THREE.Vector2(0.2, 0.58)
        ], 160),
        porcelain
    );
    bowl.position.set(-0.1, -0.78, 0.02);
    bowl.scale.set(1.05, 1, 0.76);
    group.add(bowl);

    const bowlRim = new THREE.Mesh(new THREE.TorusGeometry(1.35, 0.026, 14, 180), cobalt);
    bowlRim.rotation.x = Math.PI / 2;
    bowlRim.position.set(-0.1, -0.1, 0.02);
    bowlRim.scale.set(1.05, 0.76, 1);
    group.add(bowlRim);

    const cup = new THREE.Group();
    cup.position.set(2.8, -1.0, -0.28);
    cup.rotation.y = -0.28;
    const cupBody = new THREE.Mesh(
        new THREE.LatheGeometry([
            new THREE.Vector2(0.42, -0.58),
            new THREE.Vector2(0.62, -0.46),
            new THREE.Vector2(0.7, 0.32),
            new THREE.Vector2(0.6, 0.74),
            new THREE.Vector2(0.42, 0.82)
        ], 128),
        porcelain
    );
    cup.add(cupBody);
    const cupRim = new THREE.Mesh(new THREE.TorusGeometry(0.62, 0.026, 14, 128), cobalt);
    cupRim.rotation.x = Math.PI / 2;
    cupRim.position.y = 0.78;
    cup.add(cupRim);
    const handlePath = new THREE.CatmullRomCurve3([
        new THREE.Vector3(0.58, 0.5, 0.0),
        new THREE.Vector3(1.0, 0.42, 0.02),
        new THREE.Vector3(1.06, -0.05, 0.02),
        new THREE.Vector3(0.58, -0.2, 0.0)
    ]);
    const handle = new THREE.Mesh(new THREE.TubeGeometry(handlePath, 42, 0.035, 12, false), porcelain);
    cup.add(handle);
    group.add(cup);

    const glassBowl = new THREE.Mesh(
        new THREE.LatheGeometry([
            new THREE.Vector2(0.22, -0.56),
            new THREE.Vector2(0.75, -0.44),
            new THREE.Vector2(0.94, 0.16),
            new THREE.Vector2(0.68, 0.62),
            new THREE.Vector2(0.3, 0.72)
        ], 128),
        glass
    );
    glassBowl.position.set(-2.45, -0.96, -0.35);
    glassBowl.scale.set(0.86, 0.86, 0.72);
    group.add(glassBowl);

    const fork = createFlatware(THREE, silver, -3.2, -1.3, 0.48, -0.2);
    const spoon = createFlatware(THREE, silver, 3.75, -1.28, 0.48, 0.2);
    group.add(fork, spoon);

    const accentTiles = [];
    for (let i = 0; i < 7; i += 1) {
        const tile = new THREE.Mesh(new THREE.BoxGeometry(0.28, 0.018, 0.18), cobalt);
        tile.position.set(-1.2 + i * 0.4, -1.24, 1.06 + Math.sin(i) * 0.08);
        tile.rotation.y = 0.08 * Math.sin(i * 1.7);
        tile.userData.phase = i * 0.44;
        accentTiles.push(tile);
        group.add(tile);
    }

    return {
        group,
        animate(time, pointer, scroll) {
            group.rotation.y = pointer.x * 0.11 + Math.sin(time * 0.16) * 0.012;
            group.rotation.x = -0.06 + pointer.y * -0.035;
            group.position.x = pointer.x * 0.18;
            group.position.y = -0.15 - scroll * 0.12 + Math.sin(time * 0.38) * 0.012;
            slab.rotation.y = Math.sin(time * 0.06) * 0.018;
            dinnerPlate.rotation.y = time * 0.018;
            bowl.rotation.y = -time * 0.025;
            cup.rotation.y = -0.28 + pointer.x * -0.05;
            glassBowl.rotation.y = 0.16 + pointer.x * 0.05;
            shadow.scale.x = 1.18 + pointer.x * 0.018;
            accentTiles.forEach((tile) => {
                tile.position.y = -1.24 + Math.sin(time * 0.8 + tile.userData.phase) * 0.01;
            });
        }
    };
}

function createFlatware(THREE, material, x, y, z, rotation) {
    const group = new THREE.Group();
    group.position.set(x, y, z);
    group.rotation.y = rotation;
    const handle = new THREE.Mesh(new THREE.BoxGeometry(0.08, 0.035, 1.45), material);
    handle.position.z = -0.2;
    group.add(handle);
    const head = new THREE.Mesh(new THREE.SphereGeometry(0.18, 28, 14), material);
    head.scale.set(0.72, 0.16, 1.1);
    head.position.z = 0.65;
    group.add(head);
    return group;
}

function createMinimalPorcelainLabels(THREE) {
    const group = new THREE.Group();
    const configs = [
        { label: "SIGNAL", value: "79 / 100", x: -5.0, y: 0.35, z: -4.7, side: -1 },
        { label: "EXPECTED", value: "20D ALPHA", x: 4.8, y: 0.3, z: -4.9, side: 1 },
        { label: "RISK", value: "COVARIANCE", x: -4.55, y: -1.15, z: -5.2, side: -1 },
        { label: "OPINION", value: "CALIBRATED", x: 4.3, y: -1.05, z: -5.1, side: 1 }
    ];

    const panels = configs.map((config, index) => {
        const mesh = new THREE.Mesh(
            new THREE.PlaneGeometry(2.05, 0.78),
            new THREE.MeshBasicMaterial({
                map: createPorcelainPanelTexture(THREE, config.label, config.value),
                transparent: true,
                opacity: 0.82,
                depthWrite: false
            })
        );
        mesh.position.set(config.x, config.y, config.z);
        mesh.rotation.y = config.side < 0 ? 0.3 : -0.3;
        mesh.userData = {
            base: mesh.position.clone(),
            rotation: mesh.rotation.y,
            side: config.side,
            phase: index * 0.66
        };
        group.add(mesh);
        return mesh;
    });

    return {
        group,
        animate(time, pointer, scroll) {
            panels.forEach((mesh) => {
                const side = mesh.userData.side;
                mesh.position.x = mesh.userData.base.x + pointer.x * 0.28 * side;
                mesh.position.y = mesh.userData.base.y + pointer.y * 0.08 - scroll * 0.1 + Math.sin(time * 0.32 + mesh.userData.phase) * 0.025;
                mesh.rotation.y = mesh.userData.rotation + pointer.x * side * 0.052;
                mesh.material.opacity = 0.72 + Math.sin(time * 0.65 + mesh.userData.phase) * 0.035;
            });
        }
    };
}

function createDaylightDust(THREE) {
    const count = 620;
    const positions = new Float32Array(count * 3);
    const colors = new Float32Array(count * 3);
    const seeds = [];
    const color = new THREE.Color();
    for (let i = 0; i < count; i += 1) {
        const x = THREE.MathUtils.randFloatSpread(24);
        const y = THREE.MathUtils.randFloat(-3.6, 5.8);
        const z = THREE.MathUtils.randFloat(-22, -4);
        positions[i * 3] = x;
        positions[i * 3 + 1] = y;
        positions[i * 3 + 2] = z;
        color.setHSL(0.08 + Math.random() * 0.08, 0.32, 0.72 + Math.random() * 0.14);
        colors[i * 3] = color.r;
        colors[i * 3 + 1] = color.g;
        colors[i * 3 + 2] = color.b;
        seeds.push({ x, y, z, phase: Math.random() * Math.PI * 2, speed: 0.04 + Math.random() * 0.11 });
    }

    const geometry = new THREE.BufferGeometry();
    geometry.setAttribute("position", new THREE.BufferAttribute(positions, 3));
    geometry.setAttribute("color", new THREE.BufferAttribute(colors, 3));
    const material = new THREE.PointsMaterial({
        size: 0.028,
        transparent: true,
        opacity: 0.38,
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
                attr.setX(i, seed.x + pointer.x * seed.z * -0.007 + Math.sin(time * seed.speed + seed.phase) * 0.06);
                attr.setY(i, seed.y + pointer.y * 0.12 + Math.cos(time * seed.speed + seed.phase) * 0.04);
                attr.setZ(i, seed.z + scroll * 0.8);
            }
            attr.needsUpdate = true;
            material.opacity = 0.28 + scroll * 0.08;
        }
    };
}

function createPorcelainTexture(THREE) {
    const c = document.createElement("canvas");
    c.width = 1024;
    c.height = 1024;
    const ctx = c.getContext("2d");
    const base = ctx.createLinearGradient(0, 0, c.width, c.height);
    base.addColorStop(0, "#ffffff");
    base.addColorStop(0.45, "#f8f8f3");
    base.addColorStop(1, "#e8e2d8");
    ctx.fillStyle = base;
    ctx.fillRect(0, 0, c.width, c.height);
    for (let i = 0; i < 80; i += 1) {
        ctx.globalAlpha = 0.035;
        ctx.strokeStyle = i % 2 ? "#b8c6c9" : "#d1bfa4";
        ctx.lineWidth = 1 + (i % 3);
        ctx.beginPath();
        const startY = (i * 67) % c.height;
        ctx.moveTo(-40, startY);
        for (let x = 0; x <= c.width + 80; x += 80) {
            ctx.lineTo(x, startY + Math.sin((x + i * 31) * 0.012) * 16);
        }
        ctx.stroke();
    }
    ctx.globalAlpha = 1;
    const texture = new THREE.CanvasTexture(c);
    texture.colorSpace = THREE.SRGBColorSpace;
    texture.anisotropy = 4;
    return texture;
}

function createMarbleTexture(THREE) {
    const c = document.createElement("canvas");
    c.width = 1024;
    c.height = 1024;
    const ctx = c.getContext("2d");
    const gradient = ctx.createLinearGradient(0, 0, c.width, c.height);
    gradient.addColorStop(0, "#fbf7ee");
    gradient.addColorStop(0.48, "#ebe1d3");
    gradient.addColorStop(1, "#cfc3b3");
    ctx.fillStyle = gradient;
    ctx.fillRect(0, 0, c.width, c.height);
    for (let i = 0; i < 44; i += 1) {
        ctx.globalAlpha = 0.08 + (i % 5) * 0.012;
        ctx.strokeStyle = i % 3 === 0 ? "#8aa2a8" : "#a99174";
        ctx.lineWidth = 1 + (i % 5);
        ctx.beginPath();
        const y = (i * 103) % c.height;
        ctx.moveTo(-120, y);
        for (let x = -120; x <= c.width + 160; x += 90) {
            ctx.lineTo(x, y + Math.sin(x * 0.018 + i) * (18 + (i % 4) * 10));
        }
        ctx.stroke();
    }
    ctx.globalAlpha = 1;
    const texture = new THREE.CanvasTexture(c);
    texture.colorSpace = THREE.SRGBColorSpace;
    texture.anisotropy = 4;
    return texture;
}

function createPorcelainPanelTexture(THREE, label, value) {
    const c = document.createElement("canvas");
    c.width = 680;
    c.height = 280;
    const ctx = c.getContext("2d");
    ctx.clearRect(0, 0, c.width, c.height);

    const gradient = ctx.createLinearGradient(0, 0, c.width, c.height);
    gradient.addColorStop(0, "rgba(255, 255, 255, 0.92)");
    gradient.addColorStop(0.48, "rgba(247, 244, 238, 0.86)");
    gradient.addColorStop(1, "rgba(224, 216, 203, 0.76)");
    roundRect(ctx, 24, 24, c.width - 48, c.height - 48, 40);
    ctx.fillStyle = gradient;
    ctx.fill();
    ctx.strokeStyle = "rgba(23, 76, 120, 0.18)";
    ctx.lineWidth = 3;
    ctx.stroke();

    ctx.fillStyle = "rgba(23, 76, 120, 0.7)";
    ctx.font = "800 30px Inter, Arial, sans-serif";
    ctx.textAlign = "left";
    ctx.textBaseline = "top";
    ctx.fillText(label, 64, 64);

    ctx.fillStyle = "#17242e";
    ctx.font = "800 52px Inter, Arial, sans-serif";
    ctx.fillText(value, 64, 126);

    ctx.globalAlpha = 0.75;
    ctx.fillStyle = "#174c78";
    ctx.fillRect(64, 214, 168, 4);
    ctx.globalAlpha = 0.32;
    ctx.fillStyle = "#8aa2a8";
    ctx.fillRect(244, 214, 48, 4);

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

        const gradient = ctx.createRadialGradient(width * 0.5, height * 0.34, 10, width * 0.5, height * 0.5, width * 0.72);
        gradient.addColorStop(0, "#ffffff");
        gradient.addColorStop(0.42, "#efe7db");
        gradient.addColorStop(1, "#d8cebd");
        ctx.fillStyle = gradient;
        ctx.fillRect(0, 0, width, height);

        ctx.save();
        ctx.translate(width * (0.5 + pointer.x * 0.03), height * (0.48 - pointer.y * 0.02));
        ctx.rotate(pointer.x * 0.05 + Math.sin(t * 0.2) * 0.01);
        const radius = Math.min(width, height) * 0.22;
        ctx.fillStyle = "#ffffff";
        ctx.beginPath();
        ctx.ellipse(0, 0, radius * 1.45, radius * 0.62, 0, 0, Math.PI * 2);
        ctx.fill();
        ctx.strokeStyle = "#174c78";
        ctx.lineWidth = 2;
        ctx.beginPath();
        ctx.ellipse(0, -radius * 0.02, radius * 1.25, radius * 0.5, 0, 0, Math.PI * 2);
        ctx.stroke();
        ctx.fillStyle = "rgba(23, 76, 120, 0.08)";
        ctx.beginPath();
        ctx.ellipse(0, radius * 0.46, radius * 1.6, radius * 0.18, 0, 0, Math.PI * 2);
        ctx.fill();
        ctx.restore();

        requestAnimationFrame(draw);
    };

    window.addEventListener("resize", resize, { passive: true });
    window.addEventListener("pointermove", updatePointer, { passive: true });
    requestAnimationFrame(draw);
}
