const TAU = Math.PI * 2;

export function sampleWind(elapsed, x, z, seed = 917) {
    const broad = Math.sin(elapsed * 0.42 - z * 0.19 + seed * 0.0017);
    const crossing = Math.sin(elapsed * 0.71 + x * 0.28 + z * 0.11 + seed * 0.0031);
    const tremor = Math.sin(elapsed * 1.83 - x * 0.67 + z * 0.53 + seed * 0.0073);
    const gust = clamp01(0.5 + broad * 0.28 + crossing * 0.16 + tremor * 0.06);
    return {
        x: round6(0.18 + broad * 0.32 + crossing * 0.12),
        z: round6(-0.06 + crossing * 0.19 + tremor * 0.05),
        gust: round6(gust),
        strength: round6(clamp01(0.16 + gust * 0.72))
    };
}

export function selectNatureProfile(width, height, devicePixelRatio = 1) {
    const shortSide = Math.min(width, height);
    const mobile = width < 720 || shortSide < 430;
    const compact = width < 1100 || height < 680;
    return Object.freeze({
        mobile,
        grassCount: mobile ? 2200 : compact ? 4800 : 7200,
        flowerCount: mobile ? 110 : compact ? 190 : 280,
        pollenCount: mobile ? 180 : compact ? 320 : 520,
        dewCount: mobile ? 260 : compact ? 520 : 860,
        pixelRatio: Math.min(devicePixelRatio || 1, mobile ? 1.25 : 1.5)
    });
}

export function createLivingMeadowWorld(THREE, options = {}) {
    const seed = options.seed ?? 917;
    const profile = {
        grassCount: options.grassCount ?? 7200,
        flowerCount: options.flowerCount ?? 280,
        pollenCount: options.pollenCount ?? 520,
        dewCount: options.dewCount ?? 860
    };
    const random = mulberry32(seed);
    const root = new THREE.Group();
    root.userData.kind = "living-meadow-world";
    const textures = [];

    const sky = createSky(THREE);
    const terrain = createTerrain(THREE);
    const grass = createGrass(THREE, profile.grassCount, random, {
        widthScale: options.grassWidthScale ?? 0.72,
        heightScale: options.grassHeightScale ?? 1,
        colorLift: options.grassColorLift ?? 0
    });
    const fineGrass = createGrass(THREE, Math.max(24, Math.round(profile.grassCount * 0.38)), random, {
        widthScale: 0.3,
        heightScale: 1.12,
        colorLift: 0.035
    });
    const seedHeads = createSeedHeads(THREE, Math.max(24, Math.round(profile.grassCount * 0.06)), random);
    const flowerBundle = createFlowers(THREE, profile.flowerCount, random);
    const dew = createDew(THREE, profile.dewCount, random);
    const pollen = createPollen(THREE, profile.pollenCount, random);
    const mist = createMist(THREE, random);
    const foreground = createForegroundFronds(THREE, random);

    root.add(sky, terrain, grass, fineGrass, seedHeads, flowerBundle.group, dew, pollen, mist, foreground);

    const update = ({ elapsed = 0, pointerX = 0, pointerY = 0, reducedMotion = false } = {}) => {
        const time = reducedMotion ? 1.75 : elapsed;
        const px = reducedMotion ? 0 : pointerX;
        const py = reducedMotion ? 0 : pointerY;
        const wind = sampleWind(time, px * 8, py * 5, seed);

        sky.material.uniforms.uTime.value = time;
        terrain.material.uniforms.uTime.value = time;
        grass.material.uniforms.uTime.value = time;
        grass.material.uniforms.uWind.value.set(wind.x, wind.z);
        grass.material.uniforms.uPointer.value.set(px, py);
        fineGrass.material.uniforms.uTime.value = time;
        fineGrass.material.uniforms.uWind.value.set(wind.x * 1.08, wind.z);
        fineGrass.material.uniforms.uPointer.value.set(px, py);
        seedHeads.rotation.z = Math.sin(time * 0.43) * 0.006 + wind.x * 0.008;
        flowerBundle.material.uniforms.uTime.value = time;
        flowerBundle.material.uniforms.uWind.value.set(wind.x, wind.z);
        dew.material.uniforms.uTime.value = time;
        pollen.material.uniforms.uTime.value = time;
        pollen.position.x = Math.sin(time * 0.09) * 0.42;
        pollen.position.y = Math.sin(time * 0.13) * 0.08;
        mist.children.forEach((layer, index) => {
            layer.material.uniforms.uTime.value = time + index * 7.1;
            layer.position.x = Math.sin(time * (0.018 + index * 0.006) + index) * (0.7 + index * 0.25);
        });
        foreground.rotation.z = Math.sin(time * 0.31) * 0.008 + px * 0.008;
        foreground.rotation.x = Math.sin(time * 0.23 + 1.2) * 0.005;
    };

    const dispose = () => {
        root.traverse((object) => {
            object.geometry?.dispose?.();
            if (Array.isArray(object.material)) object.material.forEach((material) => material.dispose?.());
            else object.material?.dispose?.();
        });
        root.clear();
    };

    return {
        root,
        sky,
        terrain,
        grass,
        fineGrass,
        seedHeads,
        flowers: flowerBundle.group,
        flowerMaterial: flowerBundle.material,
        flowerCenters: flowerBundle.centers,
        dew,
        pollen,
        mist,
        foreground,
        textures,
        update,
        dispose
    };
}

