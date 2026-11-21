import * as THREE from "/js/vendor/three.module.js";
import { GLTFLoader } from "/js/vendor/loaders/GLTFLoader.js";
import { RGBELoader } from "/js/vendor/loaders/RGBELoader.js";
import { createLivingMeadowWorld, selectNatureProfile, sampleWind } from "/js/contact-nature-core.js";
import { NATURE_ASSETS, createPhotorealMeadowLayer } from "/js/contact-nature-assets.js?v=2026080521";
import { createNaturePostProcessor } from "/js/contact-nature-post.js?v=2026080521";

const stage = document.querySelector("[data-nature-stage]");
const canvas = document.querySelector("[data-nature-canvas]");
const loader = document.querySelector("[data-nature-loader]");
const loaderLine = document.querySelector("[data-nature-loader-line]");
const loaderValue = document.querySelector("[data-nature-loader-value]");
const windSpeed = document.querySelector("[data-wind-speed]");
const humidity = document.querySelector("[data-field-humidity]");
const light = document.querySelector("[data-field-light]");
const gust = document.querySelector("[data-field-gust]");
const fieldClock = document.querySelector("[data-field-clock]");
const errorMessage = document.querySelector("[data-nature-error]");
const motionPreference = window.matchMedia("(prefers-reduced-motion: reduce)");

if (stage && canvas) {
    initializeNatureExperience().catch(showFallback);
}

