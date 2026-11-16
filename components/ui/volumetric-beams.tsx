"use client";

import { useEffect, useMemo } from "react";
import * as THREE from "three";
import { Canvas, useFrame, useThree } from "@react-three/fiber";
import type { CanvasProps, ThreeElements } from "@react-three/fiber";

const vertexShader = `
  varying vec2 vUv;
  void main() {
    vUv = uv;
    gl_Position = vec4(position.xy, 0.0, 1.0);
  }
`;

const fragmentShader = `
  precision highp float;

  varying vec2 vUv;

  uniform float uTime;
  uniform vec2  uResolution;
  uniform vec2  uMouse;

  uniform float uSpeed;
  uniform float uRadius;
  uniform float uFov;
  uniform float uMouseInfluence;
  uniform float uAutoRotateSpeed;

  uniform float uBeamCount;
  uniform float uHalfAngle;
  uniform float uEdgeSoft;
  uniform float uBeamRot;
  uniform float uTwistDepth;

  uniform float uDensity;
  uniform float uFalloff;
  uniform float uAniso;
  uniform float uLightIntensity;
  uniform vec3  uLightColor;
  uniform vec3  uTint;

  uniform float uStripeFreq;
  uniform float uStripeAmp;
  uniform float uStripeSharp;
  uniform float uStripeSpeed;
  uniform float uStripeJit;

  uniform float uVolSteps;
  uniform float uStepMin;
  uniform float uStepMax;
  uniform float uMaxDist;

  uniform float uExposure;
  uniform float uGamma;
  uniform float uGrainAmount;
  uniform float uVignette;
  uniform vec3  uBgColor;

  const float PI = 3.141592653589793;

  float hash21(vec2 p) {
    p = fract(p*vec2(123.34, 345.45));
    p += dot(p, p+34.45);
    return fract(p.x*p.y);
  }

  mat2 rot2(float a){ float s=sin(a), c=cos(a); return mat2(c,-s,s,c); }

  void beamAxis(vec2 p, float N, float rot, out vec2 axis, out float angDist){
    float ang = atan(p.y, p.x) + rot;
    float period = 2.0*PI / max(1.0, N);
    float k = floor(ang/period + 0.5);
    float centerAng = k * period;
    axis = vec2(cos(centerAng - rot), sin(centerAng - rot));
    float d = ang - centerAng;
    d = mod(d + PI, 2.0*PI) - PI;
    angDist = abs(d);
  }

  float beamMask(float ad, float halfAng, float edgeSoft){
    float a0 = max(0.0, halfAng - edgeSoft);
    float a1 = halfAng + edgeSoft;
    return 1.0 - smoothstep(a0, a1, ad);
  }

  float hg(float mu, float g){
    float g2 = g*g;
    return (1.0 - g2) / pow(1.0 + g2 - 2.0*g*mu, 1.5);
  }

  float mediumDensity(vec3 p, float t, out vec2 stripeInfo){
    vec3 q = p;
    q.xy *= rot2(uTwistDepth * q.z);

    vec2 axis; float ad;
    beamAxis(q.xy, uBeamCount, uBeamRot, axis, ad);

    float beam = beamMask(ad, uHalfAngle, uEdgeSoft);
    float r = length(q.xy);
    float center = exp(-uFalloff * r * r);

    vec2 perp = vec2(-axis.y, axis.x);
    float coord = dot(q.xy, perp);
    float jit = uStripeJit * sin(0.7*q.z + 2.3*coord + 1.7*t);
    float stripes = 0.5 + 0.5 * sin(coord * uStripeFreq + jit - t*uStripeSpeed);
    stripes = pow(clamp(stripes, 0.0, 1.0), uStripeSharp);

    float rib = mix(1.0, 0.55 + 0.45*stripes, uStripeAmp * beam);
    float d = uDensity * beam * center * rib;

    stripeInfo = vec2(stripes, beam);
    return d;
  }

  void main(){
    float t = uTime * uSpeed;
    vec2 uv = (gl_FragCoord.xy - 0.5*uResolution.xy) / uResolution.y;

    float az = t*uAutoRotateSpeed + (uMouse.x*2.0-1.0) * PI * 0.35 * uMouseInfluence;
    float el = (uMouse.y*2.0-1.0) * 0.25 * uMouseInfluence;

    vec3 ro = vec3(cos(az)*cos(el), sin(el), sin(az)*cos(el)) * uRadius;
    vec3 ta = vec3(0.0);

    vec3 ww = normalize(ta - ro);
    vec3 uu = normalize(cross(vec3(0.0,1.0,0.0), ww));
    vec3 vv = cross(ww, uu);

    vec3 rd = normalize(uv.x*uu + uv.y*vv + uFov*ww);

    vec3 col = uBgColor;
    vec3 accum = vec3(0.0);
    float Tr = 1.0;
    float dist = 0.0;
    int stepsHard = int(uVolSteps);

    for(int i=0; i<256; i++){
      if(i >= stepsHard) break;

      float s = mix(uStepMin, uStepMax, clamp(dist/uMaxDist, 0.0, 1.0));
      vec3 pos = ro + rd * dist;

      vec2 stripeInfo;
      float dens = mediumDensity(pos, t, stripeInfo);

      vec3 L = normalize(-pos);
      float mu = dot(rd, L);
      float phase = hg(mu, uAniso);

      vec3 scatterCol = uLightColor * uLightIntensity * phase * dens;
      accum += Tr * scatterCol * s;
      Tr *= exp(-dens * s);

      dist += s;

      if(Tr < 1e-3 || dist > uMaxDist) break;
    }

    col += accum * abs(ro * 0.3) * uTint;

    float vig = 1.0 - uVignette * length(uv);
    col *= clamp(vig, 0.0, 1.0);

    float g = (hash21(gl_FragCoord.xy + fract(t*123.45)) - 0.5) * uGrainAmount * 1.4;
    col += g;

    col *= uExposure;
    col = col / (1.0 + col);
    col = pow(col, vec3(1.0 / uGamma));

    gl_FragColor = vec4(col, 1.0);
  }
`;