function createSky(THREE) {
    const geometry = new THREE.PlaneGeometry(72, 40, 1, 1);
    const material = new THREE.ShaderMaterial({
        side: THREE.DoubleSide,
        depthWrite: false,
        uniforms: { uTime: { value: 0 } },
        vertexShader: `
            varying vec2 vUv;
            void main() {
                vUv = uv;
                gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0);
            }
        `,
        fragmentShader: `
            precision highp float;
            varying vec2 vUv;
            uniform float uTime;
            float hash(vec2 p){ return fract(sin(dot(p,vec2(127.1,311.7)))*43758.5453); }
            float noise(vec2 p){
                vec2 i=floor(p), f=fract(p); f=f*f*(3.0-2.0*f);
                return mix(mix(hash(i),hash(i+vec2(1.,0.)),f.x),mix(hash(i+vec2(0.,1.)),hash(i+1.),f.x),f.y);
            }
            float fbm(vec2 p){ float v=0.; float a=.5; for(int i=0;i<5;i++){v+=a*noise(p);p=p*2.03+17.1;a*=.5;}return v; }
            void main(){
                vec3 zenith=vec3(.18,.35,.43);
                vec3 horizon=vec3(.82,.78,.60);
                vec3 dawn=vec3(.97,.69,.39);
                float h=smoothstep(0.,1.,vUv.y);
                vec3 color=mix(horizon,zenith,pow(h,.72));
                float sun=1.-smoothstep(.0,.16,distance(vUv,vec2(.72,.67)));
                color+=dawn*sun*.72;
                vec2 cloudUv=vec2(vUv.x*2.2-uTime*.004,vUv.y*5.4);
                float clouds=smoothstep(.53,.78,fbm(cloudUv+fbm(cloudUv*1.8)));
                clouds*=smoothstep(.34,.62,vUv.y)*smoothstep(1.0,.72,vUv.y);
                color=mix(color,vec3(.95,.94,.84),clouds*.28);
                float ridge=.26+noise(vec2(vUv.x*5.2,3.7))*.095+sin(vUv.x*18.)*.012;
                float ridgeFar=.32+noise(vec2(vUv.x*3.1,8.1))*.07;
                color=mix(color,vec3(.22,.34,.31),1.-smoothstep(ridgeFar-.004,ridgeFar+.004,vUv.y));
                color=mix(color,vec3(.09,.20,.18),1.-smoothstep(ridge-.005,ridge+.005,vUv.y));
                float haze=exp(-abs(vUv.y-ridge)*34.)*.28;
                color=mix(color,vec3(.85,.78,.59),haze);
                float grain=(hash(gl_FragCoord.xy+uTime)-.5)*.018;
                gl_FragColor=vec4(color+grain,1.);
            }
        `
    });
    const sky = new THREE.Mesh(geometry, material);
    sky.position.set(0, 7.4, -30);
    sky.frustumCulled = false;
    sky.renderOrder = -20;
    return sky;
}