async function initializeNatureExperience() {
    let loadingProgress = 4;
    const loadingStarted = performance.now();
    const loadingTimer = window.setInterval(() => {
        loadingProgress = Math.min(94, loadingProgress + Math.max(1, Math.round((96 - loadingProgress) * 0.09)));
        writeLoader(loadingProgress);
    }, 42);

    const scene = new THREE.Scene();
    scene.background = new THREE.Color(0xe4efd8);
    scene.fog = new THREE.FogExp2(0xe4ecd8, 0.0084);
    const camera = new THREE.PerspectiveCamera(39, 1, 0.1, 80);
    const clock = new THREE.Clock(false);
    const pointerTarget = new THREE.Vector2();
    const pointer = new THREE.Vector2();
    let world;
    let photorealLayer;
    let postProcessor;
    let renderer;
    let pmremGenerator;
    let environmentMap;
    let sourceEnvironment;
    let disposed = false;
    let lastTelemetry = -1;

    try {
        renderer = new THREE.WebGLRenderer({
            canvas,
            antialias: window.innerWidth >= 720,
            alpha: false,
            powerPreference: "high-performance"
        });
    } catch (error) {
        window.clearInterval(loadingTimer);
        throw error;
    }

    renderer.outputColorSpace = THREE.SRGBColorSpace;
    renderer.toneMapping = THREE.ACESFilmicToneMapping;
    renderer.toneMappingExposure = 1.14;
    renderer.shadowMap.enabled = true;
    renderer.shadowMap.type = THREE.PCFSoftShadowMap;

    const initialProfile = selectNatureProfile(window.innerWidth, window.innerHeight, window.devicePixelRatio);
    postProcessor = createNaturePostProcessor(THREE, renderer, {
        width: Math.max(1, stage.clientWidth),
        height: Math.max(1, stage.clientHeight),
        pixelRatio: initialProfile.pixelRatio
    });
    world = createLivingMeadowWorld(THREE, {
        seed: 917,
        ...initialProfile,
        grassCount: 24,
        flowerCount: initialProfile.mobile ? 12 : 26,
        grassWidthScale: 0.3,
        grassHeightScale: 0.34,
        grassColorLift: 0.07
    });
    scene.add(world.root);
    stage.dataset.detailLayers = "hdri,pbr-ground,scanned-grass,scanned-dandelions,wildflowers,pollen,mist";

    const hemisphere = new THREE.HemisphereLight(0xfffced, 0x6f8845, 1.28);
    const sunrise = new THREE.DirectionalLight(0xffe3a0, 2.34);
    sunrise.position.set(7.5, 12, 5.5);
    sunrise.castShadow = true;
    sunrise.shadow.mapSize.set(initialProfile.mobile ? 1024 : 2048, initialProfile.mobile ? 1024 : 2048);
    sunrise.shadow.camera.left = -15;
    sunrise.shadow.camera.right = 15;
    sunrise.shadow.camera.top = 12;
    sunrise.shadow.camera.bottom = -8;
    sunrise.shadow.camera.near = 0.5;
    sunrise.shadow.camera.far = 45;
    sunrise.shadow.bias = -0.00012;
    scene.add(hemisphere, sunrise);

    try {
        const assets = await loadPhotorealAssets(renderer);
        sourceEnvironment = assets.environment;
        pmremGenerator = new THREE.PMREMGenerator(renderer);
        pmremGenerator.compileEquirectangularShader();
        environmentMap = pmremGenerator.fromEquirectangular(sourceEnvironment).texture;
        scene.environment = environmentMap;
        scene.environmentIntensity = 1.2;
        scene.background = sourceEnvironment;
        scene.backgroundBlurriness = initialProfile.mobile ? 0.045 : 0.018;
        scene.backgroundIntensity = 1.04;
        scene.backgroundRotation.y = -0.42;
        scene.environmentRotation.y = -0.42;
        photorealLayer = createPhotorealMeadowLayer(THREE, {
            maps: assets.maps,
            vegetationScenes: [{
                name: "moss-carpet",
                scene: assets.moss.scene,
                alphaMap: assets.alpha.moss,
                count: initialProfile.mobile ? 660 : 1800,
                scale: [0.72, 1.42],
                targetHeight: 0.1,
                horizontalScale: 1.75,
                zRange: [7.6, -23.4],
                clusterCount: 0,
                wind: 0.32,
                alphaTest: 0.28,
                castShadow: false
            }, {
                name: "grass-foreground",
                scene: assets.lushGrass.scene,
                alphaMap: assets.alpha.lushGrass,
                count: initialProfile.mobile ? 320 : 760,
                scale: [0.48, 0.92],
                targetHeight: 0.28,
                zRange: [7.8, 0.4],
                clusterCount: 0,
                wind: 1.16,
                alphaTest: 0.31,
                castShadow: false
            }, {
                name: "grass-near",
                scene: assets.lushGrass.scene,
                alphaMap: assets.alpha.lushGrass,
                count: initialProfile.mobile ? 360 : 850,
                scale: [0.5, 0.96],
                targetHeight: 0.34,
                zRange: [3.2, -12.5],
                clusterCount: 0,
                wind: 1.08,
                alphaTest: 0.3,
                castShadow: false
            }, {
                name: "grass-mid",
                scene: assets.meadowGrass.scene,
                alphaMap: assets.alpha.meadowGrass,
                count: initialProfile.mobile ? 400 : 950,
                scale: [0.46, 0.92],
                targetHeight: 0.3,
                zRange: [-3.2, -23.8],
                clusterCount: 0,
                wind: 0.88,
                alphaTest: 0.34,
                castShadow: false
            }, {
                name: "dandelion-rosettes",
                scene: assets.dandelion.scene,
                alphaMap: assets.alpha.dandelion,
                count: initialProfile.mobile ? 32 : 80,
                scale: [0.62, 1.02],
                targetHeight: 0.25,
                horizontalScale: 0.52,
                yOffset: -0.035,
                zRange: [2.5, -18.8],
                clusterCount: 9,
                clusterSpread: 0.12,
                quietZone: 0.08,
                wind: 0.5,
                alphaTest: 0.43,
                castShadow: false
            }, {
                name: "dandelion-foreground",
                scene: assets.dandelion.scene,
                alphaMap: assets.alpha.dandelion,
                count: initialProfile.mobile ? 600 : 1500,
                scale: [0.86, 1.42],
                targetHeight: 0.82,
                horizontalScale: 0.72,
                minHeightRatio: 0.56,
                yOffset: -0.04,
                zRange: [5.8, -7.2],
                clusterCount: 0,
                quietZone: 0,
                wind: 0.84,
                alphaTest: 0.4,
                castShadow: false
            }, {
                name: "dandelion-colonies",
                scene: assets.dandelion.scene,
                alphaMap: assets.alpha.dandelion,
                count: initialProfile.mobile ? 1320 : 3400,
                scale: [0.72, 1.3],
                targetHeight: 0.72,
                horizontalScale: 0.68,
                minHeightRatio: 0.56,
                yOffset: -0.035,
                zRange: [4.4, -21.8],
                clusterCount: 0,
                quietZone: 0,
                wind: 0.78,
                alphaTest: 0.43,
                castShadow: false
            }, {
                name: "dandelion-sunlit-distance",
                scene: assets.dandelion.scene,
                alphaMap: assets.alpha.dandelion,
                count: initialProfile.mobile ? 620 : 1700,
                scale: [0.62, 1.08],
                targetHeight: 0.58,
                horizontalScale: 0.72,
                minHeightRatio: 0.56,
                yOffset: -0.03,
                zRange: [-5.4, -24.2],
                clusterCount: 14,
                clusterSpread: 0.19,
                quietZone: 0,
                wind: 0.68,
                alphaTest: 0.42,
                castShadow: false
            }, {
                name: "secondary-flowers",
                scene: assets.wildflower.scene,
                alphaMap: assets.alpha.wildflower,
                count: initialProfile.mobile ? 120 : 300,
                scale: [0.66, 1.14],
                targetHeight: 0.56,
                horizontalScale: 0.78,
                minHeightRatio: 0.52,
                zRange: [2.2, -19.5],
                clusterCount: 0,
                quietZone: 0,
                wind: 0.72,
                alphaTest: 0.38,
                castShadow: false
            }],
            seed: 917
        });
        scene.add(photorealLayer.root);
        world.sky.visible = false;
        world.terrain.visible = false;
        world.grass.visible = false;
        world.fineGrass.visible = false;
        world.seedHeads.visible = false;
        world.flowers.visible = false;
        world.dew.visible = false;
        world.foreground.visible = false;
        stage.dataset.assetState = "photoreal";
    } catch (assetError) {
        stage.dataset.assetState = "procedural";
        errorMessage.textContent = "PROCEDURAL FIELD / HIGH-RES LAYER UNAVAILABLE";
        console.info("Photoreal meadow layer unavailable; procedural field retained", assetError);
    }

    const resize = () => {
        if (disposed) return;
        const width = Math.max(1, stage.clientWidth);
        const height = Math.max(1, stage.clientHeight);
        const aspect = width / height;
        const profile = selectNatureProfile(width, height, window.devicePixelRatio);
        renderer.setPixelRatio(profile.pixelRatio);
        renderer.setSize(width, height, false);
        postProcessor.resize(width, height, profile.pixelRatio);
        camera.aspect = aspect;
        camera.fov = profile.mobile ? 46 : aspect > 2 ? 35 : 39;
        camera.position.set(0, profile.mobile ? 1.05 : 0.72, profile.mobile ? 10.4 : aspect > 2 ? 11.2 : 9.5);
        camera.lookAt(0, profile.mobile ? 1.48 : 0.82, -8.2);
        camera.updateProjectionMatrix();
        world.root.scale.setScalar(profile.mobile ? 0.95 : 1);
        world.root.position.y = profile.mobile ? -0.12 : 0;
        if (photorealLayer) {
            photorealLayer.root.scale.setScalar(profile.mobile ? 0.94 : 1);
            photorealLayer.root.position.y = profile.mobile ? -0.08 : 0;
        }
    };

    const setPointer = (clientX, clientY) => {
        pointerTarget.set(
            THREE.MathUtils.clamp(clientX / window.innerWidth * 2 - 1, -1, 1),
            THREE.MathUtils.clamp(-(clientY / window.innerHeight * 2 - 1), -1, 1)
        );
    };
    const onPointerMove = (event) => setPointer(event.clientX, event.clientY);
    const onTouchMove = (event) => {
        const touch = event.touches[0];
        if (touch) setPointer(touch.clientX, touch.clientY);
    };
    const resetPointer = () => pointerTarget.set(0, 0);

    const renderFrame = () => {
        const delta = Math.min(clock.getDelta(), 0.05);
        const elapsed = motionPreference.matches ? 1.75 : clock.elapsedTime;
        const ease = 1 - Math.exp(-delta * 2.8);
        pointer.lerp(pointerTarget, ease);

        world.update({
            elapsed,
            pointerX: pointer.x,
            pointerY: pointer.y,
            reducedMotion: motionPreference.matches
        });
        photorealLayer?.update({ elapsed, reducedMotion: motionPreference.matches });

        if (!motionPreference.matches) {
            camera.position.x += (pointer.x * 0.16 - camera.position.x) * (1 - Math.exp(-delta * 1.2));
            const mobileView = window.innerWidth < 720;
            camera.position.y += ((mobileView ? 1.05 : 0.72) + pointer.y * 0.045 - camera.position.y) * (1 - Math.exp(-delta * 1.1));
            camera.lookAt(pointer.x * 0.18, (mobileView ? 1.48 : 0.82) + pointer.y * 0.035, -8.2);
        }

        postProcessor.render(scene, camera);
        stage.style.setProperty("--pointer-x", pointer.x.toFixed(3));
        stage.style.setProperty("--pointer-y", pointer.y.toFixed(3));

        if (elapsed - lastTelemetry > 0.16) {
            updateTelemetry(elapsed, pointer.x, pointer.y);
            lastTelemetry = elapsed;
        }
        if (motionPreference.matches) renderer.setAnimationLoop(null);
    };

    const pause = () => {
        renderer.setAnimationLoop(null);
        clock.stop();
    };
    const resume = () => {
        if (disposed) return;
        if (!clock.running) clock.start();
        renderer.setAnimationLoop(renderFrame);
    };
    const onVisibilityChange = () => document.hidden ? pause() : resume();
    const onMotionChange = () => {
        pointerTarget.set(0, 0);
        if (motionPreference.matches) {
            renderer.setAnimationLoop(renderFrame);
        } else {
            resume();
        }
    };
    const dispose = () => {
        if (disposed) return;
        disposed = true;
        pause();
        window.clearInterval(loadingTimer);
        window.removeEventListener("resize", resize);
        window.removeEventListener("pointermove", onPointerMove);
        window.removeEventListener("touchmove", onTouchMove);
        window.removeEventListener("pointerleave", resetPointer);
        document.removeEventListener("visibilitychange", onVisibilityChange);
        motionPreference.removeEventListener("change", onMotionChange);
        world.dispose();
        photorealLayer?.dispose();
        postProcessor.dispose();
        environmentMap?.dispose();
        sourceEnvironment?.dispose();
        pmremGenerator?.dispose();
        renderer.dispose();
    };

    canvas.addEventListener("webglcontextlost", (event) => {
        event.preventDefault();
        pause();
        showFallback(new Error("WebGL context lost"));
    }, { once: true });
    window.addEventListener("resize", resize, { passive: true });
    window.addEventListener("pointermove", onPointerMove, { passive: true });
    window.addEventListener("touchmove", onTouchMove, { passive: true });
    window.addEventListener("pointerleave", resetPointer, { passive: true });
    document.addEventListener("visibilitychange", onVisibilityChange);
    motionPreference.addEventListener("change", onMotionChange);
    window.addEventListener("pagehide", dispose, { once: true });

    resize();
    world.update({ elapsed: 1.75, reducedMotion: motionPreference.matches });
    postProcessor.render(scene, camera);
    const minimumLoaderTime = Math.max(0, 680 - (performance.now() - loadingStarted));
    await new Promise((resolve) => window.setTimeout(resolve, minimumLoaderTime));
    window.clearInterval(loadingTimer);
    writeLoader(100);
    stage.dataset.renderState = "ready";
    window.requestAnimationFrame(() => stage.classList.add("nature-ready"));
    resume();
}

