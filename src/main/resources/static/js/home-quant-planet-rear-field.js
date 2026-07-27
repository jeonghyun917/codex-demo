import {
    createRearFieldLayout,
    sampleRearFieldTransform
} from "./home-quant-planet-rear-field-layout.js";

function createCloudCardGeometry(THREE) {
    const geometry = new THREE.BufferGeometry();
    geometry.setAttribute(
        "position",
        new THREE.Float32BufferAttribute([
            -1, -1, 0,
            1, -1, 0,
            1, 1, 0,
            -1, 1, 0
        ], 3)
    );
    geometry.setAttribute(
        "uv",
        new THREE.Float32BufferAttribute([
            0, 0,
            1, 0,
            1, 1,
            0, 1
        ], 2)
    );
    geometry.setIndex([0, 1, 2, 0, 2, 3]);
    geometry.computeVertexNormals();
    return geometry;
}

function createCloudAlphaTexture(THREE, variant = 0) {
    const size = 64;
    const data = new Uint8Array(size * size * 4);
    for (let y = 0; y < size; y += 1) {
        for (let x = 0; x < size; x += 1) {
            const unitX = x / (size - 1) * 2 - 1;
            const unitY = y / (size - 1) * 2 - 1;
            const angle = Math.atan2(unitY, unitX);
            const phase = variant * 1.41;
            const boundary = .76
                + Math.sin(angle * (3 + variant) + phase) * .12
                + Math.cos(angle * (5 + variant) - phase) * .07;
            const radius = Math.hypot(
                unitX,
                unitY / (.74 + variant * .05)
            );
            const alpha = Math.round(
                Math.min(1, Math.max(0, (boundary - radius) * 16)) * 255
            );
            const offset = (y * size + x) * 4;
            data[offset] = 255;
            data[offset + 1] = 255;
            data[offset + 2] = 255;
            data[offset + 3] = alpha;
        }
    }
    const texture = new THREE.DataTexture(
        data,
        size,
        size,
        THREE.RGBAFormat
    );
    texture.minFilter = THREE.LinearFilter;
    texture.magFilter = THREE.LinearFilter;
    texture.needsUpdate = true;
    return texture;
}

const FAMILY_DEFINITIONS = Object.freeze([
    Object.freeze({
        name: "RearBroadHaze",
        layoutFamilies: Object.freeze(["haze"]),
        color: 0x72c9c5,
        textureVariant: 0,
        geometry: createCloudCardGeometry,
        material: (THREE, color, map) => new THREE.MeshBasicMaterial({
            color,
            map,
            transparent: true,
            opacity: .08,
            depthTest: true,
            depthWrite: false,
            vertexColors: true,
            side: THREE.DoubleSide,
            toneMapped: false
        }),
        scale: Object.freeze([3.6, 2.8, .02])
    }),
    Object.freeze({
        name: "RearAtmosphericClouds",
        layoutFamilies: Object.freeze(["cloud"]),
        color: 0xb8e7e2,
        textureVariant: 1,
        geometry: createCloudCardGeometry,
        material: (THREE, color, map) => new THREE.MeshBasicMaterial({
            color,
            map,
            transparent: true,
            opacity: .12,
            depthTest: true,
            depthWrite: false,
            vertexColors: true,
            side: THREE.DoubleSide,
            toneMapped: false
        }),
        scale: Object.freeze([2.2, 1.7, .02])
    }),
    Object.freeze({
        name: "RearMistIslands",
        layoutFamilies: Object.freeze(["mist"]),
        color: 0x4eb5b2,
        textureVariant: 2,
        geometry: createCloudCardGeometry,
        material: (THREE, color, map) => new THREE.MeshBasicMaterial({
            color,
            map,
            transparent: true,
            opacity: .08,
            depthTest: true,
            depthWrite: false,
            vertexColors: true,
            side: THREE.DoubleSide,
            toneMapped: false
        }),
        scale: Object.freeze([1.1, 1, .02])
    })
]);

function triangleCount(geometry) {
    const available = geometry.index
        ? geometry.index.count
        : geometry.attributes.position.count;
    const requested = geometry.drawRange?.count;
    const drawn = Number.isFinite(requested)
        ? Math.min(available, Math.max(0, requested))
        : available;
    return Math.floor(drawn / 3);
}

function copyTransform(placement, transform) {
    return Object.freeze({
        id: placement.id,
        position: Object.freeze([...transform.position]),
        rotation: Object.freeze([...transform.rotation]),
        opacity: transform.opacity,
        behindSafePlane: transform.position[2] <= placement.rearDepth
    });
}

function enableInstanceOpacity(material) {
    material.transparent = true;
    material.depthWrite = false;
    material.onBeforeCompile = (shader) => {
        shader.vertexShader = shader.vertexShader
            .replace(
                "#include <common>",
                [
                    "#include <common>",
                    "attribute float instanceOpacity;",
                    "varying float vInstanceOpacity;"
                ].join("\n")
            )
            .replace(
                "#include <begin_vertex>",
                [
                    "#include <begin_vertex>",
                    "vInstanceOpacity = instanceOpacity;"
                ].join("\n")
            );
        shader.fragmentShader = shader.fragmentShader
            .replace(
                "#include <common>",
                [
                    "#include <common>",
                    "varying float vInstanceOpacity;"
                ].join("\n")
            )
            .replace(
                "#include <opaque_fragment>",
                [
                    "diffuseColor.a *= vInstanceOpacity;",
                    "#include <opaque_fragment>"
                ].join("\n")
            );
    };
    material.customProgramCacheKey = () =>
        "quant-planet-rear-instance-opacity-v1";
    return material;
}