function createTerrain(THREE) {
    const geometry = new THREE.PlaneGeometry(42, 34, 112, 88);
    geometry.rotateX(-Math.PI / 2);
    const material = new THREE.ShaderMaterial({
        side: THREE.DoubleSide,
        uniforms: { uTime: { value: 0 } },
        vertexShader: `
            varying float vHeight; varying vec3 vWorld; varying vec2 vUv;
            float hill(vec2 p){ return sin(p.x*.23)*.24+sin(p.y*.19+1.7)*.31+sin((p.x+p.y)*.11)*.16; }
            void main(){
                vec3 p=position; p.y+=hill(p.xz);
                vHeight=p.y; vWorld=p; vUv=uv;
                gl_Position=projectionMatrix*modelViewMatrix*vec4(p,1.);
            }
        `,
        fragmentShader: `
            precision highp float; varying float vHeight; varying vec3 vWorld; varying vec2 vUv; uniform float uTime;
            float hash(vec2 p){return fract(sin(dot(p,vec2(41.7,289.1)))*43758.5453);}
            void main(){
                float fleck=hash(floor(vWorld.xz*5.));
                vec3 lo=vec3(.075,.15,.075), hi=vec3(.29,.38,.15), warm=vec3(.42,.39,.18);
                vec3 c=mix(lo,hi,smoothstep(-.5,.55,vHeight));
                c=mix(c,warm,smoothstep(.84,.99,fleck)*.16);
                float wave=sin(vWorld.z*.48-uTime*.42+sin(vWorld.x*.22))* .5+.5;
                c*=.86+wave*.08;
                gl_FragColor=vec4(c,1.);
            }
        `
    });
    const terrain = new THREE.Mesh(geometry, material);
    terrain.position.set(0, -1.52, -5.4);
    terrain.receiveShadow = true;
    return terrain;
}

