export function selectAuroraQuality(environment) {
    if (environment.reduceMotion) {
        return {
            name: "reduced",
            pixelRatio: 1,
            shaderQuality: 0.35,
            pointerEnabled: false,
            ambientSpeed: 0
        };
    }
    if (environment.mobile || environment.cores <= 4 || environment.memory <= 4) {
        return {
            name: "low",
            pixelRatio: 1,
            shaderQuality: 0.5,
            pointerEnabled: false,
            ambientSpeed: 0.55
        };
    }
    if (environment.cores <= 8 || environment.memory <= 8) {
        return {
            name: "medium",
            pixelRatio: Math.min(environment.devicePixelRatio, 1.4),
            shaderQuality: 0.75,
            pointerEnabled: true,
            ambientSpeed: 0.8
        };
    }
    return {
        name: "high",
        pixelRatio: Math.min(environment.devicePixelRatio, 1.75),
        shaderQuality: 1,
        pointerEnabled: true,
        ambientSpeed: 1
    };
}

export function readEnvironment() {
    return {
        reduceMotion: window.matchMedia("(prefers-reduced-motion: reduce)").matches,
        mobile: window.matchMedia("(max-width: 900px), (pointer: coarse)").matches,
        cores: navigator.hardwareConcurrency || 8,
        memory: navigator.deviceMemory || 8,
        devicePixelRatio: window.devicePixelRatio || 1
    };
}