async function loadPhotorealAssets(renderer) {
    const textureLoader = new THREE.TextureLoader();
    const gltfLoader = new GLTFLoader();
    const rgbeLoader = new RGBELoader();
    const loadTexture = (url) => new Promise((resolve, reject) => textureLoader.load(url, resolve, undefined, reject));
    const loadGltf = (url) => new Promise((resolve, reject) => gltfLoader.load(url, resolve, undefined, reject));
    const loadEnvironment = (url) => new Promise((resolve, reject) => rgbeLoader.load(url, resolve, undefined, reject));

    const [dandelion, moss, lushGrass, meadowGrass, wildflower, dandelionAlpha, mossAlpha, lushGrassAlpha, meadowGrassAlpha, wildflowerAlpha, diffuse, normal, roughness, ao, displacement, environment] = await Promise.all([
        loadGltf(NATURE_ASSETS.dandelionModel),
        loadGltf(NATURE_ASSETS.mossModel),
        loadGltf(NATURE_ASSETS.lushGrassModel),
        loadGltf(NATURE_ASSETS.meadowGrassModel),
        loadGltf(NATURE_ASSETS.wildflowerModel),
        loadTexture(NATURE_ASSETS.dandelionAlpha),
        loadTexture(NATURE_ASSETS.mossAlpha),
        loadTexture(NATURE_ASSETS.lushGrassAlpha),
        loadTexture(NATURE_ASSETS.meadowGrassAlpha),
        loadTexture(NATURE_ASSETS.wildflowerAlpha),
        loadTexture(NATURE_ASSETS.groundLushDiffuse),
        loadTexture(NATURE_ASSETS.groundNormal),
        loadTexture(NATURE_ASSETS.groundRoughness),
        loadTexture(NATURE_ASSETS.groundAo),
        loadTexture(NATURE_ASSETS.groundDisplacement),
        loadEnvironment(NATURE_ASSETS.environment)
    ]);
    environment.mapping = THREE.EquirectangularReflectionMapping;
    const anisotropy = Math.min(8, renderer.capabilities.getMaxAnisotropy());
    [diffuse, normal, roughness, ao, displacement].forEach((texture) => {
        texture.anisotropy = anisotropy;
    });
    [dandelionAlpha, mossAlpha, lushGrassAlpha, meadowGrassAlpha, wildflowerAlpha].forEach((texture) => {
        texture.flipY = false;
        texture.anisotropy = anisotropy;
        texture.needsUpdate = true;
    });
    return {
        dandelion,
        moss,
        lushGrass,
        meadowGrass,
        wildflower,
        alpha: {
            dandelion: dandelionAlpha,
            moss: mossAlpha,
            lushGrass: lushGrassAlpha,
            meadowGrass: meadowGrassAlpha,
            wildflower: wildflowerAlpha
        },
        maps: { diffuse, normal, roughness, ao, displacement },
        environment
    };
}