function createGrass(THREE, count, random, options = {}) {
    const widthScale = options.widthScale ?? 1;
    const heightScale = options.heightScale ?? 1;
    const colorLift = options.colorLift ?? 0;
    const geometry = new THREE.InstancedBufferGeometry();
    const segments = 5;
    const positions = [];
    const uvs = [];
    const indices = [];
    for (let segment = 0; segment <= segments; segment++) {
        const t = segment / segments;
        const width = (1 - Math.pow(t, 1.45)) * 0.052 * widthScale;
        positions.push(-width, t, 0, width, t, 0);
        uvs.push(0, t, 1, t);
        if (segment < segments) {
            const a = segment * 2;
            indices.push(a, a + 1, a + 2, a + 1, a + 3, a + 2);
        }
    }
    geometry.setAttribute("position", new THREE.Float32BufferAttribute(positions, 3));
    geometry.setAttribute("uv", new THREE.Float32BufferAttribute(uvs, 2));
    geometry.setIndex(indices);

    const offsets = new Float32Array(count * 3);
    const scales = new Float32Array(count * 2);
    const phases = new Float32Array(count);
    const yaws = new Float32Array(count);
    const colors = new Float32Array(count * 3);
    const stiffness = new Float32Array(count);
    const color = new THREE.Color();
    for (let i = 0; i < count; i++) {
        const depth = Math.pow(random(), 0.78);
        const z = 4 - depth * 27;
        const spread = 5.8 + depth * 16.5;
        const x = (random() * 2 - 1) * spread;
        const y = terrainHeight(x, z) - 1.48;
        offsets.set([x, y, z], i * 3);
        const height = (0.38 + random() * 0.96) * (0.74 + depth * 0.45) * heightScale;
        scales.set([height, 0.72 + random() * 0.78], i * 2);
        phases[i] = random() * TAU;
        yaws[i] = random() * TAU;
        stiffness[i] = 0.55 + random() * 0.42;
        const palette = random();
        if (palette < 0.35) color.setRGB(0.11 + colorLift, 0.25 + colorLift + random() * 0.07, 0.09);
        else if (palette < 0.78) color.setRGB(0.24 + colorLift + random() * 0.08, 0.39 + colorLift + random() * 0.09, 0.12);
        else color.setRGB(0.45 + colorLift + random() * 0.08, 0.47 + colorLift + random() * 0.07, 0.18);
        colors.set([color.r, color.g, color.b], i * 3);
    }
    geometry.setAttribute("aOffset", new THREE.InstancedBufferAttribute(offsets, 3));
    geometry.setAttribute("aScale", new THREE.InstancedBufferAttribute(scales, 2));
    geometry.setAttribute("aPhase", new THREE.InstancedBufferAttribute(phases, 1));
    geometry.setAttribute("aYaw", new THREE.InstancedBufferAttribute(yaws, 1));
    geometry.setAttribute("aColor", new THREE.InstancedBufferAttribute(colors, 3));
    geometry.setAttribute("aStiffness", new THREE.InstancedBufferAttribute(stiffness, 1));
    geometry.instanceCount = count;

    const material = new THREE.ShaderMaterial({
        side: THREE.DoubleSide,
        uniforms: {
            uTime: { value: 0 },
            uWind: { value: new THREE.Vector2() },
            uPointer: { value: new THREE.Vector2() }
        },
        vertexShader: `
            attribute vec3 aOffset; attribute vec2 aScale; attribute float aPhase; attribute float aYaw;
            attribute vec3 aColor; attribute float aStiffness;
            varying vec2 vUv; varying vec3 vColor; varying float vLight;
            uniform float uTime; uniform vec2 uWind; uniform vec2 uPointer;
            void main(){
                float t=position.y;
                vec3 p=position;
                p.x*=aScale.y; p.y*=aScale.x;
                float broad=sin(uTime*.68-aOffset.z*.34+aOffset.x*.09+aPhase);
                float ripple=sin(uTime*1.37+aOffset.x*.47+aOffset.z*.18+aPhase*1.7);
                float pointerDistance=distance(aOffset.xz,vec2(uPointer.x*9.,-4.-uPointer.y*6.));
                float eddy=exp(-pointerDistance*.22)*uPointer.x;
                float bend=(broad*.22+ripple*.055+uWind.x*.32+eddy*.16)*(1.-aStiffness*.42);
                float curve=t*t*(.64+t*.36);
                p.x+=bend*curve*aScale.x;
                p.z+=(uWind.y*.24+sin(aPhase+uTime*.52)*.045)*curve*aScale.x;
                float c=cos(aYaw),s=sin(aYaw);
                p.xz=mat2(c,-s,s,c)*p.xz;
                p+=aOffset;
                vUv=uv; vColor=aColor;
                vLight=.72+t*.42+max(0.,broad)*.08;
                gl_Position=projectionMatrix*modelViewMatrix*vec4(p,1.);
            }
        `,
        fragmentShader: `
            precision highp float; varying vec2 vUv; varying vec3 vColor; varying float vLight;
            void main(){
                float vein=pow(abs(vUv.x-.5)*2.,7.);
                float edge=smoothstep(.0,.1,vUv.x)*smoothstep(1.,.9,vUv.x);
                vec3 c=vColor*vLight;
                c+=vec3(.22,.23,.08)*(1.-vein)*vUv.y*.17;
                c-=vein*.08;
                gl_FragColor=vec4(c,edge);
            }
        `,
        transparent: true,
        alphaTest: 0.12
    });
    const grass = new THREE.Mesh(geometry, material);
    grass.frustumCulled = false;
    return grass;
}

