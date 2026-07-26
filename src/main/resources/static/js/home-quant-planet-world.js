const COLORS = Object.freeze({
    water: 0x9ed9d5,
    haze: 0xd9efe8,
    ground: 0xb8c989,
    groundShade: 0x8fab70,
    architecture: 0xf1d7a4,
    paper: 0xfff9ea,
    coral: 0xe8755d,
    blue: 0x2357a5,
    ink: 0x173149,
    positive: 0x2f8c73,
    risk: 0xc85a5a,
    rock: 0x71847e
});

function createGradientMap(THREE) {
    const texture = new THREE.DataTexture(
        new Uint8Array([55, 118, 187, 255]),
        4,
        1,
        THREE.RedFormat
    );
    texture.name = "FourStepToonGradient";
    texture.minFilter = THREE.NearestFilter;
    texture.magFilter = THREE.NearestFilter;
    texture.generateMipmaps = false;
    texture.needsUpdate = true;
    return texture;
}

function createToonMaterial(THREE, color, gradientMap, options = {}) {
    return new THREE.MeshToonMaterial({ color, gradientMap, ...options });
}

function addOutlinedMesh(THREE, parent, geometry, material, outlineMaterial, {
    position = [0, 0, 0], rotation = [0, 0, 0], scale = [1, 1, 1],
    outlined = true, name = ""
} = {}) {
    const mesh = new THREE.Mesh(geometry, material);
    mesh.name = name;
    mesh.position.set(...position);
    mesh.rotation.set(...rotation);
    mesh.scale.set(...scale);
    if (outlined && outlineMaterial) {
        const outline = new THREE.Mesh(geometry, outlineMaterial);
        outline.name = name ? `${name}Outline` : "ToonOutline";
        outline.material.side = THREE.BackSide;
        outline.scale.set(1.035, 1.035, 1.035);
        mesh.add(outline);
    }
    parent.add(mesh);
    return mesh;
}

function orientInstance(THREE, matrix, position, scale, rotation = [0, 0, 0]) {
    const quaternion = new THREE.Quaternion().setFromEuler(new THREE.Euler(...rotation));
    matrix.compose(
        new THREE.Vector3(...position),
        quaternion,
        new THREE.Vector3(...scale)
    );
}

function addCable(THREE, parent, geometrySet, material, points, name) {
    const curve = new THREE.CatmullRomCurve3(points.map((point) => new THREE.Vector3(...point)));
    const geometry = new THREE.TubeGeometry(curve, 12, .018, 4, false);
    geometrySet.add(geometry);
    const cable = new THREE.Mesh(geometry, material);
    cable.name = name;
    parent.add(cable);
    return cable;
}

