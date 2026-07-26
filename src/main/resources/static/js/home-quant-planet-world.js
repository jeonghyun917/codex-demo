const COLORS = Object.freeze({
    water: 0x78d7d0,
    waterDeep: 0x338f91,
    ground: 0xa8bf78,
    groundShade: 0x78965f,
    architecture: 0xf2e5c7,
    paper: 0xfffae9,
    coral: 0xd65f47,
    coralShade: 0xa94737,
    ink: 0x233a42,
    foliage: 0x39805b,
    foliageDark: 0x205f43,
    rock: 0x7c8580,
    gold: 0xe7b54f
});

function createGradientMap(THREE) {
    const texture = new THREE.DataTexture(
        new Uint8Array([52, 105, 158, 220]),
        4,
        1,
        THREE.RedFormat
    );
    texture.name = "ReferenceFourStepToonGradient";
    texture.minFilter = THREE.NearestFilter;
    texture.magFilter = THREE.NearestFilter;
    texture.generateMipmaps = false;
    texture.needsUpdate = true;
    return texture;
}

function createToonMaterial(THREE, color, gradientMap, options = {}) {
    return new THREE.MeshToonMaterial({ color, gradientMap, ...options });
}

function createWaterMaterial(THREE) {
    return new THREE.ShaderMaterial({
        uniforms: {
            time: { value: 0 },
            surfaceColor: { value: new THREE.Color(COLORS.water) },
            deepColor: { value: new THREE.Color(COLORS.waterDeep) }
        },
        vertexShader: `
            uniform float time;
            varying float vRipple;
            varying vec3 vNormal;

            void main() {
                float ripple = sin(position.x * 2.4 + time * 1.35)
                    * cos(position.z * 1.7 - time * .72);
                vRipple = ripple;
                vNormal = normalize(normalMatrix * normal);
                gl_Position = projectionMatrix
                    * modelViewMatrix
                    * vec4(position, 1.0);
            }
        `,
        fragmentShader: `
            uniform vec3 surfaceColor;
            uniform vec3 deepColor;
            varying float vRipple;
            varying vec3 vNormal;

            void main() {
                float light = .68 + max(vNormal.z, 0.0) * .24;
                float band = smoothstep(-.2, .7, vRipple);
                vec3 color = mix(deepColor, surfaceColor, .55 + band * .25);
                gl_FragColor = vec4(color * light, .84);
            }
        `,
        transparent: true,
        opacity: .84,
        depthWrite: false
    });
}

function addOutlinedMesh(THREE, parent, geometry, material, outlineMaterial, {
    position = [0, 0, 0],
    rotation = [0, 0, 0],
    scale = [1, 1, 1],
    outlined = true,
    name = ""
} = {}) {
    const mesh = new THREE.Mesh(geometry, material);
    mesh.name = name;
    mesh.position.set(...position);
    mesh.rotation.set(...rotation);
    mesh.scale.set(...scale);
    if (outlined && outlineMaterial) {
        const outline = new THREE.Mesh(geometry, outlineMaterial);
        outline.name = name ? `${name}Outline` : "ReferenceToonOutline";
        outline.scale.setScalar(1.035);
        mesh.add(outline);
    }
    parent.add(mesh);
    return mesh;
}

function orientInstance(THREE, matrix, position, scale, rotation = [0, 0, 0]) {
    const quaternion = new THREE.Quaternion().setFromEuler(
        new THREE.Euler(...rotation)
    );
    matrix.compose(
        new THREE.Vector3(...position),
        quaternion,
        new THREE.Vector3(...scale)
    );
}

function addCable(THREE, parent, resources, material, points, name) {
    const curve = new THREE.CatmullRomCurve3(
        points.map((point) => new THREE.Vector3(...point))
    );
    const geometry = resources.ownGeometry(
        new THREE.TubeGeometry(curve, 12, .026, 4, false)
    );
    const cable = new THREE.Mesh(geometry, material);
    cable.name = name;
    parent.add(cable);
    return cable;
}