function updateTelemetry(elapsed, pointerX, pointerY) {
    const field = sampleWind(elapsed, pointerX * 7, pointerY * 5, 917);
    if (windSpeed) windSpeed.textContent = `${(2.6 + field.strength * 1.35).toFixed(1)} M/S`;
    if (humidity) humidity.textContent = `${Math.round(82 + Math.sin(elapsed * 0.08) * 2)}%`;
    if (light) light.textContent = `${(6.8 + Math.sin(elapsed * 0.035) * 0.2).toFixed(1)} EV`;
    if (gust) gust.textContent = `NE · ${String(Math.round(field.gust * 9)).padStart(2, "0")}`;
    if (fieldClock) fieldClock.textContent = formatTime(elapsed);
}

function writeLoader(value) {
    if (loaderLine) loaderLine.style.width = `${value}%`;
    if (loaderValue) loaderValue.textContent = String(Math.round(value)).padStart(3, "0");
    if (loader) loader.setAttribute("aria-label", `Growing the field, ${Math.round(value)} percent`);
}

function formatTime(elapsed) {
    const seconds = Math.max(0, Math.floor(elapsed));
    const h = String(Math.floor(seconds / 3600)).padStart(2, "0");
    const m = String(Math.floor(seconds % 3600 / 60)).padStart(2, "0");
    const s = String(seconds % 60).padStart(2, "0");
    return `${h}:${m}:${s}`;
}

function showFallback(error) {
    if (!stage) return;
    stage.dataset.renderState = "fallback";
    stage.classList.add("nature-ready", "nature-failed");
    if (errorMessage) errorMessage.textContent = "A STILL MEADOW / GRAPHICS FALLBACK ACTIVE";
    console.warn("Living meadow fallback activated", error);
}
