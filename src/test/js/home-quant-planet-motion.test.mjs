import assert from "node:assert/strict";
import test from "node:test";
import {
    REFERENCE_HERO
} from "../../main/resources/static/js/home-quant-planet-composition.js";
import {
    clampUnit,
    createDomMotion,
    createHeroIntroState,
    createHeroPose,
    createPageLifecycle,
    normalizePointer,
    resolveLocalTestOverrides,
    selectPlanetQualityProfile,
    shouldRenderFrame
} from "../../main/resources/static/js/home-quant-planet-motion.js";

test("selects bounded planet quality profiles", () => {
    assert.equal(selectPlanetQualityProfile({
        reduceMotion: true, webgl: true, mobile: false, cores: 16, dpr: 3
    }).name, "static");
    assert.deepEqual(selectPlanetQualityProfile({
        reduceMotion: false, webgl: true, mobile: true, cores: 8, dpr: 3
    }), {
        name: "balanced", webgl: true, pixelRatio: 1.25,
        pointerEnabled: true, shadows: false, outlineLevel: 1,
        foliageCount: 18, cloudCount: 5, planetScale: 0.82
    });
    assert.equal(selectPlanetQualityProfile({
        reduceMotion: false, webgl: true, mobile: false, cores: 16, dpr: 3
    }).pixelRatio, 1.5);
});

test("orders loader, world, title, and action before becoming ready", () => {
    const loader = createHeroIntroState({
        elapsedMs: 400,
        sceneReady: true,
        reduceMotion: false
    });
    const world = createHeroIntroState({
        elapsedMs: 900,
        sceneReady: true,
        reduceMotion: false
    });
    const title = createHeroIntroState({
        elapsedMs: 1400,
        sceneReady: true,
        reduceMotion: false
    });
    const action = createHeroIntroState({
        elapsedMs: 1800,
        sceneReady: true,
        reduceMotion: false
    });

    assert.ok(loader.loader > 0);
    assert.deepEqual(
        [loader.world, loader.title, loader.action],
        [0, 0, 0]
    );
    assert.ok(world.world > 0);
    assert.deepEqual([world.title, world.action], [0, 0]);
    assert.ok(title.title > 0);
    assert.equal(title.action, 0);
    assert.ok(action.action > 0);
    assert.equal(action.ready, false);
});

test("forces the complete hero ready at the 3999ms hard deadline", () => {
    const beforeDeadline = createHeroIntroState({
        elapsedMs: REFERENCE_HERO.introDeadlineMs - 1,
        sceneReady: false,
        reduceMotion: false
    });
    const deadline = createHeroIntroState({
        elapsedMs: REFERENCE_HERO.introDeadlineMs,
        sceneReady: false,
        reduceMotion: false
    });

    assert.equal(beforeDeadline.ready, false);
    assert.deepEqual(deadline, {
        phase: "ready",
        loader: 1,
        world: 1,
        title: 1,
        action: 1,
        ready: true
    });
});

test("reduced motion resolves directly to the final static hero", () => {
    assert.deepEqual(createHeroIntroState({
        elapsedMs: 0,
        sceneReady: false,
        reduceMotion: true
    }), {
        phase: "ready",
        loader: 1,
        world: 1,
        title: 1,
        action: 1,
        ready: true
    });
});

test("completes one reference world revolution in 36 seconds", () => {
    const start = createHeroPose({
        elapsedMs: 0,
        worldAngle: 0,
        pointer: { x: 0, y: 0 },
        titleProgress: 1,
        reduceMotion: false
    });
    const end = createHeroPose({
        elapsedMs: REFERENCE_HERO.worldRevolutionMs,
        worldAngle: 0,
        pointer: { x: 0, y: 0 },
        titleProgress: 1,
        reduceMotion: false
    });
    assert.ok(Math.abs(
        (end.worldAngle - start.worldAngle) - Math.PI * 2
    ) < 1e-9);
});

test("rotates a quarter turn after nine seconds", () => {
    const pose = createHeroPose({
        elapsedMs: 9000,
        worldAngle: .25
    });

    assert.equal(pose.worldAngle, .25 + Math.PI / 2);
});

test("advances autonomous planet yaw independently of pointer", () => {
    const left = createHeroPose({
        elapsedMs: 9000,
        worldAngle: .25,
        pointer: { x: -1, y: .8 },
        titleProgress: .7
    });
    const right = createHeroPose({
        elapsedMs: 9000,
        worldAngle: .25,
        pointer: { x: 1, y: -.8 },
        titleProgress: .7
    });

    assert.equal(left.worldAngle, right.worldAngle);
    assert.deepEqual(left.pointer, { x: -1, y: .8 });
    assert.deepEqual(right.pointer, { x: 1, y: -.8 });
    assert.equal(left.titleProgress, .7);
    assert.equal(
        left.worldAngle,
        .25 + Math.PI * 2 * 9000 / REFERENCE_HERO.worldRevolutionMs
    );
});

test("reduced motion freezes both autonomous and pointer targets", () => {
    assert.deepEqual(createHeroPose({
        elapsedMs: 9000,
        worldAngle: .4,
        pointer: { x: 1, y: -1 },
        titleProgress: .3,
        reduceMotion: true
    }), {
        worldAngle: .4,
        pointer: { x: 0, y: 0 },
        titleProgress: 1,
        elapsedMs: 9000,
        reduceMotion: true
    });
});