function markInstancesDirty(mesh) {
    mesh.instanceMatrix.needsUpdate = true;
    if (mesh.instanceColor) {
        mesh.instanceColor.needsUpdate = true;
    }
}

function buildPlanetCore(THREE, resources, palette) {
    const group = new THREE.Group();
    group.name = "ReferencePlanetCore";

    const body = addOutlinedMesh(
        THREE,
        group,
        resources.geometry.detailedSphere,
        palette.ground,
        resources.heroOutline,
        {
            position: [0, -.05, 0],
            rotation: [.04, -.16, -.045],
            scale: [3.25, 3.75, 3],
            name: "ReferencePlanetBody"
        }
    );
    body.receiveShadow = Boolean(resources.shadows);

    addOutlinedMesh(
        THREE,
        group,
        resources.geometry.sphere,
        palette.groundShade,
        null,
        {
            position: [-2.05, -.55, 1.85],
            rotation: [.08, -.2, .24],
            scale: [1.18, 1.72, .88],
            outlined: false,
            name: "ReferenceWesternTerrainShelf"
        }
    );
    addOutlinedMesh(
        THREE,
        group,
        resources.geometry.sphere,
        palette.ground,
        null,
        {
            position: [2.08, -.38, 1.72],
            rotation: [-.08, .22, -.2],
            scale: [1.12, 1.82, .92],
            outlined: false,
            name: "ReferenceEasternTerrainShelf"
        }
    );
    addOutlinedMesh(
        THREE,
        group,
        resources.geometry.cone,
        palette.rock,
        null,
        {
            position: [-2.5, -2.35, 1.62],
            rotation: [0, 0, -.38],
            scale: [.64, 1.5, .72],
            outlined: false,
            name: "ReferenceWesternCliff"
        }
    );
    addOutlinedMesh(
        THREE,
        group,
        resources.geometry.cone,
        palette.rock,
        null,
        {
            position: [2.58, -2.18, 1.5],
            rotation: [0, 0, .32],
            scale: [.72, 1.68, .78],
            outlined: false,
            name: "ReferenceEasternCliff"
        }
    );

    const rockPositions = [
        [-2.78, 2.15, 1.22], [-2.2, 2.72, .98], [-1.45, 3.05, .86],
        [1.32, 3.08, .92], [2.2, 2.62, 1.02], [2.82, 1.84, 1.18],
        [-3.02, -.92, 1.06], [2.98, -.74, 1.12], [-2.74, -2.42, .92],
        [2.62, -2.6, .86], [-1.54, -3.25, .72], [1.4, -3.36, .76]
    ];
    const rocks = new THREE.InstancedMesh(
        resources.geometry.sphere,
        palette.rock,
        rockPositions.length
    );
    rocks.name = "ReferenceCoreRocks";
    const matrix = new THREE.Matrix4();
    rockPositions.forEach((position, index) => {
        const size = .23 + (index % 4) * .06;
        orientInstance(
            THREE,
            matrix,
            position,
            [size * 1.2, size, size * .85],
            [index * .31, index * .19, index * .23]
        );
        rocks.setMatrixAt(index, matrix);
    });
    markInstancesDirty(rocks);
    group.add(rocks);
    return group;
}