function createSeedHeads(THREE, count, random) {
    const geometry = new THREE.DodecahedronGeometry(0.045, 0);
    geometry.scale(0.7, 2.35, 0.7);
    const material = new THREE.MeshBasicMaterial({ color: 0xb5aa68 });
    const heads = new THREE.InstancedMesh(geometry, material, count);
    const matrix = new THREE.Matrix4();
    const quaternion = new THREE.Quaternion();
    const position = new THREE.Vector3();
    const scale = new THREE.Vector3();
    const tint = new THREE.Color();
    for (let i = 0; i < count; i++) {
        const depth = 0.12 + random() * 0.88;
        const z = 2.4 - depth * 25;
        const x = (random() * 2 - 1) * (4.8 + depth * 14.5);
        const height = 0.72 + random() * 0.72;
        position.set(x, terrainHeight(x, z) - 1.45 + height, z);
        quaternion.setFromEuler(new THREE.Euler((random() - 0.5) * 0.24, random() * TAU, (random() - 0.5) * 0.18));
        scale.setScalar(0.72 + random() * 0.72);
        matrix.compose(position, quaternion, scale);
        heads.setMatrixAt(i, matrix);
        tint.setRGB(0.44 + random() * 0.23, 0.4 + random() * 0.2, 0.17 + random() * 0.11);
        heads.setColorAt(i, tint);
    }
    heads.instanceMatrix.needsUpdate = true;
    if (heads.instanceColor) heads.instanceColor.needsUpdate = true;
    return heads;
}

function createFlowers(THREE, count, random) {
    const group = new THREE.Group();
    group.name = "wildflowers";
    const stemGeometry = new THREE.CylinderGeometry(0.012, 0.021, 1, 5, 4);
    stemGeometry.translate(0, 0.5, 0);
    const material = new THREE.ShaderMaterial({
        side: THREE.DoubleSide,
        uniforms: { uTime: { value: 0 }, uWind: { value: new THREE.Vector2() } },
        vertexShader: `
            attribute float aPhase; uniform float uTime; uniform vec2 uWind; varying float vHeight;
            void main(){ vec3 p=position; float t=clamp(p.y,0.,1.); float sway=sin(uTime*.74+aPhase)*.09+uWind.x*.13; p.x+=sway*t*t; p.z+=uWind.y*.08*t*t; vHeight=t; gl_Position=projectionMatrix*modelViewMatrix*instanceMatrix*vec4(p,1.); }
        `,
        fragmentShader: `varying float vHeight; void main(){ gl_FragColor=vec4(mix(vec3(.08,.19,.07),vec3(.34,.43,.16),vHeight),1.); }`
    });
    const stems = new THREE.InstancedMesh(stemGeometry, material, count);
    const phases = new Float32Array(count);
    const flowerData = [];
    const matrix = new THREE.Matrix4();
    const quaternion = new THREE.Quaternion();
    const scale = new THREE.Vector3();
    const position = new THREE.Vector3();
    for (let i = 0; i < count; i++) {
        const depth = 0.18 + random() * 0.82;
        const z = 2.5 - depth * 24;
        const x = (random() * 2 - 1) * (4.5 + depth * 13);
        const height = 0.56 + random() * 0.68;
        const y = terrainHeight(x, z) - 1.47;
        position.set(x, y, z);
        scale.set(1, height, 1);
        matrix.compose(position, quaternion, scale);
        stems.setMatrixAt(i, matrix);
        phases[i] = random() * TAU;
        flowerData.push({ x, y: y + height, z, size: 0.07 + random() * 0.085, phase: phases[i], family: Math.floor(random() * 3) });
    }
    stemGeometry.setAttribute("aPhase", new THREE.InstancedBufferAttribute(phases, 1));
    stems.instanceMatrix.needsUpdate = true;
    group.add(stems);

    const petalGeometry = new THREE.CircleGeometry(1, 7);
    const petalColors = [0xe9d8a6, 0xd9b4c6, 0xf0eee0];
    for (let family = 0; family < 3; family++) {
        const familyFlowers = flowerData.filter((flower) => flower.family === family);
        const petals = new THREE.InstancedMesh(
            petalGeometry,
            new THREE.MeshBasicMaterial({ color: petalColors[family], side: THREE.DoubleSide, transparent: true, opacity: 0.94 }),
            Math.max(1, familyFlowers.length * 5)
        );
        let index = 0;
        familyFlowers.forEach((flower) => {
            for (let petal = 0; petal < 5; petal++) {
                const angle = petal / 5 * TAU + flower.phase;
                position.set(
                    flower.x + Math.cos(angle) * flower.size * 0.82,
                    flower.y + Math.sin(angle) * flower.size * 0.27,
                    flower.z + Math.sin(angle) * flower.size * 0.65
                );
                quaternion.setFromEuler(new THREE.Euler(-0.62 + Math.sin(angle) * 0.24, angle, angle * 0.18));
                scale.set(flower.size, flower.size * 0.58, flower.size);
                matrix.compose(position, quaternion, scale);
                petals.setMatrixAt(index++, matrix);
            }
        });
        petals.count = index;
        petals.instanceMatrix.needsUpdate = true;
        group.add(petals);
    }
    const centerGeometry = new THREE.IcosahedronGeometry(0.055, 1);
    const centerMaterial = new THREE.MeshBasicMaterial({ color: 0xceb760 });
    const centers = new THREE.InstancedMesh(centerGeometry, centerMaterial, flowerData.length);
    flowerData.forEach((flower, index) => {
        position.set(flower.x, flower.y + flower.size * 0.04, flower.z + flower.size * 0.04);
        quaternion.identity();
        scale.setScalar(0.7 + flower.size * 2.1);
        matrix.compose(position, quaternion, scale);
        centers.setMatrixAt(index, matrix);
    });
    centers.instanceMatrix.needsUpdate = true;
    group.add(centers);
    return { group, material, centers };
}