export function createQuantPlanetRearField(THREE, { profile = {} } = {}) {
    const group = new THREE.Group();
    group.name = "QuantPlanetRearField";
    let placements = createRearFieldLayout(profile);
    const maximumPlacements = createRearFieldLayout({ name: "high" });
    const geometries = new Set();
    const materials = new Set();
    const textures = new Set();
    const meshes = new Set();
    const families = [];
    const matrix = new THREE.Matrix4();
    const position = new THREE.Vector3();
    const rotation = new THREE.Euler();
    const scale = new THREE.Vector3();
    const familyScale = new THREE.Vector3();
    const quaternion = new THREE.Quaternion();
    const color = new THREE.Color();
    let disposed = false;
    let debugState = Object.freeze([]);
    let lastPose = {};

    function ownGeometry(geometry) {
        geometries.add(geometry);
        return geometry;
    }

    function ownMaterial(material) {
        materials.add(material);
        return material;
    }

    function ownTexture(texture) {
        textures.add(texture);
        return texture;
    }

    function ownMesh(mesh) {
        meshes.add(mesh);
        return mesh;
    }

    function disposeTrackedResources() {
        if (disposed) {
            return;
        }
        disposed = true;
        meshes.forEach((mesh) => {
            try {
                mesh.dispose();
            } catch {
                // Continue freeing geometry and material resources.
            }
        });
        meshes.clear();
        for (const resources of [geometries, materials, textures]) {
            resources.forEach((resource) => {
                try {
                    resource.dispose();
                } catch {
                    // Continue freeing unrelated GPU resources during rollback.
                }
            });
            resources.clear();
        }
        group.clear();
    }

    try {
        for (const definition of FAMILY_DEFINITIONS) {
            const authoredPlacements = placements.filter((placement) =>
                definition.layoutFamilies.includes(placement.family)
            );
            const maximumFamilyPlacements = maximumPlacements.filter(
                (placement) =>
                    definition.layoutFamilies.includes(placement.family)
            );
            const geometry = ownGeometry(definition.geometry(THREE));
            const texture = ownTexture(createCloudAlphaTexture(
                THREE,
                definition.textureVariant
            ));
            const opacityAttribute = new THREE.InstancedBufferAttribute(
                new Float32Array(maximumFamilyPlacements.length).fill(1),
                1
            );
            geometry.setAttribute("instanceOpacity", opacityAttribute);
            const mesh = ownMesh(new THREE.InstancedMesh(
                geometry,
                ownMaterial(enableInstanceOpacity(
                    definition.material(THREE, definition.color, texture)
                )),
                maximumFamilyPlacements.length
            ));
            mesh.count = authoredPlacements.length;
            mesh.name = definition.name;
            mesh.castShadow = false;
            mesh.receiveShadow = false;
            mesh.frustumCulled = false;
            group.add(mesh);
            families.push({
                placements: authoredPlacements,
                mesh,
                definition,
                opacityAttribute
            });
        }

        function update(pose = {}) {
            if (disposed) {
                return;
            }
            lastPose = { ...pose };
            const nextDebugState = [];
            for (const family of families) {
                family.placements.forEach((placement, index) => {
                    const transform = sampleRearFieldTransform(placement, pose);
                    position.fromArray(transform.position);
                    rotation.fromArray(transform.rotation);
                    scale.fromArray(placement.scale);
                    if (family.definition.scale) {
                        familyScale.fromArray(family.definition.scale);
                        scale.multiply(familyScale);
                    }
                    quaternion.setFromEuler(rotation);
                    matrix.compose(position, quaternion, scale);
                    family.mesh.setMatrixAt(index, matrix);
                    color.setHex(definitionColor(placement, family.definition));
                    color.multiplyScalar(.98);
                    family.mesh.setColorAt(index, color);
                    family.opacityAttribute.setX(index, transform.opacity);
                    nextDebugState.push(copyTransform(placement, transform));
                });
                family.mesh.instanceMatrix.needsUpdate = true;
                family.opacityAttribute.needsUpdate = true;
                if (family.mesh.instanceColor) {
                    family.mesh.instanceColor.needsUpdate = true;
                }
            }
            debugState = Object.freeze(nextDebugState);
        }

        function setProfile(nextProfile = {}) {
            if (disposed) {
                return;
            }
            placements = createRearFieldLayout(nextProfile);
            for (const family of families) {
                family.placements = placements.filter((placement) =>
                    family.definition.layoutFamilies.includes(
                        placement.family
                    )
                );
                family.mesh.count = family.placements.length;
            }
            update(lastPose);
        }

        function getRenderStats() {
            let ancestor = group;
            while (ancestor) {
                if (ancestor.visible === false) {
                    return { drawCalls: 0, shadowCalls: 0, triangles: 0 };
                }
                ancestor = ancestor.parent;
            }
            let drawCalls = 0;
            let triangles = 0;
            group.traverseVisible((object) => {
                if (!object.isInstancedMesh) {
                    return;
                }
                drawCalls += 1;
                triangles += triangleCount(object.geometry) * object.count;
            });
            return { drawCalls, shadowCalls: 0, triangles };
        }

        update();
        return {
            group,
            update,
            setProfile,
            getRenderStats,
            getDebugState: () => debugState,
            dispose: disposeTrackedResources
        };
    } catch (error) {
        disposeTrackedResources();
        throw error;
    }
}

function definitionColor(placement, definition) {
    const paletteShift = placement.colorIndex % 3;
    return definition.color + paletteShift * 0x070503;
}