function buildWaterBand(THREE, resources, palette) {
    const group = new THREE.Group();
    group.name = "ReferenceWaterBand";

    const water = addOutlinedMesh(
        THREE,
        group,
        resources.geometry.sphere,
        palette.water,
        null,
        {
            position: [0, -.02, 1.12],
            rotation: [0, -.1, .015],
            scale: [3.18, .62, 2.45],
            outlined: false,
            name: "ReferenceWaterSurface"
        }
    );
    water.renderOrder = 2;

    const foam = new THREE.InstancedMesh(
        resources.geometry.box,
        palette.paper,
        7
    );
    foam.name = "ReferenceWaterFoamBars";
    const matrix = new THREE.Matrix4();
    const foamBars = [
        [-2.42, .26, 2.28, .42], [-1.55, -.26, 2.48, .28],
        [-.74, .24, 2.52, .34], [.22, -.24, 2.55, .26],
        [.98, .2, 2.48, .38], [1.82, -.2, 2.34, .24],
        [2.46, .18, 2.12, .3]
    ];
    foamBars.forEach(([x, y, z, width], index) => {
        orientInstance(
            THREE,
            matrix,
            [x, y, z],
            [width, .035, .055],
            [0, 0, index % 2 ? -.08 : .06]
        );
        foam.setMatrixAt(index, matrix);
    });
    markInstancesDirty(foam);
    group.add(foam);
    return { group, water };
}

function buildTemplePeak(THREE, resources, palette) {
    const group = new THREE.Group();
    group.name = "ReferenceTemplePeak";

    [
        [-1.35, 3.63, .78, [1.1, 2.55, 1.02], -.06],
        [.1, 3.72, .58, [1.38, 2.88, 1.2], .02],
        [1.48, 3.5, .7, [1.02, 2.35, .94], .08]
    ].forEach(([x, y, z, scale, rotation]) => {
        addOutlinedMesh(
            THREE,
            group,
            resources.geometry.cone,
            palette.paper,
            null,
            {
                position: [x, y, z],
                rotation: [0, 0, rotation],
                scale,
                outlined: false,
                name: "ReferencePaleMountain"
            }
        );
    });

    addOutlinedMesh(
        THREE,
        group,
        resources.geometry.box,
        palette.architecture,
        resources.outline,
        {
            position: [-.05, 4.38, 1.72],
            rotation: [0, -.08, 0],
            scale: [1.24, .56, .72],
            name: "ReferenceTempleHall"
        }
    );

    const columns = new THREE.InstancedMesh(
        resources.geometry.cylinder,
        palette.paper,
        5
    );
    columns.name = "ReferenceTempleColumns";
    const matrix = new THREE.Matrix4();
    for (let index = 0; index < columns.count; index += 1) {
        orientInstance(
            THREE,
            matrix,
            [-.47 + index * .23, 4.12, 2.12],
            [.045, .28, .045]
        );
        columns.setMatrixAt(index, matrix);
    }
    markInstancesDirty(columns);
    group.add(columns);

    const roofPieces = new THREE.InstancedMesh(
        resources.geometry.box,
        palette.ink,
        3
    );
    roofPieces.name = "ReferenceTempleRoofPieces";
    [
        [[-.05, 4.77, 1.72], [1.48, .17, .93], [0, -.08, 0]],
        [[-1.19, 4.83, 1.72], [.72, .12, .86], [0, -.08, .13]],
        [[1.08, 4.81, 1.72], [.72, .12, .86], [0, -.08, -.13]]
    ].forEach(([position, scale, rotation], index) => {
        orientInstance(THREE, matrix, position, scale, rotation);
        roofPieces.setMatrixAt(index, matrix);
    });
    markInstancesDirty(roofPieces);
    group.add(roofPieces);

    addOutlinedMesh(
        THREE,
        group,
        resources.geometry.cylinder,
        palette.gold,
        null,
        {
            position: [-.05, 5.03, 1.72],
            scale: [.045, .24, .045],
            outlined: false,
            name: "ReferenceTempleFinialStem"
        }
    );
    addOutlinedMesh(
        THREE,
        group,
        resources.geometry.cone,
        palette.gold,
        null,
        {
            position: [-.05, 5.28, 1.72],
            scale: [.2, .28, .2],
            outlined: false,
            name: "ReferenceTempleFinial"
        }
    );
    return group;
}