type Vec3 = [number, number, number];

export type VolumetricBeamsShaderProps = {
  speed?: number;
  autoRotateSpeed?: number;
  mouseInfluence?: number;
  cameraRadius?: number;
  fov?: number;
  beamCount?: number;
  beamHalfAngle?: number;
  beamEdgeSoft?: number;
  beamRotation?: number;
  twistDepth?: number;
  density?: number;
  falloff?: number;
  anisotropy?: number;
  lightIntensity?: number;
  lightColor?: Vec3;
  tint?: Vec3;
  stripeFreq?: number;
  stripeAmp?: number;
  stripeSharp?: number;
  stripeSpeed?: number;
  stripeJitter?: number;
  volSteps?: number;
  stepMin?: number;
  stepMax?: number;
  maxDist?: number;
  exposure?: number;
  gamma?: number;
  grainAmount?: number;
  vignette?: number;
  bgColor?: Vec3;
  pointerSmoothing?: number;
} & ThreeElements["mesh"];

function VolumetricBeamsShader({
  speed = 0.25,
  autoRotateSpeed = 0.015,
  mouseInfluence = 0.45,
  cameraRadius = 3.8,
  fov = 1.65,
  beamCount = 4,
  beamHalfAngle = 0.085,
  beamEdgeSoft = 0.045,
  beamRotation = 0.0,
  twistDepth = 0.06,
  density = 1.15,
  falloff = 0.55,
  anisotropy = 0.76,
  lightIntensity = 2.2,
  lightColor = [0.64, 0.74, 1.0],
  tint = [0.55, 0.58, 0.95],
  stripeFreq = 42.0,
  stripeAmp = 0.55,
  stripeSharp = 1.85,
  stripeSpeed = 0.12,
  stripeJitter = 0.25,
  volSteps = 110,
  stepMin = 0.015,
  stepMax = 0.06,
  maxDist = 18.0,
  exposure = 1.05,
  gamma = 2.0,
  grainAmount = 0.045,
  vignette = 0.35,
  bgColor = [0.04, 0.035, 0.06],
  pointerSmoothing = 0.18,
  ...meshProps
}: VolumetricBeamsShaderProps) {
  const { size, gl } = useThree();
  const tmpV2 = useMemo(() => new THREE.Vector2(), []);

  const uniforms = useMemo(
    () => ({
      uTime: { value: 0 },
      uResolution: { value: new THREE.Vector2(1, 1) },
      uMouse: { value: new THREE.Vector2(0.5, 0.5) },
      uSpeed: { value: speed },
      uRadius: { value: cameraRadius },
      uFov: { value: fov },
      uMouseInfluence: { value: mouseInfluence },
      uAutoRotateSpeed: { value: autoRotateSpeed },
      uBeamCount: { value: beamCount },
      uHalfAngle: { value: beamHalfAngle },
      uEdgeSoft: { value: beamEdgeSoft },
      uBeamRot: { value: beamRotation },
      uTwistDepth: { value: twistDepth },
      uDensity: { value: density },
      uFalloff: { value: falloff },
      uAniso: { value: anisotropy },
      uLightIntensity: { value: lightIntensity },
      uLightColor: { value: new THREE.Vector3().fromArray(lightColor) },
      uTint: { value: new THREE.Vector3().fromArray(tint) },
      uStripeFreq: { value: stripeFreq },
      uStripeAmp: { value: stripeAmp },
      uStripeSharp: { value: stripeSharp },
      uStripeSpeed: { value: stripeSpeed },
      uStripeJit: { value: stripeJitter },
      uVolSteps: { value: volSteps },
      uStepMin: { value: stepMin },
      uStepMax: { value: stepMax },
      uMaxDist: { value: maxDist },
      uExposure: { value: exposure },
      uGamma: { value: gamma },
      uGrainAmount: { value: grainAmount },
      uVignette: { value: vignette },
      uBgColor: { value: new THREE.Vector3().fromArray(bgColor) },
    }),
    [],
  );

  useEffect(() => { uniforms.uSpeed.value = speed; }, [speed, uniforms]);
  useEffect(() => { uniforms.uRadius.value = cameraRadius; }, [cameraRadius, uniforms]);
  useEffect(() => { uniforms.uFov.value = fov; }, [fov, uniforms]);
  useEffect(() => { uniforms.uMouseInfluence.value = mouseInfluence; }, [mouseInfluence, uniforms]);
  useEffect(() => { uniforms.uAutoRotateSpeed.value = autoRotateSpeed; }, [autoRotateSpeed, uniforms]);
  useEffect(() => { uniforms.uBeamCount.value = beamCount; }, [beamCount, uniforms]);
  useEffect(() => { uniforms.uHalfAngle.value = beamHalfAngle; }, [beamHalfAngle, uniforms]);
  useEffect(() => { uniforms.uEdgeSoft.value = beamEdgeSoft; }, [beamEdgeSoft, uniforms]);
  useEffect(() => { uniforms.uBeamRot.value = beamRotation; }, [beamRotation, uniforms]);
  useEffect(() => { uniforms.uTwistDepth.value = twistDepth; }, [twistDepth, uniforms]);
  useEffect(() => { uniforms.uDensity.value = density; }, [density, uniforms]);
  useEffect(() => { uniforms.uFalloff.value = falloff; }, [falloff, uniforms]);
  useEffect(() => { uniforms.uAniso.value = anisotropy; }, [anisotropy, uniforms]);
  useEffect(() => { uniforms.uLightIntensity.value = lightIntensity; }, [lightIntensity, uniforms]);
  useEffect(() => { uniforms.uLightColor.value.fromArray(lightColor); }, [lightColor, uniforms]);
  useEffect(() => { uniforms.uTint.value.fromArray(tint); }, [tint, uniforms]);
  useEffect(() => { uniforms.uStripeFreq.value = stripeFreq; }, [stripeFreq, uniforms]);
  useEffect(() => { uniforms.uStripeAmp.value = stripeAmp; }, [stripeAmp, uniforms]);
  useEffect(() => { uniforms.uStripeSharp.value = stripeSharp; }, [stripeSharp, uniforms]);
  useEffect(() => { uniforms.uStripeSpeed.value = stripeSpeed; }, [stripeSpeed, uniforms]);
  useEffect(() => { uniforms.uStripeJit.value = stripeJitter; }, [stripeJitter, uniforms]);
  useEffect(() => { uniforms.uVolSteps.value = volSteps; }, [volSteps, uniforms]);
  useEffect(() => { uniforms.uStepMin.value = stepMin; }, [stepMin, uniforms]);
  useEffect(() => { uniforms.uStepMax.value = stepMax; }, [stepMax, uniforms]);
  useEffect(() => { uniforms.uMaxDist.value = maxDist; }, [maxDist, uniforms]);
  useEffect(() => { uniforms.uExposure.value = exposure; }, [exposure, uniforms]);
  useEffect(() => { uniforms.uGamma.value = gamma; }, [gamma, uniforms]);
  useEffect(() => { uniforms.uGrainAmount.value = grainAmount; }, [grainAmount, uniforms]);
  useEffect(() => { uniforms.uVignette.value = vignette; }, [vignette, uniforms]);
  useEffect(() => { uniforms.uBgColor.value.fromArray(bgColor); }, [bgColor, uniforms]);

  useFrame((state) => {
    const dpr = gl.getPixelRatio();
    uniforms.uTime.value = state.clock.elapsedTime;
    uniforms.uResolution.value.set(size.width * dpr, size.height * dpr);
    const mx = 0.5 + state.pointer.x * 0.5;
    const my = 0.5 + state.pointer.y * 0.5;
    uniforms.uMouse.value.lerp(tmpV2.set(mx, my), pointerSmoothing);
  });

  return (
    <mesh frustumCulled={false} {...meshProps}>
      <planeGeometry args={[2, 2, 1, 1]} />
      <shaderMaterial
        vertexShader={vertexShader}
        fragmentShader={fragmentShader}
        uniforms={uniforms}
        depthTest={false}
        depthWrite={false}
        side={THREE.DoubleSide}
      />
    </mesh>
  );
}

