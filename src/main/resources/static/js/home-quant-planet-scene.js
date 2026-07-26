import {
    createCourierLoopPose,
    createOrbitPose,
    shouldRenderFrame
} from "./home-quant-planet-motion.js";
import { fitPerspectiveSphere } from "./home-quant-planet-composition.js";
import { createQuantPlanetWorld } from "./home-quant-planet-world.js";

const HIGH_LIMITS = Object.freeze({ drawCalls: 75, triangles: 140000 });
const BALANCED_LIMITS = Object.freeze({ drawCalls: 65, triangles: 90000 });
const framingMargin = (aspect) => aspect < .75 ? 1 : 1.1;

function createStaticSceneController() {
    return {
        resize() {},
        setPointer() {},
        setDragAngle() {},
        setVisible() {},
        render() { return false; },
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

export async function createQuantPlanetScene({ canvas, profile, three } = {}) {
    if (!canvas || !profile?.webgl || profile.name === "static") {
        return createStaticSceneController();
    }

    let renderer = null;
    let world = null;

    function disposeInitializationResources() {
        try {
            world?.dispose();
        } catch {
            // Preserve the initialization error after attempting every cleanup.
        }
        try {
            renderer?.dispose();
        } catch {
            // Preserve the initialization error after attempting every cleanup.
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
        renderer.setPixelRatio(profile.pixelRatio);
        renderer.shadowMap.enabled = Boolean(profile.shadows);
        renderer.shadowMap.type = THREE.PCFSoftShadowMap;

        const scene = new THREE.Scene();
        scene.fog = new THREE.Fog(0xd9efe8, 11, 24);
        const camera = new THREE.PerspectiveCamera(34, 1, .1, 40);
        world = createQuantPlanetWorld(THREE, { profile });
        const worldScale = Math.max(.001, Number(profile.planetScale) || 1);
        world.group.scale.setScalar(worldScale);
        scene.add(world.group);
        const localEnvelope = world.getFramingEnvelope();
        const envelope = {
            center: {
                x: localEnvelope.center.x * worldScale,
                y: localEnvelope.center.y * worldScale,
                z: localEnvelope.center.z * worldScale
            },
            radius: localEnvelope.radius * worldScale,
            top: localEnvelope.top * worldScale,
            bottom: localEnvelope.bottom * worldScale
        };
        function refitCamera(aspect) {
            const next = fitPerspectiveSphere({
                center: envelope.center,
                radius: envelope.radius,
                aspect,
                verticalFovDegrees: 34,
                margin: framingMargin(aspect)
            });
            const clipPadding = next.radius * 1.08;
            camera.near = Math.max(.1, next.distance - clipPadding);
            camera.far = next.distance + clipPadding;
            camera.updateProjectionMatrix();
            return next;
        }
        let framing = refitCamera(1);
        const framingTarget = new THREE.Vector3();

        const hemisphere = new THREE.HemisphereLight(0xfff9ea, 0x71847e, 1.7);
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

        let pointer = { x: 0, y: 0 };
        let dragAngle = 0;
        let visible = true;
        let dirty = true;
        let disposed = false;
        const limits = profile.name === "high" ? HIGH_LIMITS : BALANCED_LIMITS;

        function markDirty() {
            if (!disposed) {
                dirty = true;
            }
        }

        function resize() {
            if (disposed) {
                return;
            }
            const width = Math.max(1, canvas.clientWidth);
            const height = Math.max(1, canvas.clientHeight);
            camera.aspect = width / height;
            framing = refitCamera(camera.aspect);
            renderer.setSize(width, height, false);
            markDirty();
        }

        return {
            resize,
            setDragAngle(value) {
                const next = Math.max(-Math.PI * 4, Math.min(
                    Math.PI * 4, Number(value) || 0
                ));
                if (next !== dragAngle) {
                    dragAngle = next;
                    markDirty();
                }
            },
            setPointer(value) {
                const next = profile.pointerEnabled
                    ? value || { x: 0, y: 0 }
                    : { x: 0, y: 0 };
                if (next.x !== pointer.x || next.y !== pointer.y) {
                    pointer = next;
                    markDirty();
                }
            },
            setVisible(value) {
                const next = Boolean(value);
                if (next !== visible) {
                    visible = next;
                    markDirty();
                }
            },
            render(time = 0) {
                if (disposed || !shouldRenderFrame({
                    visible,
                    hidden: false,
                    webgl: profile.webgl,
                    reducedMotion: profile.name === "static",
                    dirty,
                    profile
                })) {
                    return false;
                }
                const orbitPose = createOrbitPose({
                    time, dragAngle, pointer,
                    reduceMotion: profile.name === "static"
                });
                const courierPose = createCourierLoopPose(time, false);
                world.update({ orbitPose, courierPose, time });

                framingTarget.set(
                    framing.target.x,
                    framing.target.y,
                    framing.target.z
                ).applyQuaternion(world.group.quaternion);
                const cosPitch = Math.cos(orbitPose.cameraPitch);
                camera.position.set(
                    framingTarget.x + Math.sin(orbitPose.cameraYaw)
                        * cosPitch * framing.distance,
                    framingTarget.y + Math.sin(orbitPose.cameraPitch)
                        * framing.distance,
                    framingTarget.z + Math.cos(orbitPose.cameraYaw)
                        * cosPitch * framing.distance
                );
                camera.lookAt(framingTarget);
                renderer.render(scene, camera);
                dirty = false;
                return true;
            },
            getDiagnostics() {
                const drawCalls = renderer.info.render.calls;
                const triangles = renderer.info.render.triangles;
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
                try {
                    world.dispose();
                } finally {
                    renderer.dispose();
                }
            }
        };
    } catch (error) {
        disposeInitializationResources();
        throw error;
    }
}