function buildIndustrialCluster(THREE, resources, palette) {
    const group = new THREE.Group();
    group.name = "ReferenceIndustrialCluster";

    const mainEngine = addOutlinedMesh(
        THREE,
        group,
        resources.geometry.cylinder,
        palette.coral,
        resources.outline,
        {
            position: [-3.72, .62, 1.18],
            rotation: [0, 0, Math.PI / 2],
            scale: [.68, 1.34, .68],
            name: "ReferenceMainEngine"
        }
    );
    mainEngine.castShadow = Boolean(resources.shadows);
    addOutlinedMesh(
        THREE,
        group,
        resources.geometry.cylinder,
        palette.coralShade,
        resources.outline,
        {
            position: [-3.42, -.78, 1.22],
            rotation: [0, 0, Math.PI / 2],
            scale: [.58, 1.04, .58],
            name: "ReferenceLowerEngine"
        }
    );
    addOutlinedMesh(
        THREE,
        group,
        resources.geometry.cylinder,
        palette.coral,
        null,
        {
            position: [-4.15, 1.82, .94],
            rotation: [0, 0, Math.PI / 2],
            scale: [.31, .78, .31],
            outlined: false,
            name: "ReferenceUpperPipe"
        }
    );
    addOutlinedMesh(
        THREE,
        group,
        resources.geometry.torus,
        palette.coralShade,
        null,
        {
            position: [-4.02, -1.62, 1.05],
            rotation: [Math.PI / 2, .1, 0],
            scale: [.58, .58, .58],
            outlined: false,
            name: "ReferencePipeElbow"
        }
    );
    addOutlinedMesh(
        THREE,
        group,
        resources.geometry.box,
        palette.rock,
        null,
        {
            position: [-3.2, .02, .88],
            rotation: [0, -.18, -.12],
            scale: [.65, 2.35, .62],
            outlined: false,
            name: "ReferenceEngineMount"
        }
    );

    const braces = new THREE.InstancedMesh(
        resources.geometry.box,
        palette.ink,
        8
    );
    braces.name = "ReferenceIndustrialBraces";
    const matrix = new THREE.Matrix4();
    for (let index = 0; index < braces.count; index += 1) {
        const upper = index < 4;
        orientInstance(
            THREE,
            matrix,
            [-4.72 + (index % 4) * .58, upper ? .22 : -.78, 1.84],
            [.045, .42, .045],
            [0, 0, upper ? -.24 : .24]
        );
        braces.setMatrixAt(index, matrix);
    }
    markInstancesDirty(braces);
    group.add(braces);

    addCable(
        THREE,
        group,
        resources,
        palette.ink,
        [
            [-4.9, 1.18, 1.12],
            [-4.42, .44, 2],
            [-3.72, -.42, 1.92],
            [-3.08, -1.38, 1.24]
        ],
        "ReferenceIndustrialCableA"
    );
    addCable(
        THREE,
        group,
        resources,
        palette.ink,
        [
            [-4.72, -.12, .74],
            [-4.35, -1.02, .62],
            [-3.72, -1.88, .84]
        ],
        "ReferenceIndustrialCableB"
    );
    return group;
}