function createDew(THREE, count, random) {
    const positions = new Float32Array(count * 3);
    const sizes = new Float32Array(count);
    for (let i = 0; i < count; i++) {
        const depth = random();
        const z = 2 - depth * 20;
        const x = (random() * 2 - 1) * (5 + depth * 12);
        positions.set([x, terrainHeight(x, z) - 1.15 + random() * 0.75, z], i * 3);
        sizes[i] = 0.65 + random() * 1.2;
    }
    const geometry = new THREE.BufferGeometry();
    geometry.setAttribute("position", new THREE.BufferAttribute(positions, 3));
    geometry.setAttribute("aSize", new THREE.BufferAttribute(sizes, 1));
    const material = new THREE.ShaderMaterial({
        transparent: true,
        depthWrite: false,
        blending: THREE.AdditiveBlending,
        uniforms: { uTime: { value: 0 } },
        vertexShader: `attribute float aSize; varying float vSparkle; uniform float uTime; void main(){ vec4 mv=modelViewMatrix*vec4(position,1.); vSparkle=.4+.6*sin(uTime*1.4+position.x*3.1+position.z); gl_PointSize=aSize*(90./-mv.z); gl_Position=projectionMatrix*mv; }`,
        fragmentShader: `varying float vSparkle; void main(){ float d=length(gl_PointCoord-.5); float a=1.-smoothstep(.06,.5,d); gl_FragColor=vec4(vec3(.78,.94,1.)*(.5+vSparkle*.7),a*(.18+vSparkle*.45)); }`
    });
    return new THREE.Points(geometry, material);
}

function createPollen(THREE, count, random) {
    const positions = new Float32Array(count * 3);
    const phases = new Float32Array(count);
    for (let i = 0; i < count; i++) {
        positions.set([(random() * 2 - 1) * 13, random() * 6.3 - 0.6, -2 - random() * 18], i * 3);
        phases[i] = random() * TAU;
    }
    const geometry = new THREE.BufferGeometry();
    geometry.setAttribute("position", new THREE.BufferAttribute(positions, 3));
    geometry.setAttribute("aPhase", new THREE.BufferAttribute(phases, 1));
    const material = new THREE.ShaderMaterial({
        transparent: true,
        depthWrite: false,
        blending: THREE.AdditiveBlending,
        uniforms: { uTime: { value: 0 } },
        vertexShader: `attribute float aPhase; uniform float uTime; varying float vAlpha; void main(){ vec3 p=position; p.x+=sin(uTime*.22+aPhase+p.y)*.28; p.y+=sin(uTime*.31+aPhase*1.7)*.12; vec4 mv=modelViewMatrix*vec4(p,1.); gl_PointSize=(1.2+sin(aPhase)*.5)*(52./-mv.z); gl_Position=projectionMatrix*mv; vAlpha=.35+.3*sin(uTime*.53+aPhase); }`,
        fragmentShader: `varying float vAlpha; void main(){ float d=length(gl_PointCoord-.5); float a=1.-smoothstep(.08,.5,d); gl_FragColor=vec4(.95,.82,.49,a*vAlpha); }`
    });
    return new THREE.Points(geometry, material);
}

