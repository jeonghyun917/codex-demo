const root = document.querySelector("[data-aurora-core]");
if (root) {
    root.classList.add("is-ready", "has-metrics");
    const metrics = root.querySelectorAll("[data-aurora-metric]");
    for (let index = 0; index < metrics.length; index += 1) {
        metrics[index].classList.add("is-visible");
    }
}