function buildUtilityCluster(THREE, resources, palette) {
    const group = new THREE.Group();
    group.name = "ReferenceUtilityCluster";

    addOutlinedMesh(
        THREE,
        group,
        resources.geometry.box,
        palette.architecture,
        resources.outline,
        {
            position: [3.02, -.9, 1.34],
            rotation: [0, .12, -.04],
            scale: [1.06, 1.12, .82],
            name: "ReferenceUtilityHouse"
        }
    );
    addOutlinedMesh(
        THREE,
        group,
        resources.geometry.cone,
        palette.coralShade,
        null,
        {
            position: [3.02, -.22, 1.34],
            rotation: [0, 0, .08],
            scale: [.82, .46, .72],
            outlined: false,
            name: "ReferenceUtilityRoof"
        }
    );
    addOutlinedMesh(
        THREE,
        group,
        resources.geometry.box,
        palette.architecture,
        null,
        {
            position: [4.08, -1.72, .72],
            rotation: [0, -.2, -.08],
            scale: [.84, .74, .72],
            outlined: false,
            name: "ReferenceOuterUtilityHut"
        }
    );
    addOutlinedMesh(
        THREE,
        group,
        resources.geometry.cylinder,
        palette.ink,
        null,
        {
            position: [3.7, 1.08, 1.22],
            scale: [.055, 1.62, .055],
            outlined: false,
            name: "ReferenceUtilityMast"
        }
    );
    addOutlinedMesh(
        THREE,
        group,
        resources.geometry.box,
        palette.ink,
        null,
        {
            position: [3.7, 1.78, 1.22],
            rotation: [0, 0, -.05],
            scale: [.92, .055, .055],
            outlined: false,
            name: "ReferenceUtilityCrossbar"
        }
    );
    addOutlinedMesh(
        THREE,
        group,
        resources.geometry.dish,
        palette.paper,
        null,
        {
            position: [3.18, .92, 1.48],
            rotation: [Math.PI / 2, 0, -.48],
            scale: [.58, .58, .58],
            outlined: false,
            name: "ReferenceUtilityDish"
        }
    );

    const utilityRotor = new THREE.Group();
    utilityRotor.name = "ReferenceUtilityRotor";
    utilityRotor.position.set(3.72, 2.35, 1.28);
    const rotorBlades = new THREE.InstancedMesh(
        resources.geometry.box,
        palette.paper,
        4
    );
    rotorBlades.name = "ReferenceUtilityRotorBlades";
    const matrix = new THREE.Matrix4();
    for (let index = 0; index < rotorBlades.count; index += 1) {
        const angle = index * Math.PI / 2;
        orientInstance(
            THREE,
            matrix,
            [Math.cos(angle) * .25, Math.sin(angle) * .25, 0],
            [.42, .07, .045],
            [0, 0, angle]
        );
        rotorBlades.setMatrixAt(index, matrix);
    }
    markInstancesDirty(rotorBlades);
    utilityRotor.add(rotorBlades);
    addOutlinedMesh(
        THREE,
        utilityRotor,
        resources.geometry.cylinder,
        palette.gold,
        null,
        {
            rotation: [Math.PI / 2, 0, 0],
            scale: [.1, .09, .1],
            outlined: false,
            name: "ReferenceUtilityRotorHub"
        }
    );
    group.add(utilityRotor);

    addCable(
        THREE,
        group,
        resources,
        palette.ink,
        [
            [3.7, 1.74, 1.22],
            [4.18, 1.18, 1.58],
            [4.48, .38, 1.18]
        ],
        "ReferenceUtilityCableA"
    );
    addCable(
        THREE,
        group,
        resources,
        palette.ink,
        [
            [3.68, 1.68, 1.2],
            [3.22, .7, 1.86],
            [2.84, -.4, 1.52]
        ],
        "ReferenceUtilityCableB"
    );
    return { group, utilityRotor };
}