function createMist(THREE, random) {
    const group = new THREE.Group();
    for (let i = 0; i < 4; i++) {
        const geometry = new THREE.PlaneGeometry(22 + i * 5, 3.6 + i * 0.7);
        const material = new THREE.ShaderMaterial({
            transparent: true,
            depthWrite: false,
            side: THREE.DoubleSide,
            uniforms: { uTime: { value: i * 4 } },
            vertexShader: `varying vec2 vUv; void main(){vUv=uv;gl_Position=projectionMatrix*modelViewMatrix*vec4(position,1.);}`,
            fragmentShader: `precision highp float; varying vec2 vUv; uniform float uTime; float hash(vec2 p){return fract(sin(dot(p,vec2(127.1,311.7)))*43758.5453);} float n(vec2 p){vec2 i=floor(p),f=fract(p);f=f*f*(3.-2.*f);return mix(mix(hash(i),hash(i+vec2(1,0)),f.x),mix(hash(i+vec2(0,1)),hash(i+1.),f.x),f.y);} void main(){float cloud=n(vec2(vUv.x*5.-uTime*.012,vUv.y*2.3)+n(vUv*4.));float edge=smoothstep(0.,.26,vUv.y)*smoothstep(1.,.68,vUv.y)*smoothstep(0.,.2,vUv.x)*smoothstep(1.,.8,vUv.x);gl_FragColor=vec4(.82,.87,.76,cloud*edge*.12);}`
        });
        const layer = new THREE.Mesh(geometry, material);
        layer.position.set((random() * 2 - 1) * 2, 0.1 + i * 0.58, -10 - i * 3.8);
        group.add(layer);
    }
    return group;
}

function createForegroundFronds(THREE, random) {
    const group = new THREE.Group();
    group.name = "foreground-fronds";
    const geometry = new THREE.ConeGeometry(0.08, 1.8, 5, 5);
    geometry.translate(0, 0.9, 0);
    const material = new THREE.MeshBasicMaterial({ color: 0x173b1c, side: THREE.DoubleSide });
    const mesh = new THREE.InstancedMesh(geometry, material, 22);
    const matrix = new THREE.Matrix4();
    const quaternion = new THREE.Quaternion();
    const scale = new THREE.Vector3();
    const position = new THREE.Vector3();
    for (let i = 0; i < 22; i++) {
        const side = i % 2 === 0 ? -1 : 1;
        position.set(side * (5.8 + random() * 3.8), -1.85 + random() * 0.2, 1.2 - random() * 5);
        quaternion.setFromEuler(new THREE.Euler((random() - 0.5) * 0.18, (random() - 0.5) * 0.45, side * (0.12 + random() * 0.15)));
        scale.set(0.65 + random() * 0.65, 0.74 + random() * 0.8, 0.65 + random() * 0.65);
        matrix.compose(position, quaternion, scale);
        mesh.setMatrixAt(i, matrix);
    }
    mesh.instanceMatrix.needsUpdate = true;
    group.add(mesh);
    return group;
}

function terrainHeight(x, z) {
    return Math.sin(x * 0.23) * 0.24 + Math.sin(z * 0.19 + 1.7) * 0.31 + Math.sin((x + z) * 0.11) * 0.16;
}

function mulberry32(seed) {
    let value = seed >>> 0;
    return () => {
        value |= 0;
        value = value + 0x6d2b79f5 | 0;
        let result = Math.imul(value ^ value >>> 15, 1 | value);
        result = result + Math.imul(result ^ result >>> 7, 61 | result) ^ result;
        return ((result ^ result >>> 14) >>> 0) / 4294967296;
    };
}

function clamp01(value) {
    return Math.max(0, Math.min(1, value));
}

function round6(value) {
    return Math.round(value * 1_000_000) / 1_000_000;
}