function addInstrument(THREE, parent, geometry, materials, outline, {
    name, position, accent
}) {
    const instrument = new THREE.Group();
    instrument.name = name;
    instrument.position.set(...position);
    addOutlinedMesh(THREE, instrument, geometry.box, materials.architecture, outline, {
        scale: [.38, .42, .25], name: `${name}Housing`
    });
    addOutlinedMesh(THREE, instrument, geometry.box, accent, outline, {
        position: [0, .03, .26], scale: [.27, .18, .035],
        outlined: false, name: `${name}Display`
    });
    addOutlinedMesh(THREE, instrument, geometry.cylinder, materials.ink, outline, {
        position: [0, -.34, 0], scale: [.08, .15, .08],
        outlined: false, name: `${name}Stand`
    });
    parent.add(instrument);
    return instrument;
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

    const material = {
        water: ownMaterial(createToonMaterial(THREE, COLORS.water, gradientMap, {
            transparent: true, opacity: .48, depthWrite: false
        })),
        ground: ownMaterial(createToonMaterial(THREE, COLORS.ground, gradientMap)),
        groundShade: ownMaterial(createToonMaterial(THREE, COLORS.groundShade, gradientMap)),
        architecture: ownMaterial(createToonMaterial(THREE, COLORS.architecture, gradientMap)),
        paper: ownMaterial(createToonMaterial(THREE, COLORS.paper, gradientMap)),
        coral: ownMaterial(createToonMaterial(THREE, COLORS.coral, gradientMap, {
            emissive: COLORS.coral, emissiveIntensity: .04
        })),
        blue: ownMaterial(createToonMaterial(THREE, COLORS.blue, gradientMap)),
        ink: ownMaterial(createToonMaterial(THREE, COLORS.ink, gradientMap)),
        positive: ownMaterial(createToonMaterial(THREE, COLORS.positive, gradientMap)),
        risk: ownMaterial(createToonMaterial(THREE, COLORS.risk, gradientMap)),
        rock: ownMaterial(createToonMaterial(THREE, COLORS.rock, gradientMap)),
        cloud: ownMaterial(createToonMaterial(THREE, COLORS.paper, gradientMap)),
        routeLamp: ownMaterial(createToonMaterial(THREE, COLORS.paper, gradientMap))
    };
    const outline = profile.outlineLevel > 0
        ? ownMaterial(new THREE.MeshBasicMaterial({
            color: COLORS.ink, side: THREE.BackSide
        }))
        : null;
    const heroOutline = profile.outlineLevel > 1 ? outline : null;

    const geometry = {
        box: ownGeometry(new THREE.BoxGeometry(1, 1, 1, 1, 1, 1)),
        cylinder: ownGeometry(new THREE.CylinderGeometry(1, 1, 1, 8, 1)),
        sphere: ownGeometry(new THREE.IcosahedronGeometry(1, 1)),
        detailedSphere: ownGeometry(new THREE.IcosahedronGeometry(1, 2)),
        cone: ownGeometry(new THREE.ConeGeometry(1, 1, 7)),
        ring: ownGeometry(new THREE.TorusGeometry(1, .13, 5, 12)),
        dish: ownGeometry(new THREE.SphereGeometry(.65, 12, 5, 0, Math.PI * 2, 0, .55)),
        capsule: ownGeometry(new THREE.CapsuleGeometry(.18, .3, 3, 6))
    };

    const planet = new THREE.Group();
    planet.name = "QuantPlanet";
    const landmarks = new THREE.Group();
    landmarks.name = "Landmarks";
    const observe = new THREE.Group();
    observe.name = "ObserveStation";
    const resolve = new THREE.Group();
    resolve.name = "ResolveWorks";
    const act = new THREE.Group();
    act.name = "DecisionGate";
    const courier = new THREE.Group();
    courier.name = "QuantCourier";

    const routeCurve = new THREE.CatmullRomCurve3([
        new THREE.Vector3(-2.35, 2.62, .78),
        new THREE.Vector3(-1.02, 3.02, 1.48),
        new THREE.Vector3(.72, 2.9, 1.62),
        new THREE.Vector3(2.32, 2.5, .84)
    ], false, "catmullrom", .38);

    observe.position.set(-2.02, 2.42, .48);
    resolve.position.set(.06, 2.62, 1.24);
    act.position.set(2.14, 2.18, .62);
    landmarks.add(observe, resolve, act);
    planet.add(landmarks, courier);

    const land = addOutlinedMesh(THREE, planet, geometry.detailedSphere, material.ground, heroOutline, {
        scale: [2.82, 2.82, 2.82], rotation: [0, -.18, -.07], name: "FacetedLand"
    });
    land.receiveShadow = Boolean(profile.shadows);
    const water = addOutlinedMesh(THREE, planet, geometry.detailedSphere, material.water, null, {
        scale: [2.86, 2.86, 2.86], rotation: [0, -.18, -.07],
        outlined: false, name: "ShallowWaterShell"
    });
    water.renderOrder = -1;

    const routeGeometry = ownGeometry(new THREE.TubeGeometry(routeCurve, 42, .085, 7, false));
    const route = addOutlinedMesh(THREE, planet, routeGeometry, material.coral, null, {
        outlined: false, name: "ElevatedSignalRoute"
    });
    route.castShadow = Boolean(profile.shadows);

    addOutlinedMesh(THREE, observe, geometry.box, material.architecture, heroOutline, {
        scale: [.72, .45, .58], rotation: [0, -.16, -.05],
        name: "ObserveStationCabin"
    });
    addOutlinedMesh(THREE, observe, geometry.cone, material.blue, heroOutline, {
        position: [-.08, .47, 0], rotation: [0, .18, 0], scale: [.82, .35, .7],
        name: "ObserveAsymmetricRoof"
    });
    const observeDishPivot = new THREE.Group();
    observeDishPivot.name = "ObserveDishCalibrationPivot";
    observeDishPivot.position.set(.56, .68, .08);
    observe.add(observeDishPivot);
    const observeDish = addOutlinedMesh(
        THREE, observeDishPivot, geometry.dish, material.paper, null,
        { rotation: [Math.PI / 2, 0, -.25], name: "ObserveDirectionalDish" }
    );
    addOutlinedMesh(THREE, observeDishPivot, geometry.cylinder, material.ink, null, {
        position: [0, -.28, 0], scale: [.05, .35, .05],
        outlined: false, name: "ObserveDishMast"
    });
    for (let index = 0; index < 3; index += 1) {
        addOutlinedMesh(THREE, observe, geometry.cylinder, material.ink, null, {
            position: [-.54 + index * .18, .7 + index * .17, -.13],
            scale: [.035, .23 + index * .07, .035],
            outlined: false, name: `ObserveMastSegment${index + 1}`
        });
    }
    addOutlinedMesh(THREE, observe, geometry.box, material.paper, null, {
        position: [-.76, .03, .6], rotation: [0, -.08, 0],
        scale: [.38, .16, .035], name: "ObserveRouteSign01"
    });
    addOutlinedMesh(THREE, observe, geometry.box, material.blue, null, {
        position: [-.76, -.16, .6], scale: [.29, .07, .04],
        outlined: false, name: "MarketFieldOnlineDisplay"
    });
    addOutlinedMesh(THREE, observe, geometry.ring, material.coral, null, {
        position: [-.82, -.42, .28], rotation: [Math.PI / 2, 0, 0],
        scale: [.12, .12, .12], outlined: false, name: "CalibrationProp"
    });
    addCable(THREE, observe, geometries, material.ink, [
        [-.54, .86, -.13], [-.2, .62, .32], [.42, .46, .28]
    ], "ObserveSignalCable");

    addOutlinedMesh(THREE, resolve, geometry.box, material.architecture, heroOutline, {
        scale: [.92, .47, .65], rotation: [.03, .13, -.04], name: "ResolveWorksShop"
    });
    addOutlinedMesh(THREE, resolve, geometry.box, material.blue, null, {
        position: [-.18, .53, -.04], rotation: [0, .08, -.08],
        scale: [.8, .15, .7], name: "ResolveOffsetRoof"
    });
    addOutlinedMesh(THREE, resolve, geometry.cylinder, material.coral, null, {
        position: [.72, .32, -.38], rotation: [0, 0, Math.PI / 2],
        scale: [.14, .5, .14], name: "ResolveExternalPipe"
    });
    addOutlinedMesh(THREE, resolve, geometry.cylinder, material.ink, null, {
        position: [.45, .76, -.25], scale: [.11, .28, .11],
        outlined: false, name: "ResolveRoofVent"
    });
    addInstrument(THREE, resolve, geometry, material, outline, {
        name: "SignalInstrument79", position: [-.66, .04, .7], accent: material.blue
    });
    addInstrument(THREE, resolve, geometry, material, outline, {
        name: "AlphaInstrument20D", position: [0, .08, .82], accent: material.positive
    });
    addInstrument(THREE, resolve, geometry, material, outline, {
        name: "RiskInstrumentMedium", position: [.68, .02, .64], accent: material.risk
    });
    const resolveSorter = addOutlinedMesh(THREE, resolve, geometry.box, material.coral, null, {
        position: [0, -.42, .78], scale: [.42, .08, .16], name: "ResolveSorter"
    });
    const resolveSorterBaseY = resolveSorter.position.y;
    for (let index = 0; index < 3; index += 1) {
        addOutlinedMesh(THREE, resolveSorter, geometry.box, material.paper, null, {
            position: [-.24 + index * .24, .14, 0],
            scale: [.08, .08 + index * .025, .08],
            outlined: false, name: `ResolveSortedPiece${index + 1}`
        });
    }
    for (let index = 0; index < 3; index += 1) {
        addOutlinedMesh(THREE, resolve, geometry.box, material.architecture, null, {
            position: [-.74 + index * .23, -.49 - index * .08, .1],
            scale: [.24, .07, .4], outlined: false, name: `ResolveStair${index + 1}`
        });
    }

    const gateLeft = addOutlinedMesh(THREE, act, geometry.box, material.architecture, heroOutline, {
        position: [-.48, .2, 0], scale: [.23, .86, .28], name: "DecisionGateLeft"
    });
    const gateRight = addOutlinedMesh(THREE, act, geometry.box, material.architecture, heroOutline, {
        position: [.48, .2, 0], scale: [.23, .86, .28], name: "DecisionGateRight"
    });
    gateLeft.castShadow = gateRight.castShadow = Boolean(profile.shadows);
    addOutlinedMesh(THREE, act, geometry.box, material.blue, null, {
        position: [0, .98, 0], scale: [.75, .18, .32], name: "DecisionGateHeader"
    });
    const productPortal = addOutlinedMesh(THREE, act, geometry.ring, material.coral, null, {
        position: [0, .24, .04], rotation: [Math.PI / 2, 0, 0],
        scale: [.5, .65, .5], name: "QuantProductPortal"
    });
    const productPortalBaseScale = productPortal.scale.clone();
    addCable(THREE, act, geometries, material.ink, [
        [-.48, .85, -.22], [0, 1.15, -.28], [.48, .85, -.22]
    ], "DecisionGatePowerCable");

    const foliageCount = Math.max(0, Math.floor(profile.foliageCount ?? 18));
    const foliageTrunks = new THREE.InstancedMesh(geometry.cylinder, material.ink, foliageCount);
    const foliageCanopies = new THREE.InstancedMesh(geometry.sphere, material.groundShade, foliageCount);
    foliageTrunks.name = "InstancedFoliageTrunks";
    foliageCanopies.name = "InstancedFoliageCanopies";
    const instanceMatrix = new THREE.Matrix4();
    for (let index = 0; index < foliageCount; index += 1) {
        const angle = -.48 + index * 2.31;
        const radius = 2.55 + (index % 4) * .1;
        const position = [
            Math.sin(angle) * radius,
            1.2 + (index % 3) * .13,
            Math.cos(angle) * radius
        ];
        orientInstance(THREE, instanceMatrix, position, [.045, .24, .045], [0, angle, 0]);
        foliageTrunks.setMatrixAt(index, instanceMatrix);
        orientInstance(THREE, instanceMatrix,
            [position[0], position[1] + .32, position[2]],
            [.19 + (index % 3) * .025, .17, .19],
            [index * .37, angle, 0]);
        foliageCanopies.setMatrixAt(index, instanceMatrix);
    }
    foliageTrunks.instanceMatrix.needsUpdate = true;
    foliageCanopies.instanceMatrix.needsUpdate = true;
    planet.add(foliageTrunks, foliageCanopies);

    const perimeterUtilityModules = new THREE.InstancedMesh(
        geometry.box, material.rock, 8
    );
    perimeterUtilityModules.name = "PerimeterUtilityModules";
    for (let index = 0; index < perimeterUtilityModules.count; index += 1) {
        const angle = .4 + index * Math.PI * 2 / perimeterUtilityModules.count;
        const radius = 3.08 + (index % 3) * .2;
        orientInstance(THREE, instanceMatrix, [
            Math.sin(angle) * radius,
            -.15 + Math.sin(angle * 2) * .32,
            Math.cos(angle) * radius
        ], [.17, .11 + (index % 2) * .05, .13], [angle * .3, angle, angle * .12]);
        perimeterUtilityModules.setMatrixAt(index, instanceMatrix);
    }
    perimeterUtilityModules.instanceMatrix.needsUpdate = true;
    planet.add(perimeterUtilityModules);

    const atmosphericSpecks = new THREE.InstancedMesh(
        geometry.sphere, material.paper, 10
    );
    atmosphericSpecks.name = "AtmosphericSpecks";
    for (let index = 0; index < atmosphericSpecks.count; index += 1) {
        const angle = .72 + index * 2.17;
        const radius = 3.42 + (index % 3) * .14;
        const elevation = -.34 + (index % 5) * .17;
        const horizontalRadius = Math.cos(elevation) * radius;
        orientInstance(THREE, instanceMatrix, [
            Math.sin(angle) * horizontalRadius,
            Math.sin(elevation) * radius,
            Math.cos(angle) * horizontalRadius
        ], [.035 + (index % 3) * .012, .035, .035], [angle, 0, 0]);
        atmosphericSpecks.setMatrixAt(index, instanceMatrix);
    }
    atmosphericSpecks.instanceMatrix.needsUpdate = true;
    const routeLights = new THREE.InstancedMesh(geometry.sphere, material.routeLamp, 10);
    routeLights.name = "InstancedRouteLamps";
    const lampDim = new THREE.Color(0x425866);
    const lampBright = new THREE.Color(COLORS.coral);
    const lampColor = new THREE.Color();
    for (let index = 0; index < 10; index += 1) {
        const point = routeCurve.getPointAt(index / 9);
        orientInstance(THREE, instanceMatrix,
            [point.x, point.y + .15, point.z], [.07, .07, .07]);
        routeLights.setMatrixAt(index, instanceMatrix);
        routeLights.setColorAt(index, lampDim);
    }
    routeLights.instanceMatrix.needsUpdate = true;
    planet.add(atmosphericSpecks, routeLights);

    const cloudCount = Math.max(0, Math.floor(profile.cloudCount ?? 5));
    const clouds = new THREE.InstancedMesh(geometry.sphere, material.cloud, cloudCount);
    clouds.name = "InstancedAtmosphericClouds";
    for (let index = 0; index < cloudCount; index += 1) {
        const angle = index * 2.17;
        const radius = 3.25 + (index % 3) * .12;
        const elevation = .25 + (index % 3) * .18;
        const horizontalRadius = Math.cos(elevation) * radius;
        orientInstance(THREE, instanceMatrix, [
            Math.sin(angle) * horizontalRadius,
            Math.sin(elevation) * radius,
            Math.cos(angle) * horizontalRadius
        ], [.38 + (index % 2) * .18, .13, .24], [0, angle, .08]);
        clouds.setMatrixAt(index, instanceMatrix);
    }
    clouds.instanceMatrix.needsUpdate = true;
    planet.add(clouds);

    const contourSignalFragments = new THREE.InstancedMesh(
        geometry.cone, material.groundShade, 12
    );
    contourSignalFragments.name = "ContourSignalFragments";
    for (let index = 0; index < contourSignalFragments.count; index += 1) {
        const angle = .18 + index * Math.PI * 2 / contourSignalFragments.count;
        const radius = 3.05 + (index % 4) * .16;
        orientInstance(THREE, instanceMatrix, [
            Math.cos(angle) * radius,
            Math.sin(angle) * radius,
            .3 + (index % 3) * .16
        ], [.08 + (index % 3) * .025, .2, .08], [0, 0, angle - Math.PI / 2]);
        contourSignalFragments.setMatrixAt(index, instanceMatrix);
    }
    contourSignalFragments.instanceMatrix.needsUpdate = true;
    planet.add(contourSignalFragments);

    const courierBody = addOutlinedMesh(THREE, courier, geometry.box, material.coral, outline, {
        scale: [.23, .3, .18], name: "QuantCourierBody"
    });
    addOutlinedMesh(THREE, courierBody, geometry.box, material.blue, null, {
        position: [0, .02, .19], scale: [.15, .11, .025],
        outlined: false, name: "QuantCourierDisplay"
    });
    const courierHead = addOutlinedMesh(THREE, courier, geometry.box, material.paper, outline, {
        position: [0, .39, .01], scale: [.19, .16, .17], name: "QuantCourierHead"
    });
    addOutlinedMesh(THREE, courierHead, geometry.box, material.blue, null, {
        position: [0, .01, .18], scale: [.12, .045, .022],
        outlined: false, name: "QuantCourierVisor"
    });
    const antenna = addOutlinedMesh(THREE, courierHead, geometry.cylinder, material.ink, null, {
        position: [.08, .24, 0], scale: [.02, .14, .02],
        outlined: false, name: "QuantCourierAntenna"
    });
    addOutlinedMesh(THREE, antenna, geometry.sphere, material.coral, null, {
        position: [0, .58, 0], scale: [.08, .08, .08],
        outlined: false, name: "QuantCourierAntennaTip"
    });
    addOutlinedMesh(THREE, courier, geometry.capsule, material.blue, null, {
        position: [0, .03, -.26], rotation: [Math.PI / 2, 0, 0],
        scale: [.72, .72, .72], name: "QuantCourierCanister"
    });

    function createLimb(side, x) {
        const arm = new THREE.Group();
        arm.name = `QuantCourier${side}Arm`;
        arm.position.set(x, .17, 0);
        courier.add(arm);
        addOutlinedMesh(THREE, arm, geometry.capsule, material.architecture, null, {
            position: [0, -.14, 0], scale: [.45, .55, .45],
            name: `QuantCourier${side}Forearm`
        });
        const thigh = new THREE.Group();
        thigh.name = `QuantCourier${side}Thigh`;
        thigh.position.set(x * .57, -.31, 0);
        courier.add(thigh);
        addOutlinedMesh(THREE, thigh, geometry.capsule, material.ink, null, {
            position: [0, -.12, 0], scale: [.48, .62, .48],
            name: `QuantCourier${side}UpperLeg`
        });
        const shin = new THREE.Group();
        shin.name = `QuantCourier${side}Shin`;
        shin.position.set(0, -.34, 0);
        thigh.add(shin);
        addOutlinedMesh(THREE, shin, geometry.capsule, material.architecture, null, {
            position: [0, -.12, .01], scale: [.48, .58, .48],
            name: `QuantCourier${side}LowerLeg`
        });
        addOutlinedMesh(THREE, shin, geometry.box, material.coral, null, {
            position: [0, -.36, .09], scale: [.12, .06, .2],
            outlined: false, name: `QuantCourier${side}Foot`
        });
        return { arm, thigh, shin };
    }

    const left = createLimb("Left", -.27);
    const right = createLimb("Right", .27);
    courier.scale.setScalar(.72);
    courier.castShadow = Boolean(profile.shadows);

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
            top: framingBox.max.y,
            bottom: framingBox.min.y
        };
    }

    const normal = new THREE.Vector3();
    const routePoint = new THREE.Vector3();
    const alignToNormal = new THREE.Quaternion();
    const headingRotation = new THREE.Quaternion();
    const worldUp = new THREE.Vector3(0, 1, 0);
    const fullMotion = profile.name === "high";
    function update({ orbitPose, courierPose, time = 0 }) {
        if (disposed || !orbitPose || !courierPose) {
            return;
        }
        const elapsed = Number.isFinite(time) ? time : 0;
        planet.rotation.set(
            orbitPose.planetPitch,
            orbitPose.planetYaw,
            orbitPose.planetRoll
        );
        const routeProgress = Math.min(Math.max(
            Number(courierPose.routeProgress) || 0, 0
        ), 1);
        routeCurve.getPointAt(routeProgress, routePoint);
        normal.copy(routePoint).normalize();
        courier.position.copy(routePoint).addScaledVector(normal, courierPose.elevation || 0);
        alignToNormal.setFromUnitVectors(worldUp, normal);
        headingRotation.setFromAxisAngle(worldUp, courierPose.heading);
        courier.quaternion.copy(alignToNormal).multiply(headingRotation);

        const stride = Math.sin(courierPose.walkPhase) * .5
            * (1 - courierPose.idleBlend);
        left.thigh.rotation.x = stride;
        right.thigh.rotation.x = -stride;
        left.shin.rotation.x = Math.max(0, -stride) * .7;
        right.shin.rotation.x = Math.max(0, stride) * .7;
        left.arm.rotation.x = -stride * .72;
        right.arm.rotation.x = stride * .72;
        courierHead.rotation.y = courierPose.idleBlend * Math.sin(elapsed * .0012) * .22;
        antenna.rotation.z = Math.sin(elapsed * .004) * .045
            * (1 - courierPose.idleBlend * .4);

        const activity = courierPose.landmarkActivity;
        const boundedActivity = (value) =>
            Math.min(Math.max(Number(value) || 0, 0), 1);
        const observeActivity = boundedActivity(activity.observe);
        const resolveActivity = boundedActivity(activity.resolve);
        const actActivity = boundedActivity(activity.act);
        const routeActivity = boundedActivity(courierPose.routeActivity);
        observeDish.rotation.y = elapsed * .00018 * observeActivity;
        resolveSorter.position.y = resolveSorterBaseY
            + resolveActivity * Math.sin(elapsed * .003) * .08;
        const portalPulse = 1 + actActivity
            * (.04 + Math.sin(elapsed * .003) * .025);
        productPortal.scale.copy(productPortalBaseScale).multiplyScalar(portalPulse);
        productPortal.rotation.z = actActivity * Math.sin(elapsed * .0017) * .08;
        for (let index = 0; index < routeLights.count; index += 1) {
            const localActivity = index < 3
                ? observeActivity
                : index < 7 ? resolveActivity : actActivity;
            const lampLevel = (.15 + localActivity * .85)
                * (.45 + routeActivity * .55);
            lampColor.copy(lampDim).lerp(lampBright, lampLevel);
            routeLights.setColorAt(index, lampColor);
        }
        routeLights.instanceColor.needsUpdate = true;
        if (fullMotion) {
            clouds.rotation.y = elapsed * .000012;
        }
    }

    function dispose() {
        disposeTrackedResources();
    }

    return {
        group: planet,
        getFramingEnvelope,
        update,
        dispose
    };
    } catch (error) {
        disposeTrackedResources();
        throw error;
    }
}