test("normalizes pointer into a finite unit range", () => {
    assert.deepEqual(
        normalizePointer(50, 25, { left: 0, top: 0, width: 100, height: 100 }),
        { x: 0, y: -0.5 }
    );
    assert.equal(clampUnit(Number.NaN), 0);
});

test("accepts deterministic fallback overrides only on loopback", () => {
    assert.deepEqual(resolveLocalTestOverrides({
        hostname: "127.0.0.1", search: "?render=static&motion=reduce"
    }), { forceStatic: true, forceReduced: true });
    assert.deepEqual(resolveLocalTestOverrides({
        hostname: "[::1]", search: "?render=static&motion=reduce"
    }), { forceStatic: true, forceReduced: true });
    assert.deepEqual(resolveLocalTestOverrides({
        hostname: "kingyurina.example", search: "?render=static&motion=reduce"
    }), { forceStatic: false, forceReduced: false });
});

test("renders only when the planet can contribute a visible frame", () => {
    assert.equal(shouldRenderFrame({
        visible: true, hidden: false, webgl: true, reducedMotion: false, dirty: true
    }), true);
    assert.equal(shouldRenderFrame({
        visible: false, hidden: false, webgl: true, reducedMotion: false, dirty: true
    }), false);
    assert.equal(shouldRenderFrame({
        visible: true, hidden: false, webgl: true, reducedMotion: true, dirty: false
    }), false);
    assert.equal(shouldRenderFrame({
        visible: true, hidden: false, webgl: true, dirty: false, ambient: true
    }), true);
});

test("keeps balanced non-reduced scenes eligible for continuous frames", () => {
    const balanced = selectPlanetQualityProfile({
        reduceMotion: false, webgl: true, mobile: true, cores: 8, dpr: 2
    });
    assert.equal(shouldRenderFrame({
        visible: true,
        hidden: false,
        webgl: balanced.webgl,
        reducedMotion: false,
        dirty: false,
        profile: balanced
    }), true);
    assert.equal(shouldRenderFrame({
        visible: true,
        hidden: false,
        webgl: false,
        reducedMotion: true,
        dirty: false,
        profile: { name: "static", webgl: false }
    }), false);
});

test("pauses and resumes one animation loop across the back-forward cache", () => {
    let nextFrameId = 0;
    const queuedFrames = new Map();
    const calls = { frame: 0, pause: 0, resume: 0, destroy: 0 };
    const lifecycle = createPageLifecycle({
        requestFrame(callback) {
            const id = ++nextFrameId;
            queuedFrames.set(id, callback);
            return id;
        },
        cancelFrame(id) {
            queuedFrames.delete(id);
        },
        onFrame() {
            calls.frame += 1;
        },
        onPause() {
            calls.pause += 1;
        },
        onResume() {
            calls.resume += 1;
        },
        onDestroy() {
            calls.destroy += 1;
        }
    });

    lifecycle.start();
    assert.equal(queuedFrames.size, 1);

    lifecycle.handlePageHide({ persisted: true });
    lifecycle.handlePageHide({ persisted: true });
    assert.deepEqual(calls, { frame: 0, pause: 1, resume: 0, destroy: 0 });
    assert.equal(queuedFrames.size, 0);

    lifecycle.handlePageShow({ persisted: true });
    lifecycle.handlePageShow({ persisted: true });
    assert.deepEqual(calls, { frame: 0, pause: 1, resume: 1, destroy: 0 });
    assert.equal(queuedFrames.size, 1);

    const [frameId, frameCallback] = queuedFrames.entries().next().value;
    queuedFrames.delete(frameId);
    frameCallback(16);
    assert.equal(calls.frame, 1);
    assert.equal(queuedFrames.size, 1);

    lifecycle.handlePageHide({ persisted: false });
    lifecycle.destroy();
    lifecycle.handlePageShow({ persisted: true });
    assert.deepEqual(calls, { frame: 1, pause: 1, resume: 1, destroy: 1 });
    assert.equal(queuedFrames.size, 0);
});

test("does not schedule an animation frame when a static lifecycle resumes from BFCache", () => {
    const queuedFrames = new Map();
    let nextFrameId = 0;
    const calls = { pause: 0, resume: 0, destroy: 0 };
    const lifecycle = createPageLifecycle({
        requestFrame(callback) {
            const id = ++nextFrameId;
            queuedFrames.set(id, callback);
            return id;
        },
        cancelFrame(id) {
            queuedFrames.delete(id);
        },
        onPause() { calls.pause += 1; },
        onResume() { calls.resume += 1; },
        onDestroy() { calls.destroy += 1; }
    });

    lifecycle.handlePageHide({ persisted: true });
    lifecycle.handlePageShow({ persisted: true });

    assert.deepEqual(calls, { pause: 1, resume: 1, destroy: 0 });
    assert.equal(queuedFrames.size, 0);

    lifecycle.handlePageHide({ persisted: false });
    assert.equal(calls.destroy, 1);
});

test("cancels every DOM Motion animation exactly once on destroy", () => {
    const reveal = {
        style: {
            opacity: "0",
            transform: "translateY(16px)"
        }
    };
    let cancelCalls = 0;
    const motion = createDomMotion({
        MotionAPI: {
            animate() {
                return {
                    cancel() {
                        cancelCalls += 1;
                    }
                };
            }
        },
        root: {
            querySelectorAll() {
                return [reveal];
            }
        },
        reduceMotion: false
    });

    assert.deepEqual(reveal.style, {
        opacity: "1",
        transform: "none"
    });
    motion.reveal(reveal);
    motion.destroy();
    motion.destroy();

    assert.equal(cancelCalls, 1);
});
