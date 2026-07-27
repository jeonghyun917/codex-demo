import {
    shouldRenderFrame
} from "./home-quant-planet-motion.js";
import {
    fitReferenceWorld,
    REFERENCE_HERO
} from "./home-quant-planet-composition.js";
import {
    createQuantGlyphRig
} from "./home-quant-planet-glyphs.js";
import {
    createQuantPlanetWorld
} from "./home-quant-planet-world.js";

const HIGH_LIMITS = Object.freeze({
    drawCalls: 75,
    triangles: 140000
});
const BALANCED_LIMITS = Object.freeze({
    drawCalls: 65,
    triangles: 90000
});
const GLYPH_LAYOUT_RADIUS_RATIO = .504;

const EMPTY_POSE = Object.freeze({
    worldAngle: 0,
    dragAngle: 0,
    pointer: Object.freeze({ x: 0, y: 0 }),
    elapsedMs: 0,
    reduceMotion: true
});

function createStaticSceneController() {
    return {
        resize() {},
        setVisible() {},
        render() {
            return false;
        },
        getDiagnostics() {
            return {
                drawCalls: 0,
                triangles: 0,
                limits: { drawCalls: 0, triangles: 0 },
                withinBudget: true
            };
        },
        dispose() {}
    };
}

export async function createQuantPlanetScene({
    canvas,
    profile,
    three,
    glyphFactory
} = {}) {
    if (!canvas || !profile?.webgl || profile.name === "static") {
        return createStaticSceneController();
    }

    let renderer = null;
    let world = null;
    let glyphs = null;
    const cleanupAttempted = {
        glyphs: false,
        world: false,
        renderer: false
    };

    function disposeResources() {
        let firstError = null;
        for (const [name, resource] of [
            ["glyphs", glyphs],
            ["world", world],
            ["renderer", renderer]
        ]) {
            if (!resource || cleanupAttempted[name]) {
                continue;
            }
            cleanupAttempted[name] = true;
            try {
                resource.dispose();
            } catch (error) {
                firstError ||= error;
            }
        }
        if (firstError) {
            throw firstError;
        }
    }

    try {
        const THREE = three || await import("/js/vendor/three.module.js");
        renderer = new THREE.WebGLRenderer({
            canvas,
            alpha: true,
            antialias: profile.name === "high",
            powerPreference: "high-performance"
        });
        renderer.outputColorSpace = THREE.SRGBColorSpace;
        renderer.toneMapping = THREE.ACESFilmicToneMapping;
        renderer.toneMappingExposure = 1.08;
        renderer.setPixelRatio(Math.min(
            Math.max(Number(profile.pixelRatio) || 1, 1),
            1.5
        ));
        renderer.shadowMap.enabled = Boolean(profile.shadows);
        renderer.shadowMap.type = THREE.PCFSoftShadowMap;

        const scene = new THREE.Scene();
        const camera = new THREE.PerspectiveCamera(34, 1, .1, 40);
        world = createQuantPlanetWorld(THREE, { profile });
        const makeGlyphs = glyphFactory || createQuantGlyphRig;
        glyphs = await makeGlyphs(THREE, {
            profile,
            assetUrl: REFERENCE_HERO.titleAssetUrl
        });
        const worldScale = Math.max(
            .001,
            Number(profile.planetScale) || 1
        );
        world.group.scale.setScalar(worldScale);
        scene.add(world.group, glyphs.group);

        const localEnvelope = world.getFramingEnvelope();
        const envelope = {
            center: {
                x: localEnvelope.center.x * worldScale,
                y: localEnvelope.center.y * worldScale,
                z: localEnvelope.center.z * worldScale
            },
            radius: localEnvelope.radius * worldScale
        };
        const glyphLayoutRadius = envelope.radius
            * GLYPH_LAYOUT_RADIUS_RATIO;
        function refitCamera(aspect) {
            const next = fitReferenceWorld({
                center: envelope.center,
                radius: envelope.radius,
                aspect,
                verticalFovDegrees: 34
            });
            const clipPadding = envelope.radius * 1.08;
            camera.near = Math.max(.1, next.distance - clipPadding);
            camera.far = next.distance + clipPadding;
            camera.updateProjectionMatrix();
            return next;
        }
        let framing;
        function layoutForAspect(aspect) {
            framing = refitCamera(aspect);
            glyphs.setLayout({
                center: envelope.center,
                radius: glyphLayoutRadius,
                depthRadius: framing.radius,
                aspect: camera.aspect
            });
            camera.position.set(
                framing.target.x,
                framing.target.y,
                framing.target.z + framing.distance
            );
            camera.lookAt(
                framing.target.x,
                framing.target.y,
                framing.target.z
            );
        }
        layoutForAspect(1);

        const hemisphere = new THREE.HemisphereLight(
            0xfff9ea,
            0x71847e,
            1.7
        );
        hemisphere.name = "SkyHemisphereFill";
        const key = new THREE.DirectionalLight(0xfff1cc, 3.1);
        key.name = "DaylightKey";
        key.position.set(-5.5, 8, 6);
        key.castShadow = Boolean(profile.shadows);
        key.shadow.mapSize.set(
            profile.name === "high" ? 1024 : 512,
            profile.name === "high" ? 1024 : 512
        );
        key.shadow.camera.near = 1;
        key.shadow.camera.far = 20;
        key.shadow.camera.left = -5;
        key.shadow.camera.right = 5;
        key.shadow.camera.top = 5;
        key.shadow.camera.bottom = -5;
        const rim = new THREE.DirectionalLight(0xe8755d, .85);
        rim.name = "WarmRimLight";
        rim.position.set(5, 2, -6);
        scene.add(hemisphere, key, rim);

        let visible = true;
        let dirty = true;
        let disposed = false;
        const limits = profile.name === "high"
            ? HIGH_LIMITS
            : BALANCED_LIMITS;

        function resize() {
            if (disposed) {
                return;
            }
            const width = Math.max(1, canvas.clientWidth);
            const height = Math.max(1, canvas.clientHeight);
            camera.aspect = width / height;
            layoutForAspect(camera.aspect);
            renderer.setSize(width, height, false);
            dirty = true;
        }

        return {
            resize,
            setVisible(value) {
                const next = Boolean(value);
                if (next !== visible) {
                    visible = next;
                    dirty = true;
                }
            },
            render(pose = EMPTY_POSE) {
                if (disposed || !shouldRenderFrame({
                    visible,
                    hidden: false,
                    webgl: profile.webgl,
                    reducedMotion: pose.reduceMotion,
                    dirty,
                    profile,
                    ambient: !pose.reduceMotion
                })) {
                    return false;
                }

                world.update({
                    worldAngle: pose.worldAngle,
                    elapsedMs: pose.elapsedMs,
                    reduceMotion: pose.reduceMotion
                });
                glyphs.update({
                    pointer: profile.pointerEnabled
                        ? pose.pointer
                        : EMPTY_POSE.pointer,
                    elapsedMs: pose.elapsedMs,
                    titleProgress: pose.titleProgress,
                    reduceMotion: pose.reduceMotion
                });
                renderer.render(scene, camera);
                dirty = false;
                return true;
            },
            getDiagnostics() {
                const worldStats = world.getRenderStats();
                const glyphStats = glyphs.getRenderStats();
                const drawCalls = worldStats.drawCalls
                    + worldStats.shadowCalls
                    + glyphStats.drawCalls
                    + glyphStats.shadowCalls;
                const triangles = worldStats.triangles
                    + glyphStats.triangles;
                return {
                    drawCalls,
                    triangles,
                    limits: { ...limits },
                    withinBudget: drawCalls <= limits.drawCalls
                        && triangles <= limits.triangles
                };
            },
            dispose() {
                if (disposed) {
                    return;
                }
                disposed = true;
                disposeResources();
            }
        };
    } catch (error) {
        try {
            disposeResources();
        } catch {
            // Preserve the construction failure after all cleanup attempts.
        }
        throw error;
    }
}
