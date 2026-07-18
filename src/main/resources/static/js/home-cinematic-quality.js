export function selectQualityProfile(environment) {
    if (environment.reduceMotion) {
        return {
            name: "reduced",
            pixelRatio: 1,
            shadows: false,
            particleCount: 120,
            segments: 24
        };
    }
    if (environment.mobile || environment.cores <= 4 || environment.memory <= 4) {
        return {
            name: "low",
            pixelRatio: 1,
            shadows: false,
            particleCount: 320,
            segments: 36
        };
    }
    if (environment.cores <= 8 || environment.memory <= 8) {
        return {
            name: "medium",
            pixelRatio: 1.35,
            shadows: true,
            particleCount: 720,
            segments: 48
        };
    }
    return {
        name: "high",
        pixelRatio: 1.65,
        shadows: true,
        particleCount: 1200,
        segments: 64
    };
}

export function readEnvironment() {
    return {
        reduceMotion: window.matchMedia("(prefers-reduced-motion: reduce)").matches,
        mobile: window.matchMedia("(max-width: 760px)").matches,
        cores: navigator.hardwareConcurrency || 4,
        memory: navigator.deviceMemory || 4
    };
}