function buildLowerCanopy(THREE, resources, palette, count) {
    const group = new THREE.Group();
    group.name = "ReferenceLowerCanopy";
    const canopyCount = Math.max(0, Math.floor(count));
    const matrix = new THREE.Matrix4();

    const trunks = new THREE.InstancedMesh(
        resources.geometry.cylinder,
        palette.ink,
        canopyCount
    );
    trunks.name = "ReferenceCanopyTrunks";
    const crowns = new THREE.InstancedMesh(
        resources.geometry.sphere,
        palette.canopy,
        canopyCount
    );
    crowns.name = "ReferenceCanopyCrowns";
    const canopyColors = [
        new THREE.Color(COLORS.foliage),
        new THREE.Color(COLORS.foliageDark),
        new THREE.Color(COLORS.groundShade)
    ];

    for (let index = 0; index < canopyCount; index += 1) {
        const row = Math.floor(index / 9);
        const column = index % 9;
        const x = -3.02 + column * .75 + (row % 2) * .22;
        const y = -3.58 - row * .25 - (column % 3) * .1;
        const z = 1.1 + (row % 3) * .38 + (column % 2) * .22;
        orientInstance(
            THREE,
            matrix,
            [x, y + .12, z - .12],
            [.07, .64 + (index % 3) * .09, .07],
            [0, index * .23, (column - 4) * .025]
        );
        trunks.setMatrixAt(index, matrix);

        const size = .44 + (index % 4) * .055;
        orientInstance(
            THREE,
            matrix,
            [x, y - .24, z],
            [size * 1.08, size * 1.32, size],
            [index * .19, index * .31, index * .11]
        );
        crowns.setMatrixAt(index, matrix);
        crowns.setColorAt(index, canopyColors[index % canopyColors.length]);
    }
    markInstancesDirty(trunks);
    markInstancesDirty(crowns);
    trunks.castShadow = Boolean(resources.shadows);
    crowns.castShadow = Boolean(resources.shadows);
    group.add(trunks, crowns);
    return group;
}

function buildFloatingDebris(THREE, resources, palette, count) {
    const group = new THREE.Group();
    group.name = "ReferenceFloatingDebris";
    const debrisCount = Math.max(0, Math.floor(count));
    const matrix = new THREE.Matrix4();
    const placements = [
        [[-4.72, 3.42, .18], [.22, .13, .18], [.1, .4, .22]],
        [[4.56, 3.74, .3], [.18, .28, .16], [.4, .1, -.26]],
        [[-4.78, -3.72, .52], [.28, .18, .22], [.2, -.5, .3]],
        [[4.58, -4.42, -1.7], [.22, .34, .2], [-.25, .2, -.18]],
        [[1.28, -5.02, .24], [.3, .16, .18], [.42, .25, -.12]],
        [[-1.48, 4.96, -.35], [.16, .3, .14], [.18, -.3, .42]],
        [[4.36, .18, -1.86], [.18, .12, .34], [-.2, .48, .1]],
        [[-4.42, -.46, -1.78], [.24, .14, .28], [.32, -.12, .27]],
        [[3.82, 4.18, -1.22], [.14, .2, .18], [.18, .34, -.4]],
        [[-3.72, 4.22, -1.34], [.19, .13, .23], [-.22, .18, .36]],
        [[.2, -4.92, -1.58], [.25, .16, .2], [.3, -.25, .12]],
        [[2.68, -4.72, -1.12], [.18, .22, .15], [-.2, .38, -.34]]
    ];

    const pieces = new THREE.InstancedMesh(
        resources.geometry.box,
        palette.rock,
        debrisCount
    );
    pieces.name = "ReferenceDebrisPieces";
    const flecks = new THREE.InstancedMesh(
        resources.geometry.cone,
        palette.paper,
        debrisCount
    );
    flecks.name = "ReferenceDebrisFlecks";
    for (let index = 0; index < debrisCount; index += 1) {
        const [position, scale, rotation] = placements[index % placements.length];
        const cycle = Math.floor(index / placements.length);
        const offsetPosition = [
            position[0],
            position[1] + cycle * .16,
            position[2] - cycle * .24
        ];
        orientInstance(THREE, matrix, offsetPosition, scale, rotation);
        pieces.setMatrixAt(index, matrix);
        orientInstance(
            THREE,
            matrix,
            [offsetPosition[0] + .16, offsetPosition[1] + .14, offsetPosition[2]],
            [scale[0] * .42, scale[1] * .72, scale[2] * .42],
            [rotation[0], rotation[1], rotation[2] + .8]
        );
        flecks.setMatrixAt(index, matrix);
    }
    markInstancesDirty(pieces);
    markInstancesDirty(flecks);
    group.add(pieces, flecks);
    return group;
}

