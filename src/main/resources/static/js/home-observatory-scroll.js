function createDisabledController() {
    return {
        isEnabled: false,
        raf() {},
        stop() {},
        start() {},
        destroy() {}
    };
}

export function createScrollController({ LenisClass, reduceMotion, onScroll } = {}) {
    if (reduceMotion || !LenisClass) {
        return createDisabledController();
    }

    const lenis = new LenisClass({
        autoRaf: false,
        anchors: true,
        lerp: 0.085,
        smoothWheel: true,
        syncTouch: false,
        wheelMultiplier: 0.9
    });
    lenis.on("scroll", (state) => onScroll?.(state));

    return {
        isEnabled: true,
        raf(time) { lenis.raf(time); },
        stop() { lenis.stop(); },
        start() { lenis.start(); },
        destroy() { lenis.destroy(); }
    };
}
