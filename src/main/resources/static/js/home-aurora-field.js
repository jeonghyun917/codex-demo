export function createAuroraField(THREE, quality) {
    const uniforms = {
        uTime: { value: 0 },
        uResolution: { value: new THREE.Vector2(1, 1) },
        uPointer: { value: new THREE.Vector2(0.5, 0.5) },
        uPointerEnergy: { value: 0 },
        uScroll: { value: 0 },
        uQuality: { value: quality.shaderQuality }
    };

    const material = new THREE.ShaderMaterial({
        uniforms,
        vertexShader: `
            varying vec2 vUv;
            void main() {
                vUv = uv;
                gl_Position = vec4(position, 1.0);
            }
        `,
        fragmentShader: `
            precision highp float;
            varying vec2 vUv;
            uniform float uTime;
            uniform vec2 uResolution;
            uniform vec2 uPointer;
            uniform float uPointerEnergy;
            uniform float uScroll;
            uniform float uQuality;

            float hash21(vec2 value) {
                value = fract(value * vec2(123.34, 456.21));
                value += dot(value, value + 45.32);
                return fract(value.x * value.y);
            }

            float noise(vec2 point) {
                vec2 cell = floor(point);
                vec2 local = fract(point);
                local = local * local * (3.0 - 2.0 * local);
                return mix(
                    mix(hash21(cell), hash21(cell + vec2(1.0, 0.0)), local.x),
                    mix(hash21(cell + vec2(0.0, 1.0)), hash21(cell + 1.0), local.x),
                    local.y
                );
            }

            float fbm(vec2 point) {
                float value = 0.0;
                float amplitude = 0.5;
                mat2 rotation = mat2(0.8, -0.6, 0.6, 0.8);
                for (int octave = 0; octave < 5; octave++) {
                    if (octave >= 3 && uQuality < 0.65) {
                        break;
                    }
                    if (octave >= 4 && uQuality < 0.9) {
                        break;
                    }
                    value += amplitude * noise(point);
                    point = rotation * point * 2.03 + 19.1;
                    amplitude *= 0.5;
                }
                return value;
            }

            float auroraBand(vec2 point, float offset, float width, float frequency, float phase) {
                float warp = fbm(vec2(point.x * frequency + phase, point.y * 0.7 - phase));
                float center = offset + (warp - 0.5) * (0.42 + uScroll * 0.16);
                float distanceToBand = abs(point.y - center);
                float core = exp(-distanceToBand * distanceToBand / max(0.001, width));
                float strands = 0.72 + 0.28 * noise(vec2(point.x * 8.0 + phase, point.y * 3.0));
                return core * strands;
            }

            vec3 screenBlend(vec3 base, vec3 light) {
                return 1.0 - (1.0 - base) * (1.0 - light);
            }

            void main() {
                vec2 uv = vUv;
                vec2 point = uv - 0.5;
                point.x *= uResolution.x / max(1.0, uResolution.y);

                vec2 pointerPoint = uPointer - 0.5;
                pointerPoint.x *= uResolution.x / max(1.0, uResolution.y);
                float pointerDistance = length(point - pointerPoint);
                float pointerGlow = exp(-pointerDistance * pointerDistance * 5.2);
                float localEnergy = pointerGlow * (0.12 + uPointerEnergy * 0.55);
                float widthLift = pointerGlow * uPointerEnergy * 0.028;

                float convergence = smoothstep(0.74, 1.0, uScroll);
                float time = uTime * mix(1.0, 0.35, convergence);
                float depth = mix(0.86, 1.42, uScroll);
                vec2 driftA = point * depth + vec2(time * 0.018, time * -0.012);
                vec2 driftB = point * (depth * 1.32) + vec2(time * -0.012, time * 0.016);
                vec2 driftC = point * (depth * 0.74) + vec2(time * 0.009, time * 0.011);

                float violetBand = auroraBand(driftA, 0.05, 0.075 + widthLift, 1.65, time * 0.035);
                float cobaltBand = auroraBand(driftB, -0.08, 0.052 + widthLift, 2.1, 4.7 - time * 0.028);
                float cyanBand = auroraBand(driftC, 0.15, 0.062 + widthLift, 1.4, 8.3 + time * 0.022);
                float emeraldBand = auroraBand(driftB * 0.88, -0.18, 0.045 + widthLift * 0.7, 1.8, 12.6);
                float roseBand = auroraBand(driftA * 1.12, 0.24, 0.038 + widthLift * 0.45, 2.35, 16.2);

                vec3 color = vec3(0.0078, 0.0157, 0.0431);
                color = screenBlend(color, vec3(0.4667, 0.4196, 1.0) * violetBand * 0.54);
                color = screenBlend(color, vec3(0.2980, 0.4353, 1.0) * cobaltBand * 0.48);
                color = screenBlend(color, vec3(0.1961, 0.8471, 1.0) * cyanBand * 0.43);
                color = screenBlend(color, vec3(0.2392, 1.0, 0.8157) * emeraldBand * 0.25);
                color = screenBlend(color, vec3(1.0, 0.4353, 0.7843) * roseBand * 0.12);
                color = screenBlend(color, vec3(0.22, 0.64, 1.0) * localEnergy);

                float aperture = exp(-length(point * vec2(0.78, 1.2)) * 2.8);
                color += vec3(0.10, 0.23, 0.42) * aperture * convergence * 0.34;

                float radiance = max(max(color.r, color.g), color.b);
                color += color * smoothstep(0.42, 0.9, radiance) * 0.22;

                float vignette = 1.0 - smoothstep(0.18, 0.95, length(point * vec2(0.82, 1.0)));
                color *= 0.62 + vignette * 0.42;
                float grain = (hash21(gl_FragCoord.xy + floor(time * 24.0)) - 0.5)
                    * mix(0.012, 0.022, uQuality)
                    * mix(1.0, 0.35, convergence);
                color += grain;
                color = clamp(color, vec3(0.0), vec3(0.94));

                gl_FragColor = vec4(color, 0.96);
                #include <tonemapping_fragment>
                #include <colorspace_fragment>
            }
        `,
        depthTest: false,
        depthWrite: false
    });

    const geometry = new THREE.PlaneGeometry(2, 2);
    const mesh = new THREE.Mesh(geometry, material);
    mesh.frustumCulled = false;

    return {
        mesh,
        uniforms,
        resize(width, height) {
            uniforms.uResolution.value.set(width, height);
        },
        update(time, pointerX, pointerY, pointerEnergy, scroll) {
            uniforms.uTime.value = time;
            uniforms.uPointer.value.set(pointerX, pointerY);
            uniforms.uPointerEnergy.value = pointerEnergy;
            uniforms.uScroll.value = scroll;
        },
        dispose() {
            geometry.dispose();
            material.dispose();
        }
    };
}
