import {
    createRearFieldLayout,
    sampleRearFieldTransform
} from "./home-quant-planet-rear-field-layout.js";

function createIrregularWedgeGeometry(THREE) {
    const geometry = new THREE.BufferGeometry();
    geometry.setAttribute("position", new THREE.Float32BufferAttribute([
        -.45, -.28, -.16,
        .48, -.18, -.1,
        -.22, .36, -.06,
        -.32, -.2, .22,
        .37, -.14, .18,
        -.08, .27, .29
    ], 3));
    geometry.setIndex([
        0, 1, 2,
        3, 5, 4,
        0, 3, 4, 0, 4, 1,
        1, 4, 5, 1, 5, 2,
        2, 5, 3, 2, 3, 0
    ]);
    geometry.computeVertexNormals();
    return geometry;
}

function createMergedCloudGeometry(THREE) {
    const positions = [];
    const lobes = [
        { offset: [-.31, 0, 0], scale: [.7, .42, .62] },
        { offset: [.06, .05, .02], scale: [.82, .5, .68] },
        { offset: [.38, -.03, -.01], scale: [.55, .34, .52] }
    ];
    lobes.forEach(({ offset, scale }) => {
        const source = new THREE.DodecahedronGeometry(.58, 0);
        const nonIndexed = source.index ? source.toNonIndexed() : source;
        try {
            const attribute = nonIndexed.attributes.position;
            for (let index = 0; index < attribute.count; index += 1) {
                positions.push(
                    attribute.getX(index) * scale[0] + offset[0],
                    attribute.getY(index) * scale[1] + offset[1],
                    attribute.getZ(index) * scale[2] + offset[2]
                );
            }
        } finally {
            if (nonIndexed !== source) {
                nonIndexed.dispose();
            }
            source.dispose();
        }
    });
    const geometry = new THREE.BufferGeometry();
    geometry.setAttribute(
        "position",
        new THREE.Float32BufferAttribute(positions, 3)
    );
    geometry.computeVertexNormals();
    return geometry;
}

const FAMILY_DEFINITIONS = Object.freeze([
    Object.freeze({
        name: "RearRockChips",
        layoutFamilies: Object.freeze(["shard"]),
        color: 0xc96b56,
        geometry: createIrregularWedgeGeometry,
        material: (THREE, color) => new THREE.MeshToonMaterial({
            color,
            vertexColors: true,
            toneMapped: false
        })
    }),
    Object.freeze({
        name: "RearArchitecturalScraps",
        layoutFamilies: Object.freeze(["satellite"]),
        color: 0x47635d,
        geometry: (THREE) => {
            const shape = new THREE.Shape();
            shape.moveTo(-.38, -.12);
            shape.lineTo(.38, -.12);
            shape.lineTo(.38, .12);
            shape.lineTo(-.38, .12);
            shape.closePath();
            const geometry = new THREE.ExtrudeGeometry(shape, {
                depth: .16,
                bevelEnabled: true,
                bevelSegments: 1,
                bevelSize: .035,
                bevelThickness: .035,
                curveSegments: 1
            });
            geometry.center();
            return geometry;
        },
        material: (THREE, color) => new THREE.MeshToonMaterial({
            color,
            vertexColors: true,
            toneMapped: false
        })
    }),
    Object.freeze({
        name: "RearRingRemnants",
        layoutFamilies: Object.freeze(["beacon", "ring"]),
        color: 0xd9b45a,
        geometry: (THREE) => {
            const geometry = new THREE.TorusGeometry(.48, .055, 4, 12);
            geometry.setDrawRange(0, Math.floor(geometry.index.count * .7));
            return geometry;
        },
        material: (THREE, color) => new THREE.MeshToonMaterial({
            color,
            vertexColors: true,
            toneMapped: false
        })
    }),
    Object.freeze({
        name: "RearAtmosphericClouds",
        layoutFamilies: Object.freeze(["panel"]),
        color: 0x61bdb5,
        geometry: createMergedCloudGeometry,
        material: (THREE, color) => new THREE.MeshToonMaterial({
            color,
            transparent: true,
            opacity: .24,
            depthTest: true,
            depthWrite: false,
            vertexColors: true,
            toneMapped: false
        }),
        scale: Object.freeze([1.5, .32, .72])
    }),
    Object.freeze({
        name: "RearPaperFlecks",
        layoutFamilies: Object.freeze(["signal"]),
        color: 0xe8d4a7,
        geometry: (THREE) => new THREE.ConeGeometry(.22, .7, 3, 1),
        material: (THREE, color) => new THREE.MeshToonMaterial({
            color,
            vertexColors: true,
            toneMapped: false
        })
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
    const placements = createRearFieldLayout(profile);
    const geometries = new Set();
    const materials = new Set();
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

    function ownGeometry(geometry) {
        geometries.add(geometry);
        return geometry;
    }

    function ownMaterial(material) {
        materials.add(material);
        return material;
    }

    function disposeTrackedResources() {
        if (disposed) {
            return;
        }
        disposed = true;
        for (const resources of [geometries, materials]) {
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
            const geometry = ownGeometry(definition.geometry(THREE));
            const opacityAttribute = new THREE.InstancedBufferAttribute(
                new Float32Array(authoredPlacements.length).fill(1),
                1
            );
            geometry.setAttribute("instanceOpacity", opacityAttribute);
            const mesh = new THREE.InstancedMesh(
                geometry,
                ownMaterial(enableInstanceOpacity(
                    definition.material(THREE, definition.color)
                )),
                authoredPlacements.length
            );
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
                    color.multiplyScalar(.78);
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