export function createQuantPlanetWorld(THREE, { profile = {} } = {}) {
    const geometries = new Set();
    const materials = new Set();
    const textures = new Set();
    const ownGeometry = (geometry) => (geometries.add(geometry), geometry);
    const ownMaterial = (material) => (materials.add(material), material);
    let disposed = false;

    function disposeTrackedResources() {
        if (disposed) {
            return;
        }
        disposed = true;
        [geometries, materials, textures].forEach((resources) => {
            resources.forEach((resource) => {
                try {
                    resource.dispose();
                } catch {
                    // Continue disposing independent resources during rollback.
                }
            });
            resources.clear();
        });
    }

    try {
        const gradientMap = createGradientMap(THREE);
        textures.add(gradientMap);
        const outline = profile.outlineLevel > 0
            ? ownMaterial(new THREE.MeshBasicMaterial({
                color: COLORS.ink,
                side: THREE.BackSide
            }))
            : null;
        const resources = {
            ownGeometry,
            shadows: Boolean(profile.shadows),
            outline,
            heroOutline: profile.outlineLevel > 1 ? outline : null,
            geometry: {
                box: ownGeometry(new THREE.BoxGeometry(1, 1, 1, 1, 1, 1)),
                cylinder: ownGeometry(
                    new THREE.CylinderGeometry(1, 1, 1, 8, 1)
                ),
                sphere: ownGeometry(new THREE.IcosahedronGeometry(1, 1)),
                detailedSphere: ownGeometry(
                    new THREE.IcosahedronGeometry(1, 2)
                ),
                cone: ownGeometry(new THREE.ConeGeometry(1, 1, 7)),
                torus: ownGeometry(new THREE.TorusGeometry(1, .18, 5, 12)),
                dish: ownGeometry(
                    new THREE.SphereGeometry(
                        .65,
                        12,
                        5,
                        0,
                        Math.PI * 2,
                        0,
                        .55
                    )
                )
            }
        };
        const palette = {
            water: ownMaterial(createWaterMaterial(THREE)),
            ground: ownMaterial(
                createToonMaterial(THREE, COLORS.ground, gradientMap)
            ),
            groundShade: ownMaterial(
                createToonMaterial(THREE, COLORS.groundShade, gradientMap)
            ),
            architecture: ownMaterial(
                createToonMaterial(THREE, COLORS.architecture, gradientMap)
            ),
            paper: ownMaterial(
                createToonMaterial(THREE, COLORS.paper, gradientMap)
            ),
            coral: ownMaterial(
                createToonMaterial(THREE, COLORS.coral, gradientMap)
            ),
            coralShade: ownMaterial(
                createToonMaterial(THREE, COLORS.coralShade, gradientMap)
            ),
            ink: ownMaterial(
                createToonMaterial(THREE, COLORS.ink, gradientMap)
            ),
            canopy: ownMaterial(
                createToonMaterial(
                    THREE,
                    COLORS.foliage,
                    gradientMap,
                    { vertexColors: true }
                )
            ),
            rock: ownMaterial(
                createToonMaterial(THREE, COLORS.rock, gradientMap)
            ),
            gold: ownMaterial(
                createToonMaterial(THREE, COLORS.gold, gradientMap)
            )
        };

        const planet = new THREE.Group();
        planet.name = "QuantPlanet";
        const core = buildPlanetCore(THREE, resources, palette);
        const waterBand = buildWaterBand(THREE, resources, palette);
        const temple = buildTemplePeak(THREE, resources, palette);
        const industrial = buildIndustrialCluster(THREE, resources, palette);
        const utility = buildUtilityCluster(THREE, resources, palette);
        const canopy = buildLowerCanopy(
            THREE,
            resources,
            palette,
            profile.foliageCount ?? 18
        );
        const debris = buildFloatingDebris(
            THREE,
            resources,
            palette,
            profile.cloudCount ?? 8
        );
        planet.add(
            core,
            waterBand.group,
            temple,
            industrial,
            utility.group,
            canopy,
            debris
        );

        const framingBox = new THREE.Box3();
        const framingPoint = new THREE.Vector3();
        const framingInstanceMatrix = new THREE.Matrix4();
        const framingWorldMatrix = new THREE.Matrix4();
        function forEachFramingPoint(visitor) {
            planet.updateMatrixWorld(true);
            planet.traverse((object) => {
                if (!object.isMesh || object.visible === false) {
                    return;
                }
                const positions = object.geometry.attributes.position;
                const includeGeometry = (matrix) => {
                    for (let index = 0; index < positions.count; index += 1) {
                        framingPoint.fromBufferAttribute(positions, index)
                            .applyMatrix4(matrix);
                        visitor(framingPoint);
                    }
                };
                if (object.isInstancedMesh) {
                    for (let index = 0; index < object.count; index += 1) {
                        object.getMatrixAt(index, framingInstanceMatrix);
                        framingWorldMatrix.multiplyMatrices(
                            object.matrixWorld,
                            framingInstanceMatrix
                        );
                        includeGeometry(framingWorldMatrix);
                    }
                    return;
                }
                includeGeometry(object.matrixWorld);
            });
        }

        forEachFramingPoint((point) => framingBox.expandByPoint(point));
        const framingCenter = framingBox.getCenter(new THREE.Vector3());
        let framingRadiusSquared = 0;
        forEachFramingPoint((point) => {
            framingRadiusSquared = Math.max(
                framingRadiusSquared,
                point.distanceToSquared(framingCenter)
            );
        });
        const framingRadius = Math.sqrt(framingRadiusSquared);

        function getFramingEnvelope() {
            return {
                center: {
                    x: framingCenter.x,
                    y: framingCenter.y,
                    z: framingCenter.z
                },
                radius: framingRadius,
                min: {
                    x: framingBox.min.x,
                    y: framingBox.min.y,
                    z: framingBox.min.z
                },
                max: {
                    x: framingBox.max.x,
                    y: framingBox.max.y,
                    z: framingBox.max.z
                },
                top: framingBox.max.y,
                bottom: framingBox.min.y
            };
        }

        function getRenderStats() {
            const stats = {
                drawCalls: 0,
                shadowCalls: 0,
                triangles: 0
            };
            planet.traverse((object) => {
                if (!object.isMesh || object.visible === false) {
                    return;
                }
                stats.drawCalls += 1;
                stats.shadowCalls += object.castShadow ? 1 : 0;
                const primitives = object.geometry.index
                    ? object.geometry.index.count
                    : object.geometry.attributes.position.count;
                const instances = object.isInstancedMesh ? object.count : 1;
                stats.triangles += primitives / 3 * instances;
            });
            return stats;
        }

        function update(pose = {}) {
            if (disposed) {
                return;
            }
            const worldAngle = Number.isFinite(pose.worldAngle)
                ? pose.worldAngle
                : 0;
            const dragAngle = Number.isFinite(pose.dragAngle)
                ? pose.dragAngle
                : 0;
            const elapsedMs = Number.isFinite(pose.elapsedMs)
                ? pose.elapsedMs
                : 0;
            planet.rotation.set(0, worldAngle + dragAngle, 0);
            waterBand.water.material.uniforms.time.value = pose.reduceMotion
                ? 0
                : elapsedMs / 1000;
            utility.utilityRotor.rotation.z = pose.reduceMotion
                ? 0
                : elapsedMs * .00035;
        }

        function dispose() {
            disposeTrackedResources();
        }

        return {
            group: planet,
            update,
            getFramingEnvelope,
            getRenderStats,
            dispose
        };
    } catch (error) {
        disposeTrackedResources();
        throw error;
    }
}
