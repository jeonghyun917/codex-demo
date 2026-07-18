const DEFAULT_SPRING = Object.freeze({
    stiffness: 90,
    damping: 20,
    mass: 1
});

export function createSpring(initialValue = 0) {
    return {
        value: initialValue,
        target: initialValue,
        velocity: 0
    };
}

export function stepSpring(spring, deltaSeconds, configuration = DEFAULT_SPRING) {
    const delta = Math.min(Math.max(deltaSeconds, 0), 1 / 20);
    const displacement = spring.value - spring.target;
    const acceleration = (
        -configuration.stiffness * displacement
        -configuration.damping * spring.velocity
    ) / configuration.mass;
    spring.velocity += acceleration * delta;
    spring.value += spring.velocity * delta;
    if (Math.abs(spring.target - spring.value) < 0.00001
        && Math.abs(spring.velocity) < 0.00001) {
        spring.value = spring.target;
        spring.velocity = 0;
    }
    return spring.value;
}

export function normalizedScrollProgress(rectTop, stageHeight, viewportHeight) {
    const travel = Math.max(1, stageHeight - viewportHeight);
    return clamp(-rectTop / travel, 0, 1);
}

export function smoothstep(edge0, edge1, value) {
    const amount = clamp((value - edge0) / Math.max(0.0001, edge1 - edge0), 0, 1);
    return amount * amount * (3 - 2 * amount);
}

export function clamp(value, minimum, maximum) {
    return Math.min(maximum, Math.max(minimum, value));
}
