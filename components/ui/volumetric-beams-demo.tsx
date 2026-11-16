"use client";

import VolumetricBeamsFullScreen from "@/components/ui/volumetric-beams";

export default function VolumetricBeamsDemo() {
  return (
    <VolumetricBeamsFullScreen
      className="fixed inset-0 bg-black"
      dpr={[1, 1.75]}
      speed={2.95}
      autoRotateSpeed={0.035}
      mouseInfluence={0.35}
      pointerSmoothing={0.28}
      cameraRadius={4.8}
      fov={1.25}
      beamCount={5}
      beamHalfAngle={0.065}
      beamEdgeSoft={0.045}
      beamRotation={0.7}
      twistDepth={0.95}
      density={0.95}
      falloff={0.15}
      anisotropy={0.86}
      lightIntensity={1.4}
      lightColor={[0.54, 0.74, 1.0]}
      stripeFreq={800.0}
      stripeAmp={0.07}
      stripeSharp={0.08}
      stripeSpeed={0.102}
      stripeJitter={0.91}
      volSteps={190}
      stepMin={0.05}
      stepMax={0.1}
      maxDist={3.0}
      bgColor={[0.04, 0.035, 0.06]}
      tint={[0.55, 0.38, 0.95]}
      grainAmount={0.005}
      vignette={0.95}
      exposure={0.5}
      gamma={2.0}
    />
  );
}
