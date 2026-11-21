(() => {
    const SPOTLIGHT_R = 260;
    const hero = document.querySelector("[data-lithos-hero]");
    const layer = document.querySelector("[data-lithos-reveal]");
    const canvas = document.querySelector("[data-lithos-canvas]");
    const reveal = document.querySelector("[data-lithos-reveal-image]");

    if (!hero || !layer || !canvas || !reveal) {
        return;
    }

    const context = canvas.getContext("2d");
    if (!context) {
        return;
    }

    reveal.style.backgroundImage = `url("${layer.dataset.image}")`;

    const mouse = { x: -999, y: -999 };
    const smooth = { x: -999, y: -999 };
    let cursorPos = { x: -999, y: -999 };
    let rafRef = 0;

    const resizeCanvas = () => {
        canvas.width = window.innerWidth;
        canvas.height = window.innerHeight;
    };

    const handleMouseMove = (event) => {
        mouse.x = event.clientX;
        mouse.y = event.clientY;
    };

    const drawRevealMask = () => {
        context.clearRect(0, 0, canvas.width, canvas.height);
        const gradient = context.createRadialGradient(
            cursorPos.x,
            cursorPos.y,
            0,
            cursorPos.x,
            cursorPos.y,
            SPOTLIGHT_R
        );
        gradient.addColorStop(0, "rgba(255,255,255,1)");
        gradient.addColorStop(0.4, "rgba(255,255,255,1)");
        gradient.addColorStop(0.6, "rgba(255,255,255,.75)");
        gradient.addColorStop(0.75, "rgba(255,255,255,.4)");
        gradient.addColorStop(0.88, "rgba(255,255,255,.12)");
        gradient.addColorStop(1, "rgba(255,255,255,0)");

        context.fillStyle = gradient;
        context.beginPath();
        context.arc(cursorPos.x, cursorPos.y, SPOTLIGHT_R, 0, Math.PI * 2);
        context.fill();

        const mask = `url("${canvas.toDataURL()}")`;
        reveal.style.setProperty("mask-image", mask);
        reveal.style.setProperty("-webkit-mask-image", mask);
        reveal.style.setProperty("mask-size", "100% 100%");
        reveal.style.setProperty("-webkit-mask-size", "100% 100%");
    };

    const animate = () => {
        smooth.x += (mouse.x - smooth.x) * 0.1;
        smooth.y += (mouse.y - smooth.y) * 0.1;
        cursorPos = { x: smooth.x, y: smooth.y };
        drawRevealMask();
        rafRef = window.requestAnimationFrame(animate);
    };

    const destroy = () => {
        window.removeEventListener("mousemove", handleMouseMove);
        window.removeEventListener("resize", resizeCanvas);
        window.cancelAnimationFrame(rafRef);
    };

    resizeCanvas();
    window.addEventListener("mousemove", handleMouseMove, { passive: true });
    window.addEventListener("resize", resizeCanvas);
    window.addEventListener("pagehide", destroy, { once: true });
    rafRef = window.requestAnimationFrame(animate);
})();
