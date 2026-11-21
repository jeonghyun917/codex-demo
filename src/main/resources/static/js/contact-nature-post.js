export function createNaturePostProcessor(THREE, renderer, options = {}) {
    const {
        width = 1,
        height = 1,
        pixelRatio = 1,
        forceFallback = false
    } = options;
    let disposed = false;
    let active = !forceFallback && typeof renderer?.setRenderTarget === "function";
    let target = null;
    let material = null;
    let geometry = null;
    let postScene = null;
    let postCamera = null;
    let targetWidth = 1;
    let targetHeight = 1;

    const resize = (nextWidth, nextHeight, nextPixelRatio = 1) => {
        targetWidth = Math.max(1, Math.round(nextWidth * nextPixelRatio));
        targetHeight = Math.max(1, Math.round(nextHeight * nextPixelRatio));
        if (target) target.setSize(targetWidth, targetHeight);
        if (material) material.uniforms.uResolution.value.set(targetWidth, targetHeight);
    };

    if (active) {
        try {
            target = new THREE.WebGLRenderTarget(1, 1, {
                minFilter: THREE.LinearFilter,
                magFilter: THREE.LinearFilter,
                format: THREE.RGBAFormat,
                type: THREE.UnsignedByteType,
                depthBuffer: true,
                stencilBuffer: false
            });
            target.texture.name = "contact4-meadow-color";
            target.depthTexture = new THREE.DepthTexture(1, 1, THREE.UnsignedIntType);
            target.depthTexture.name = "contact4-meadow-depth";
            target.depthTexture.format = THREE.DepthFormat;

            geometry = new THREE.PlaneGeometry(2, 2);
            material = new THREE.ShaderMaterial({
                name: "contact4-meadow-finishing-pass",
                uniforms: {
                    tDiffuse: { value: target.texture },
                    tDepth: { value: target.depthTexture },
                    uResolution: { value: new THREE.Vector2(1, 1) },
                    uFocusDepth: { value: 0.986 },
                    uAperture: { value: 0.72 },
                    uAoStrength: { value: 0.5 },
                    uExposure: { value: 0.99 },
                    uGradeStrength: { value: 0.22 }
                },
                vertexShader: `
                    varying vec2 vUv;
                    void main() {
                        vUv = uv;
                        gl_Position = vec4(position.xy, 0.0, 1.0);
                    }
                `,
                fragmentShader: `
                    uniform sampler2D tDiffuse;
                    uniform sampler2D tDepth;
                    uniform vec2 uResolution;
                    uniform float uFocusDepth;
                    uniform float uAperture;
                    uniform float uAoStrength;
                    uniform float uExposure;
                    uniform float uGradeStrength;
                    varying vec2 vUv;

                    float readDepth(vec2 uv) {
                        return texture2D(tDepth, clamp(uv, 0.001, 0.999)).x;
                    }

                    float contactOcclusion(vec2 uv, float centerDepth) {
                        vec2 pixel = 1.0 / uResolution;
                        float occlusion = 0.0;
                        occlusion += max(0.0, centerDepth - readDepth(uv + vec2(pixel.x, 0.0)) - 0.00018);
                        occlusion += max(0.0, centerDepth - readDepth(uv - vec2(pixel.x, 0.0)) - 0.00018);
                        occlusion += max(0.0, centerDepth - readDepth(uv + vec2(0.0, pixel.y)) - 0.00018);
                        occlusion += max(0.0, centerDepth - readDepth(uv - vec2(0.0, pixel.y)) - 0.00018);
                        occlusion += max(0.0, centerDepth - readDepth(uv + pixel * vec2(2.0, 1.5)) - 0.00028);
                        occlusion += max(0.0, centerDepth - readDepth(uv - pixel * vec2(2.0, 1.5)) - 0.00028);
                        return clamp(occlusion * 92.0 * uAoStrength, 0.0, 0.24);
                    }

                    vec3 focusBlur(vec2 uv, float depth) {
                        vec2 pixel = 1.0 / uResolution;
                        float focusAmount = smoothstep(0.0012, 0.018, abs(depth - uFocusDepth)) * uAperture;
                        vec2 radius = pixel * (0.6 + focusAmount * 3.4);
                        vec3 color = texture2D(tDiffuse, uv).rgb * 0.34;
                        color += texture2D(tDiffuse, uv + vec2(radius.x, 0.0)).rgb * 0.11;
                        color += texture2D(tDiffuse, uv - vec2(radius.x, 0.0)).rgb * 0.11;
                        color += texture2D(tDiffuse, uv + vec2(0.0, radius.y)).rgb * 0.11;
                        color += texture2D(tDiffuse, uv - vec2(0.0, radius.y)).rgb * 0.11;
                        color += texture2D(tDiffuse, uv + radius).rgb * 0.055;
                        color += texture2D(tDiffuse, uv - radius).rgb * 0.055;
                        color += texture2D(tDiffuse, uv + vec2(radius.x, -radius.y)).rgb * 0.055;
                        color += texture2D(tDiffuse, uv + vec2(-radius.x, radius.y)).rgb * 0.055;
                        return color;
                    }

                    vec3 warmGrade(vec3 color) {
                        float luminance = dot(color, vec3(0.2126, 0.7152, 0.0722));
                        vec3 saturated = mix(vec3(luminance), color, 1.11);
                        vec3 warm = saturated * vec3(1.022, 1.014, 0.988);
                        return mix(color, warm, uGradeStrength) * uExposure;
                    }

                    void main() {
                        float depth = readDepth(vUv);
                        vec3 color = focusBlur(vUv, depth);
                        color *= 1.0 - contactOcclusion(vUv, depth);
                        color = warmGrade(color);
                        vec2 centered = vUv * 2.0 - 1.0;
                        float vignette = smoothstep(1.35, 0.2, dot(centered, centered));
                        color *= mix(0.975, 1.015, vignette);
                        gl_FragColor = vec4(color, 1.0);
                        #include <tonemapping_fragment>
                        #include <colorspace_fragment>
                    }
                `,
                depthTest: false,
                depthWrite: false
            });
            geometry.computeBoundingBox();
            const quad = new THREE.Mesh(geometry, material);
            quad.frustumCulled = false;
            postScene = new THREE.Scene();
            postScene.add(quad);
            postCamera = new THREE.OrthographicCamera(-1, 1, 1, -1, 0, 1);
            resize(width, height, pixelRatio);
        } catch (error) {
            active = false;
            target?.dispose?.();
            geometry?.dispose?.();
            material?.dispose?.();
            target = null;
            geometry = null;
            material = null;
            postScene = null;
            postCamera = null;
        }
    } else {
        resize(width, height, pixelRatio);
    }

    const render = (scene, camera) => {
        if (disposed) return;
        if (!active) {
            renderer.render(scene, camera);
            return;
        }
        const previousTarget = renderer.getRenderTarget?.() || null;
        renderer.setRenderTarget(target);
        renderer.render(scene, camera);
        renderer.setRenderTarget(previousTarget);
        renderer.render(postScene, postCamera);
    };

    const dispose = () => {
        if (disposed) return;
        disposed = true;
        target?.dispose();
        geometry?.dispose();
        material?.dispose();
        postScene?.clear();
    };

    const diagnostics = () => ({
        active,
        disposed,
        width: targetWidth,
        height: targetHeight,
        hasDepth: Boolean(target?.depthTexture)
    });

    return { render, resize, dispose, diagnostics, target, material };
}
