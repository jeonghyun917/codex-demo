import {
    normalizePointer,
    selectHomeMotionProfile,
    shouldContinueHomeMotion,
    staggerDelay
} from "./home-brik-motion.js";

const HOME_MOTION_WAKE_MS = 1200;
const root = document.documentElement;
const hero = document.querySelector("[data-home-hero]");
const canvas = document.querySelector("[data-home-canvas]");
const revealElements = document.querySelectorAll("[data-home-reveal]");
const environment = {
    reduceMotion: window.matchMedia("(prefers-reduced-motion: reduce)").matches,
    mobile: window.matchMedia("(max-width: 767px)").matches,
    cores: navigator.hardwareConcurrency || 4,
    devicePixelRatio: window.devicePixelRatio || 1
};
const profile = selectHomeMotionProfile(environment);
const canObserve = "IntersectionObserver" in window;
const canAnimate = typeof Element.prototype.animate === "function";
const context = canvas?.getContext("2d");

function showStaticContent() {
    root.classList.add("home-motion-ready", "home-motion-static");
}

if (!hero || !canvas || !context) {
    showStaticContent();
} else {
    startHomeMotion();
}

function startHomeMotion() {
    const state = {
        width: 0,
        height: 0,
        pixelRatio: profile.pixelRatio,
        pointerX: 0,
        pointerY: 0,
        targetX: 0,
        targetY: 0,
        energy: 0,
        targetEnergy: 0,
        heroVisible: false,
        frameId: 0,
        now: 0,
        wakeUntil: 0,
        neutralGradient: null,
        coralGradient: null,
        blueGradient: null,
        noise: new Float32Array(0)
    };

    function rebuildPaint(width, height) {
        const neutral = context.createLinearGradient(0, height, width, 0);
        neutral.addColorStop(0, "rgba(93, 95, 91, 0)");
        neutral.addColorStop(0.34, "rgba(93, 95, 91, 0.24)");
        neutral.addColorStop(0.72, "rgba(11, 11, 10, 0.12)");
        neutral.addColorStop(1, "rgba(93, 95, 91, 0)");
        state.neutralGradient = neutral;

        const coral = context.createLinearGradient(0, height * 0.8, width, height * 0.2);
        coral.addColorStop(0, "rgba(255, 79, 69, 0)");
        coral.addColorStop(0.38, "rgba(255, 79, 69, 0.72)");
        coral.addColorStop(0.68, "rgba(255, 61, 191, 0.54)");
        coral.addColorStop(1, "rgba(255, 61, 191, 0)");
        state.coralGradient = coral;

        const blue = context.createLinearGradient(0, height * 0.2, width, height * 0.8);
        blue.addColorStop(0, "rgba(119, 168, 255, 0)");
        blue.addColorStop(0.46, "rgba(119, 168, 255, 0.62)");
        blue.addColorStop(0.74, "rgba(217, 255, 87, 0.28)");
        blue.addColorStop(1, "rgba(217, 255, 87, 0)");
        state.blueGradient = blue;

        const noiseCount = profile.name === "low" ? 44 : 88;
        state.noise = new Float32Array(noiseCount * 2);
        for (let index = 0; index < state.noise.length; index += 2) {
            state.noise[index] = Math.random() * width;
            state.noise[index + 1] = Math.random() * height;
        }
    }

    function resizeCanvas() {
        const rect = hero.getBoundingClientRect();
        const width = Math.max(Math.round(rect.width), 1);
        const height = Math.max(Math.round(rect.height), 1);

        if (state.width === width && state.height === height) {
            return;
        }

        state.width = width;
        state.height = height;
        canvas.width = Math.round(width * state.pixelRatio);
        canvas.height = Math.round(height * state.pixelRatio);
        context.setTransform(state.pixelRatio, 0, 0, state.pixelRatio, 0, 0);
        rebuildPaint(width, height);
    }

    function ribbon(gradient, offset, amplitude, phase, lineWidth) {
        const width = state.width;
        const height = state.height;
        const pointerLift = state.pointerY * height * 0.08;
        const pointerPull = state.pointerX * width * 0.05;

        context.beginPath();
        context.moveTo(-width * 0.08, height * offset + pointerLift);
        context.bezierCurveTo(
            width * 0.22 + pointerPull,
            height * (offset - amplitude) + Math.sin(phase) * 18,
            width * 0.68 + pointerPull,
            height * (offset + amplitude) + Math.cos(phase) * 20,
            width * 1.08,
            height * (offset - amplitude * 0.35) - pointerLift
        );
        context.strokeStyle = gradient;
        context.lineWidth = lineWidth;
        context.lineCap = "round";
        context.stroke();
    }

    function drawGrid() {
        const spacing = profile.name === "low" ? 72 : 52;
        context.save();
        context.strokeStyle = "rgba(93, 95, 91, 0.075)";
        context.lineWidth = 1;
        context.beginPath();
        for (let x = spacing; x < state.width; x += spacing) {
            context.moveTo(x, 0);
            context.lineTo(x, state.height);
        }
        for (let y = spacing; y < state.height; y += spacing) {
            context.moveTo(0, y);
            context.lineTo(state.width, y);
        }
        context.stroke();

        context.fillStyle = "rgba(11, 11, 10, 0.09)";
        for (let index = 0; index < state.noise.length; index += 2) {
            context.fillRect(state.noise[index], state.noise[index + 1], 1, 1);
        }
        context.restore();
    }

    function drawFrame(time) {
        const phase = profile.animate ? time * 0.00018 : 0;
        state.pointerX += (state.targetX - state.pointerX) * 0.18;
        state.pointerY += (state.targetY - state.pointerY) * 0.18;
        state.energy += (state.targetEnergy - state.energy) * 0.18;
        if (state.targetEnergy > 0) {
            state.targetEnergy *= 0.9;
        }

        context.clearRect(0, 0, state.width, state.height);
        drawGrid();

        context.save();
        context.filter = "blur(24px)";
        context.globalAlpha = 0.72;
        ribbon(state.neutralGradient, 0.42, 0.13, phase, Math.max(state.height * 0.15, 74));
        ribbon(state.neutralGradient, 0.66, 0.1, phase + 2.1, Math.max(state.height * 0.1, 52));

        context.globalAlpha = state.energy * 0.56;
        ribbon(state.coralGradient, 0.47, 0.16, phase + 0.7, Math.max(state.height * 0.12, 62));
        ribbon(state.blueGradient, 0.61, 0.13, phase + 2.8, Math.max(state.height * 0.09, 48));
        context.restore();
    }

    function canRun() {
        return profile.animate && state.heroVisible && !document.hidden;
    }

    function tick(time) {
        state.frameId = 0;
        if (!canRun()) {
            return;
        }

        state.now = time;
        drawFrame(time);
        if (shouldContinueHomeMotion(state)) {
            state.frameId = requestAnimationFrame(tick);
        }
    }

    function wakeMotion() {
        state.wakeUntil = performance.now() + HOME_MOTION_WAKE_MS;
        if (canRun() && !state.frameId) {
            state.frameId = requestAnimationFrame(tick);
        }
    }

    function sleepMotion() {
        if (state.frameId) {
            cancelAnimationFrame(state.frameId);
            state.frameId = 0;
        }
    }

    resizeCanvas();
    drawFrame(0);

    if (!profile.animate) {
        showStaticContent();
        return;
    }

    if (!canObserve || !canAnimate) {
        showStaticContent();
        return;
    }

    const revealObserver = new IntersectionObserver((entries, observer) => {
        for (const entry of entries) {
            if (!entry.isIntersecting) {
                continue;
            }
            const element = entry.target;
            const index = Number(element.dataset.homeRevealIndex);
            element.animate(
                [
                    { opacity: 0, transform: "translateY(18px)" },
                    { opacity: 1, transform: "translateY(0)" }
                ],
                {
                    duration: 420,
                    delay: staggerDelay(index),
                    easing: "cubic-bezier(0.22, 1, 0.36, 1)",
                    fill: "both"
                }
            );
            observer.unobserve(element);
        }
    }, { threshold: 0.12 });

    revealElements.forEach((element, index) => {
        element.dataset.homeRevealIndex = String(index);
        revealObserver.observe(element);
    });

    const heroObserver = new IntersectionObserver((entries) => {
        state.heroVisible = entries[0]?.isIntersecting ?? false;
        if (state.heroVisible) {
            wakeMotion();
        } else {
            sleepMotion();
        }
    }, { threshold: 0.01 });
    heroObserver.observe(hero);

    document.addEventListener("visibilitychange", () => {
        if (document.hidden) {
            sleepMotion();
        } else {
            wakeMotion();
        }
    });

    if ("ResizeObserver" in window) {
        const resizeObserver = new ResizeObserver(() => {
            resizeCanvas();
            wakeMotion();
        });
        resizeObserver.observe(hero);
    } else {
        window.addEventListener("resize", () => {
            resizeCanvas();
            wakeMotion();
        }, { passive: true });
    }

    if (profile.pointerEnabled) {
        hero.addEventListener("pointermove", (event) => {
            const pointer = normalizePointer(
                event.clientX,
                event.clientY,
                hero.getBoundingClientRect()
            );
            state.targetX = pointer.x;
            state.targetY = pointer.y;
            state.targetEnergy = 1;
            wakeMotion();
        }, { passive: true });

        hero.addEventListener("pointerleave", () => {
            state.targetX = 0;
            state.targetY = 0;
            state.targetEnergy = 0;
            wakeMotion();
        }, { passive: true });
    }

    root.classList.add("home-motion-ready");
}
