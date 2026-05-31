(() => {
    const tiles = Array.from(document.querySelectorAll(".heatmap-tile[data-symbol]"));
    const industryLabels = Array.from(document.querySelectorAll("[data-industry-label]"));
    const tooltip = document.querySelector("[data-heatmap-tooltip]");

    if (tiles.length === 0) {
        return;
    }

    const MAX_SYMBOL_FONT_SIZE = 38;

    const firstCharacter = (value) => Array.from(String(value || "-"))[0] || "-";

    const setTileState = (tile, text, showChange, symbolSize) => {
        const symbol = tile.querySelector("[data-tile-symbol]");
        const change = tile.querySelector("[data-tile-change]");
        if (!symbol || !change) {
            return;
        }

        symbol.textContent = text;
        symbol.style.fontSize = `${symbolSize}px`;
        change.hidden = !showChange;
        change.style.fontSize = `${changeSize(symbolSize)}px`;
    };

    const textFits = (tile, showChange) => {
        const symbol = tile.querySelector("[data-tile-symbol]");
        const change = tile.querySelector("[data-tile-change]");
        if (!symbol || !change) {
            return true;
        }

        const availableWidth = Math.max(tile.clientWidth - 8, 1);
        const availableHeight = Math.max(tile.clientHeight - 8, 1);
        const gap = showChange ? 4 : 0;
        const contentHeight = symbol.offsetHeight + (showChange ? change.offsetHeight + gap : 0);

        return symbol.scrollWidth <= availableWidth
                && (!showChange || change.scrollWidth <= availableWidth)
                && contentHeight <= availableHeight;
    };

    const changeSize = (symbolSize) => Math.min(16, Math.max(8, symbolSize * 0.34));

    const maxSymbolSize = (tile, text, showChange) => {
        const availableWidth = Math.max(tile.clientWidth - 8, 1);
        const availableHeight = Math.max(tile.clientHeight - 8, 1);
        const heightLimit = availableHeight * (showChange ? 0.68 : 0.9);
        const widthLimit = availableWidth * 1.55;
        let low = 6;
        let high = Math.min(MAX_SYMBOL_FONT_SIZE, heightLimit, widthLimit);
        let best = 0;

        for (let index = 0; index < 10; index += 1) {
            const middle = (low + high) / 2;
            setTileState(tile, text, showChange, middle);
            if (textFits(tile, showChange)) {
                best = middle;
                low = middle;
            } else {
                high = middle;
            }
        }

        return best;
    };

    const applyBestFit = (tile, text, showChange, initialOnly) => {
        const size = maxSymbolSize(tile, text, showChange);
        setTileState(tile, text, showChange, Math.min(MAX_SYMBOL_FONT_SIZE, Math.max(6, Math.floor(size))));
        tile.classList.toggle("hide-change", !showChange);
        tile.classList.toggle("initial-only", initialOnly);
        return size;
    };

    const fitTileText = (tile) => {
        const symbol = tile.querySelector("[data-tile-symbol]");
        const change = tile.querySelector("[data-tile-change]");
        if (!symbol || !change) {
            return;
        }

        const fullSymbol = tile.dataset.symbol || symbol.textContent.trim();
        const firstSymbol = firstCharacter(fullSymbol);
        const canShowChange = tile.clientWidth >= 30 && tile.clientHeight >= 34;
        tile.classList.remove("initial-only", "hide-change");

        const fullWithChange = canShowChange ? maxSymbolSize(tile, fullSymbol, true) : 0;
        if (fullWithChange >= 9) {
            applyBestFit(tile, fullSymbol, true, false);
            return;
        }

        const fullWithoutChange = maxSymbolSize(tile, fullSymbol, false);
        const initialWithChange = canShowChange ? maxSymbolSize(tile, firstSymbol, true) : 0;
        if (fullWithoutChange >= 11 && fullWithoutChange >= initialWithChange * 0.82) {
            applyBestFit(tile, fullSymbol, false, false);
            return;
        }

        if (initialWithChange >= 9) {
            applyBestFit(tile, firstSymbol, true, true);
            return;
        }

        applyBestFit(tile, firstSymbol, false, true);
    };

    const fitIndustryLabel = (label) => {
        const heading = label.closest("h3");
        if (!heading) {
            return;
        }

        const fullText = label.dataset.fullText || label.textContent.trim();
        label.dataset.fullText = fullText;
        label.textContent = fullText;
        label.style.transform = "";
        label.style.fontSize = "";

        const availableWidth = Math.max(heading.clientWidth - 8, 1);
        let low = 5;
        let high = 10;
        let best = low;

        for (let index = 0; index < 10; index += 1) {
            const middle = (low + high) / 2;
            label.style.fontSize = `${middle}px`;
            if (label.scrollWidth <= availableWidth) {
                best = middle;
                low = middle;
            } else {
                high = middle;
            }
        }

        label.style.fontSize = `${best}px`;
        if (label.scrollWidth > availableWidth) {
            label.style.transform = `scaleX(${availableWidth / label.scrollWidth})`;
        }
    };

    let resizeFrame = 0;
    const fitAllTiles = () => {
        window.cancelAnimationFrame(resizeFrame);
        resizeFrame = window.requestAnimationFrame(() => {
            tiles.forEach(fitTileText);
            industryLabels.forEach(fitIndustryLabel);
        });
    };

    fitAllTiles();

    if ("ResizeObserver" in window) {
        const observer = new ResizeObserver(fitAllTiles);
        tiles.forEach((tile) => observer.observe(tile));
        industryLabels.forEach((label) => {
            const heading = label.closest("h3");
            if (heading) {
                observer.observe(heading);
            }
        });
    } else {
        window.addEventListener("resize", fitAllTiles);
    }

    if (!tooltip) {
        return;
    }

    let activeTile = null;

    const metricRows = [
        ["현재가", "price"],
        ["등락률", "change"],
        ["시가총액", "marketCap"],
        ["전일종가", "previousClose"],
        ["시가", "open"],
        ["일중범위", "dayRange"],
        ["PER", "per"],
        ["PBR", "pbr"],
        ["ROE", "roe"]
    ];

    const escapeText = (value) => String(value || "-").replace(/[&<>"']/g, (character) => ({
        "&": "&amp;",
        "<": "&lt;",
        ">": "&gt;",
        "\"": "&quot;",
        "'": "&#39;"
    }[character]));

    const value = (tile, key) => tile.dataset[key] || "-";

    const changeClass = (change) => {
        if (!change || change === "-") {
            return "neutral";
        }
        if (change.trim().startsWith("-")) {
            return "negative";
        }
        if (change.trim().startsWith("0")) {
            return "neutral";
        }
        return "positive";
    };

    const render = (tile) => {
        const change = value(tile, "change");
        tooltip.innerHTML = `
            <div class="heatmap-tooltip__top">
                <div>
                    <strong>${escapeText(value(tile, "symbol"))}</strong>
                    <span>${escapeText(value(tile, "name"))}</span>
                </div>
                <b class="${changeClass(change)}">${escapeText(change)}</b>
            </div>
            <p>${escapeText(value(tile, "sector"))} · ${escapeText(value(tile, "industry"))}</p>
            <div class="heatmap-tooltip__metrics">
                ${metricRows.map(([label, key]) => `
                    <div>
                        <span>${escapeText(label)}</span>
                        <strong>${escapeText(value(tile, key))}</strong>
                    </div>
                `).join("")}
            </div>
            <small>클릭하면 종목 정보 페이지로 이동합니다.</small>
        `;
    };

    const position = (event) => {
        const margin = 16;
        const gap = 18;
        const rect = tooltip.getBoundingClientRect();
        let x = event.clientX + gap;
        let y = event.clientY + gap;

        if (x + rect.width + margin > window.innerWidth) {
            x = event.clientX - rect.width - gap;
        }
        if (y + rect.height + margin > window.innerHeight) {
            y = event.clientY - rect.height - gap;
        }

        x = Math.max(margin, Math.min(x, window.innerWidth - rect.width - margin));
        y = Math.max(margin, Math.min(y, window.innerHeight - rect.height - margin));
        tooltip.style.left = `${x}px`;
        tooltip.style.top = `${y}px`;
    };

    const show = (tile, event) => {
        activeTile = tile;
        render(tile);
        tooltip.classList.add("visible");
        tooltip.setAttribute("aria-hidden", "false");
        if (event) {
            position(event);
        }
    };

    const hide = () => {
        activeTile = null;
        tooltip.classList.remove("visible");
        tooltip.setAttribute("aria-hidden", "true");
    };

    tiles.forEach((tile) => {
        tile.addEventListener("mouseenter", (event) => show(tile, event));
        tile.addEventListener("mousemove", (event) => {
            if (activeTile === tile) {
                position(event);
            }
        });
        tile.addEventListener("mouseleave", hide);
        tile.addEventListener("focus", () => {
            const rect = tile.getBoundingClientRect();
            show(tile, {
                clientX: rect.left + rect.width / 2,
                clientY: rect.top + rect.height / 2
            });
        });
        tile.addEventListener("blur", hide);
    });

    document.addEventListener("keydown", (event) => {
        if (event.key === "Escape") {
            hide();
        }
    });
})();