export type VolumetricBeamsFullScreenProps = VolumetricBeamsShaderProps & {
  dpr?: CanvasProps["dpr"];
  gl?: CanvasProps["gl"];
  className?: string;
  title?: string;
  subtitle?: string;
  headingClassName?: string;
  subtitleClassName?: string;
};

export default function VolumetricBeamsFullScreen({
  dpr = [1, 2],
  gl = { antialias: true },
  className = "fixed inset-0 bg-black",
  title = "Background Shader",
  subtitle = "volumetric Beams",
  headingClassName = "",
  subtitleClassName = "",
  ...shaderProps
}: VolumetricBeamsFullScreenProps) {
  return (
    <div className={className}>
      <Canvas dpr={dpr} gl={gl} className="h-full w-full touch-none">
        <VolumetricBeamsShader {...shaderProps} />
      </Canvas>
      <div className="pointer-events-none absolute inset-x-0 top-4 z-10 flex justify-center sm:top-6 md:top-80">
        <div className="text-center">
          <h1
            className={[
              "select-none font-extrabold uppercase tracking-[0.25em]",
              "text-2xl sm:text-3xl md:text-5xl",
              "bg-gradient-to-r from-indigo-200/90 via-blue-300 to-indigo-200/90",
              "bg-clip-text text-transparent",
              "drop-shadow-[0_8px_32px_rgba(64,128,255,0.35)]",
              headingClassName,
            ].join(" ")}
          >
            {title}
          </h1>

          {subtitle && (
            <p
              className={[
                "mt-2 text-xs tracking-widest text-slate-200/70",
                "drop-shadow-[0_4px_16px_rgba(0,0,0,0.45)] sm:text-sm md:text-base",
                subtitleClassName,
              ].join(" ")}
            >
              {subtitle}
            </p>
          )}
        </div>
      </div>
    </div>
  );
}

export { VolumetricBeamsShader };
