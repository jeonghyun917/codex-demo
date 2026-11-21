(() => {
    const hero = document.querySelector("[data-axon-hero]");
    const video = document.querySelector("[data-axon-video]");
    const status = document.querySelector("[data-video-status]");
    if (!hero || !video) {
        return;
    }

    const reducedMotion = window.matchMedia(
        "(prefers-reduced-motion: reduce)"
    );

    const updatePlayback = () => {
        if (reducedMotion.matches || document.hidden) {
            video.pause();
            return;
        }
        const playAttempt = video.play();
        if (playAttempt && typeof playAttempt.catch === "function") {
            playAttempt.catch(() => {
                hero.classList.add("is-video-unavailable");
            });
        }
    };

    const markReady = () => {
        hero.classList.add("is-video-ready");
        hero.classList.remove("is-video-unavailable");
        if (status) {
            status.textContent = "";
        }
        updatePlayback();
    };

    video.addEventListener("canplay", markReady, { once: true });
    video.addEventListener("error", () => {
        hero.classList.add("is-video-unavailable");
        if (status) {
            status.textContent = "Background video unavailable";
        }
    });
    document.addEventListener("visibilitychange", updatePlayback);
    reducedMotion.addEventListener?.("change", updatePlayback);

    if (video.readyState >= HTMLMediaElement.HAVE_FUTURE_DATA) {
        markReady();
    } else {
        updatePlayback();
    }
})();
