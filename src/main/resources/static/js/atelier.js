(() => {
    const storageKeys = {
        watchlist: "ky.atelier.watchlist",
        recent: "ky.atelier.recent",
        compare: "ky.atelier.compare",
        weights: "ky.atelier.weights",
        reports: "ky.atelier.reports"
    };

    const factorLabels = [
        ["valuation", "Valuation"],
        ["quality", "Quality"],
        ["growth", "Growth"],
        ["stability", "Stability"],
        ["earnings", "Earnings"],
        ["analyst", "Analyst"],
        ["news", "News"],
        ["momentum", "Momentum"],
        ["risk", "Risk"]
    ];

    const defaultWeights = {
        valuation: 12,
        quality: 14,
        growth: 12,
        stability: 10,
        earnings: 11,
        analyst: 10,
        news: 8,
        momentum: 15,
        risk: 8
    };

    const rawRows = Array.isArray(window.KY_ATELIER_ROWS) ? window.KY_ATELIER_ROWS : [];
    const stocks = rawRows
        .map(normalizeStock)
        .filter((stock) => stock.symbol)
        .sort((left, right) => (right.marketCapNumber || 0) - (left.marketCapNumber || 0));
    const stockMap = new Map(stocks.map((stock) => [stock.symbol, stock]));

    const state = {
        watchlist: loadJson(storageKeys.watchlist, []),
        recent: loadJson(storageKeys.recent, []),
        compare: loadJson(storageKeys.compare, []),
        weights: { ...defaultWeights, ...loadJson(storageKeys.weights, {}) },
        reports: loadJson(storageKeys.reports, []),
        screenRows: [],
        selectedReportId: null
    };

    const el = {
        symbolInput: document.querySelector("#atelier-symbol-input"),
        datalist: document.querySelector("#atelier-symbols"),
        watchCount: document.querySelector("[data-watch-count]"),
        recentCount: document.querySelector("[data-recent-count]"),
        screenCount: document.querySelector("[data-screen-count]"),
        weightedAverage: document.querySelector("[data-weighted-average]"),
        watchList: document.querySelector("[data-watch-list]"),
        watchEmpty: document.querySelector("[data-watch-empty]"),
        recentList: document.querySelector("[data-recent-list]"),
        recentEmpty: document.querySelector("[data-recent-empty]"),
        screenTable: document.querySelector("[data-screen-table]"),
        compareChips: document.querySelector("[data-compare-chips]"),
        compareTable: document.querySelector("[data-compare-table]"),
        compareEmpty: document.querySelector("[data-compare-empty]"),
        weightList: document.querySelector("[data-weight-list]"),
        reportList: document.querySelector("[data-report-list]"),
        reportPreview: document.querySelector("[data-report-preview]"),
        filterSearch: document.querySelector("[data-filter-search]"),
        filterSector: document.querySelector("[data-filter-sector]"),
        filterSignal: document.querySelector("[data-filter-signal]"),
        filterPer: document.querySelector("[data-filter-per]"),
        filterRoe: document.querySelector("[data-filter-roe]")
    };

    if (!document.querySelector(".atelier-page")) {
        return;
    }

    seedDatalist();
    seedSectors();
    bindEvents();
    renderAll();

    function normalizeStock(row) {
        const components = {
            valuation: numberOrNull(row.valuationScore),
            quality: numberOrNull(row.qualityScore),
            growth: numberOrNull(row.growthScore),
            stability: numberOrNull(row.stabilityScore),
            earnings: numberOrNull(row.earningsScore),
            analyst: numberOrNull(row.analystScore),
            news: numberOrNull(row.newsScore),
            momentum: numberOrNull(row.momentumScore),
            risk: numberOrNull(row.riskScore)
        };
        const signal = numberOrNull(row.signalValue) ?? parseNumber(row.signalScore);
        const change = parseNumber(row.change);
        return {
            rank: row.rank || "-",
            symbol: String(row.symbol || "").toUpperCase(),
            name: row.name || row.symbol || "-",
            logoUrl: row.logoUrl || "",
            logoInitial: row.logoInitial || String(row.symbol || "?").slice(0, 1),
            sector: row.sector || "-",
            price: row.price || "-",
            change: row.change || "-",
            marketCap: row.marketCap || "-",
            per: row.per || "-",
            pbr: row.pbr || "-",
            roe: row.roe || "-",
            signalScore: signal,
            signalTone: row.signalTone || tone(signal),
            positive: change != null && change >= 0,
            perNumber: parseNumber(row.per),
            pbrNumber: parseNumber(row.pbr),
            roeNumber: parseNumber(row.roe),
            changeNumber: change,
            marketCapNumber: parseNumber(row.marketCap),
            components
        };
    }

    function seedDatalist() {
        if (!el.datalist) {
            return;
        }
        el.datalist.innerHTML = stocks
            .slice(0, 520)
            .map((stock) => `<option value="${escapeHtml(stock.symbol)}">${escapeHtml(stock.name)}</option>`)
            .join("");
    }

    function seedSectors() {
        if (!el.filterSector) {
            return;
        }
        const sectors = [...new Set(stocks.map((stock) => stock.sector).filter(Boolean))]
            .filter((sector) => sector !== "-")
            .sort();
        el.filterSector.insertAdjacentHTML("beforeend", sectors
            .map((sector) => `<option value="${escapeHtml(sector)}">${escapeHtml(sector)}</option>`)
            .join(""));
    }

    function bindEvents() {
        document.querySelector("[data-add-watch]")?.addEventListener("click", () => addFromInput("watch"));
        document.querySelector("[data-add-compare]")?.addEventListener("click", () => addFromInput("compare"));
        document.querySelector("[data-clear-watch]")?.addEventListener("click", () => {
            state.watchlist = [];
            saveJson(storageKeys.watchlist, state.watchlist);
            renderAll();
        });
        document.querySelector("[data-clear-recent]")?.addEventListener("click", () => {
            state.recent = [];
            saveJson(storageKeys.recent, state.recent);
            renderAll();
        });
        document.querySelector("[data-clear-compare]")?.addEventListener("click", () => {
            state.compare = [];
            saveJson(storageKeys.compare, state.compare);
            renderAll();
        });
        document.querySelector("[data-reset-screen]")?.addEventListener("click", () => {
            el.filterSearch.value = "";
            el.filterSector.value = "";
            el.filterSignal.value = "60";
            el.filterPer.value = "80";
            el.filterRoe.value = "0";
            renderScreener();
        });
        document.querySelector("[data-reset-weights]")?.addEventListener("click", () => {
            state.weights = { ...defaultWeights };
            saveJson(storageKeys.weights, state.weights);
            renderAll();
        });
        document.querySelector("[data-create-report]")?.addEventListener("click", createReport);
        document.querySelector("[data-export-report]")?.addEventListener("click", exportReport);

        [el.filterSearch, el.filterSector, el.filterSignal, el.filterPer, el.filterRoe]
            .filter(Boolean)
            .forEach((input) => input.addEventListener("input", renderScreener));

        el.symbolInput?.addEventListener("keydown", (event) => {
            if (event.key === "Enter") {
                event.preventDefault();
                addFromInput("watch");
            }
        });

        document.addEventListener("click", (event) => {
            const action = event.target.closest("[data-action]");
            if (!action) {
                return;
            }
            const symbol = action.dataset.symbol;
            if (!symbol) {
                return;
            }
            if (action.dataset.action === "watch") {
                toggleWatch(symbol);
            }
            if (action.dataset.action === "compare") {
                toggleCompare(symbol);
            }
            if (action.dataset.action === "remove-watch") {
                state.watchlist = state.watchlist.filter((item) => item !== symbol);
                saveJson(storageKeys.watchlist, state.watchlist);
                renderAll();
            }
            if (action.dataset.action === "remove-compare") {
                state.compare = state.compare.filter((item) => item !== symbol);
                saveJson(storageKeys.compare, state.compare);
                renderAll();
            }
            if (action.dataset.action === "open-report") {
                state.selectedReportId = action.dataset.reportId;
                renderReports();
            }
            if (action.dataset.action === "delete-report") {
                state.reports = state.reports.filter((report) => report.id !== action.dataset.reportId);
                if (state.selectedReportId === action.dataset.reportId) {
                    state.selectedReportId = state.reports[0]?.id || null;
                }
                saveJson(storageKeys.reports, state.reports);
                renderReports();
            }
        });
    }

    function addFromInput(target) {
        const symbol = normalizeSymbol(el.symbolInput?.value);
        if (!symbol || !stockMap.has(symbol)) {
            return;
        }
        if (target === "watch") {
            addUnique(state.watchlist, symbol);
            saveJson(storageKeys.watchlist, state.watchlist);
        } else {
            addUnique(state.compare, symbol, 5);
            saveJson(storageKeys.compare, state.compare);
        }
        if (el.symbolInput) {
            el.symbolInput.value = "";
        }
        renderAll();
    }

    function renderAll() {
        renderWeights();
        renderWatchlist();
        renderRecent();
        renderScreener();
        renderCompare();
        renderReports();
        renderSummary();
    }

    function renderSummary() {
        setText(el.watchCount, state.watchlist.length);
        setText(el.recentCount, state.recent.length);
        setText(el.screenCount, state.screenRows.length);
        const compared = comparedStocks();
        const avg = average(compared.map((stock) => weightedScore(stock)).filter((score) => score != null));
        setText(el.weightedAverage, avg == null ? "-" : `${Math.round(avg)}점`);
    }

    function renderWatchlist() {
        const rows = state.watchlist.map((symbol) => stockMap.get(symbol)).filter(Boolean);
        el.watchList.innerHTML = rows.map((stock) => miniRow(stock, "remove-watch")).join("");
        el.watchEmpty.hidden = rows.length > 0;
    }

    function renderRecent() {
        const rows = state.recent
            .map((item) => stockMap.get(item.symbol) || item)
            .filter((item) => item.symbol)
            .slice(0, 8);
        el.recentList.innerHTML = rows.map((stock) => miniRow(stock, null, true)).join("");
        el.recentEmpty.hidden = rows.length > 0;
    }

    function renderScreener() {
        const search = String(el.filterSearch?.value || "").trim().toUpperCase();
        const sector = el.filterSector?.value || "";
        const minSignal = numberOrNull(el.filterSignal?.value) ?? 0;
        const maxPer = numberOrNull(el.filterPer?.value);
        const minRoe = numberOrNull(el.filterRoe?.value);

        state.screenRows = stocks
            .filter((stock) => !search || stock.symbol.includes(search) || stock.name.toUpperCase().includes(search))
            .filter((stock) => !sector || stock.sector === sector)
            .map((stock) => ({ ...stock, weighted: weightedScore(stock) }))
            .filter((stock) => stock.weighted == null || stock.weighted >= minSignal)
            .filter((stock) => maxPer == null || maxPer <= 0 || stock.perNumber == null || stock.perNumber <= maxPer)
            .filter((stock) => minRoe == null || stock.roeNumber == null || stock.roeNumber >= minRoe)
            .sort((left, right) => (right.weighted || 0) - (left.weighted || 0))
            .slice(0, 30);

        el.screenTable.innerHTML = `
            <div class="atelier-table-row atelier-table-head">
                <span>종목</span><span>섹터</span><span>가격</span><span>등락률</span><span>PER</span><span>ROE</span><span>Signal</span><span></span>
            </div>
            ${state.screenRows.map((stock) => `
                <div class="atelier-table-row">
                    <a class="stock-identity" href="/stocks/${encodeURIComponent(stock.symbol)}">
                        ${logo(stock)}
                        <span class="stock-copy"><b>${escapeHtml(stock.symbol)}</b><small>${escapeHtml(stock.name)}</small></span>
                    </a>
                    <span>${escapeHtml(stock.sector)}</span>
                    <span>${escapeHtml(stock.price)}</span>
                    <span class="${stock.positive ? "up" : "down"}">${escapeHtml(stock.change)}</span>
                    <span>${escapeHtml(stock.per)}</span>
                    <span>${escapeHtml(stock.roe)}</span>
                    <strong class="market-signal-score ${tone(stock.weighted)}">${scoreLabel(stock.weighted)}</strong>
                    <span class="atelier-row-actions">
                        <button type="button" data-action="watch" data-symbol="${escapeHtml(stock.symbol)}">${state.watchlist.includes(stock.symbol) ? "관심해제" : "관심"}</button>
                        <button type="button" data-action="compare" data-symbol="${escapeHtml(stock.symbol)}">${state.compare.includes(stock.symbol) ? "비교해제" : "비교"}</button>
                    </span>
                </div>
            `).join("")}
        `;
        renderSummary();
    }

    function renderCompare() {
        const rows = comparedStocks();
        el.compareChips.innerHTML = rows.map((stock) => `
            <button type="button" data-action="remove-compare" data-symbol="${escapeHtml(stock.symbol)}">
                ${escapeHtml(stock.symbol)} <span>×</span>
            </button>
        `).join("");
        el.compareEmpty.hidden = rows.length >= 2;
        if (rows.length === 0) {
            el.compareTable.innerHTML = "";
            return;
        }
        el.compareTable.innerHTML = `
            <div class="atelier-matrix" style="--atelier-cols: ${rows.length}">
                <div class="atelier-matrix-head">항목</div>
                ${rows.map((stock) => `<div class="atelier-matrix-head">${escapeHtml(stock.symbol)}</div>`).join("")}
                ${matrixRow("가중 Signal", rows, (stock) => scoreLabel(weightedScore(stock)))}
                ${matrixRow("기존 Signal", rows, (stock) => scoreLabel(stock.signalScore))}
                ${matrixRow("현재가", rows, (stock) => stock.price)}
                ${matrixRow("등락률", rows, (stock) => stock.change, true)}
                ${matrixRow("시가총액", rows, (stock) => stock.marketCap)}
                ${matrixRow("PER", rows, (stock) => stock.per)}
                ${matrixRow("PBR", rows, (stock) => stock.pbr)}
                ${matrixRow("ROE", rows, (stock) => stock.roe)}
                ${factorLabels.map(([key, label]) => matrixRow(label, rows, (stock) => scoreLabel(stock.components[key]))).join("")}
            </div>
        `;
        renderSummary();
    }

    function renderWeights() {
        el.weightList.innerHTML = factorLabels.map(([key, label]) => `
            <label class="atelier-weight">
                <span>${label}</span>
                <input type="range" min="0" max="30" value="${state.weights[key] ?? 0}" data-weight="${key}">
                <b>${state.weights[key] ?? 0}</b>
            </label>
        `).join("");
        el.weightList.querySelectorAll("[data-weight]").forEach((input) => {
            input.addEventListener("input", () => {
                const key = input.dataset.weight;
                state.weights[key] = Number(input.value);
                input.closest(".atelier-weight").querySelector("b").textContent = input.value;
                saveJson(storageKeys.weights, state.weights);
                renderScreener();
                renderCompare();
            });
        });
    }

    function renderReports() {
        if (!state.selectedReportId && state.reports.length > 0) {
            state.selectedReportId = state.reports[0].id;
        }
        el.reportList.innerHTML = state.reports.length === 0
            ? `<p class="atelier-empty">저장된 리포트가 없습니다.</p>`
            : state.reports.map((report) => `
                <div class="atelier-report-item ${report.id === state.selectedReportId ? "active" : ""}">
                    <button type="button" data-action="open-report" data-report-id="${report.id}">
                        <strong>${escapeHtml(report.title)}</strong>
                        <span>${escapeHtml(report.createdAt)}</span>
                    </button>
                    <button type="button" data-action="delete-report" data-report-id="${report.id}">삭제</button>
                </div>
            `).join("");
        const report = state.reports.find((item) => item.id === state.selectedReportId);
        el.reportPreview.innerHTML = report ? reportHtml(report, false) : `
            <span>Report Preview</span>
            <h3>저장된 리포트가 없습니다.</h3>
            <p>비교 종목과 현재 가중치를 기준으로 리포트를 저장할 수 있습니다.</p>
        `;
    }

    function createReport() {
        const rows = comparedStocks().length >= 2 ? comparedStocks() : state.screenRows.slice(0, 5);
        if (rows.length === 0) {
            return;
        }
        const createdAt = new Date().toLocaleString("ko-KR", { hour12: false });
        const report = {
            id: `report-${Date.now()}`,
            title: `Atelier Report ${createdAt}`,
            createdAt,
            weights: { ...state.weights },
            rows: rows.map((stock) => ({
                symbol: stock.symbol,
                name: stock.name,
                sector: stock.sector,
                price: stock.price,
                change: stock.change,
                marketCap: stock.marketCap,
                per: stock.per,
                pbr: stock.pbr,
                roe: stock.roe,
                weighted: Math.round(weightedScore(stock) ?? stock.signalScore ?? 0),
                signal: stock.signalScore
            }))
        };
        state.reports = [report, ...state.reports].slice(0, 12);
        state.selectedReportId = report.id;
        saveJson(storageKeys.reports, state.reports);
        renderReports();
    }

    function exportReport() {
        const report = state.reports.find((item) => item.id === state.selectedReportId);
        if (!report) {
            return;
        }
        const html = `<!doctype html><html lang="ko"><head><meta charset="utf-8"><title>${escapeHtml(report.title)}</title>
            <style>body{font-family:Inter,system-ui,sans-serif;margin:32px;color:#111}table{border-collapse:collapse;width:100%}td,th{border-bottom:1px solid #ddd;padding:10px;text-align:left}h1{font-size:28px}</style>
            </head><body>${reportHtml(report, true)}</body></html>`;
        const blob = new Blob([html], { type: "text/html;charset=utf-8" });
        const url = URL.createObjectURL(blob);
        const anchor = document.createElement("a");
        anchor.href = url;
        anchor.download = `${report.title.replace(/[^\w가-힣-]+/g, "_")}.html`;
        document.body.append(anchor);
        anchor.click();
        anchor.remove();
        URL.revokeObjectURL(url);
    }

    function reportHtml(report, standalone) {
        return `
            <span>${standalone ? "King Yurina Atelier" : "Report Preview"}</span>
            <h3>${escapeHtml(report.title)}</h3>
            <p>${escapeHtml(report.createdAt)} 기준, 현재 저장된 Signal 가중치로 생성한 비교 리포트입니다.</p>
            <table>
                <thead><tr><th>종목</th><th>섹터</th><th>가중 Signal</th><th>기존 Signal</th><th>등락률</th><th>PER</th><th>ROE</th></tr></thead>
                <tbody>
                    ${report.rows.map((row) => `
                        <tr>
                            <td><strong>${escapeHtml(row.symbol)}</strong><br><small>${escapeHtml(row.name)}</small></td>
                            <td>${escapeHtml(row.sector)}</td>
                            <td>${row.weighted} / 100</td>
                            <td>${row.signal == null ? "-" : `${row.signal} / 100`}</td>
                            <td>${escapeHtml(row.change)}</td>
                            <td>${escapeHtml(row.per)}</td>
                            <td>${escapeHtml(row.roe)}</td>
                        </tr>
                    `).join("")}
                </tbody>
            </table>
        `;
    }

    function miniRow(stock, removeAction, compact = false) {
        const score = weightedScore(stock);
        return `
            <div class="atelier-mini-row">
                <a class="stock-identity" href="/stocks/${encodeURIComponent(stock.symbol)}">
                    ${logo(stock)}
                    <span class="stock-copy"><b>${escapeHtml(stock.symbol)}</b><small>${escapeHtml(stock.name || stock.sector || "")}</small></span>
                </a>
                <span class="market-signal-score ${tone(score)}">${scoreLabel(score)}</span>
                ${removeAction && !compact ? `<button type="button" data-action="${removeAction}" data-symbol="${escapeHtml(stock.symbol)}">×</button>` : ""}
            </div>
        `;
    }

    function logo(stock) {
        return `
            <span class="stock-logo small">
                ${stock.logoUrl ? `<img src="${escapeAttribute(stock.logoUrl)}" alt="${escapeAttribute(stock.symbol)} logo" loading="lazy" onerror="this.remove()">` : ""}
                <span>${escapeHtml(stock.logoInitial || "?")}</span>
            </span>
        `;
    }

    function matrixRow(label, rows, reader, signed = false) {
        return `
            <div>${escapeHtml(label)}</div>
            ${rows.map((stock) => `<div class="${signed && stock.positive ? "up" : signed ? "down" : ""}">${escapeHtml(reader(stock) ?? "-")}</div>`).join("")}
        `;
    }

    function comparedStocks() {
        return state.compare.map((symbol) => stockMap.get(symbol)).filter(Boolean);
    }

    function weightedScore(stock) {
        let weighted = 0;
        let weightTotal = 0;
        factorLabels.forEach(([key]) => {
            const score = stock.components?.[key];
            const weight = Number(state.weights[key] || 0);
            if (score != null && weight > 0) {
                weighted += score * weight;
                weightTotal += weight;
            }
        });
        if (weightTotal === 0) {
            return stock.signalScore ?? null;
        }
        return Math.round(weighted / weightTotal);
    }

    function toggleWatch(symbol) {
        if (state.watchlist.includes(symbol)) {
            state.watchlist = state.watchlist.filter((item) => item !== symbol);
        } else {
            addUnique(state.watchlist, symbol);
        }
        saveJson(storageKeys.watchlist, state.watchlist);
        renderAll();
    }

    function toggleCompare(symbol) {
        if (state.compare.includes(symbol)) {
            state.compare = state.compare.filter((item) => item !== symbol);
        } else {
            addUnique(state.compare, symbol, 5);
        }
        saveJson(storageKeys.compare, state.compare);
        renderAll();
    }

    function addUnique(list, symbol, limit = 30) {
        const normalized = normalizeSymbol(symbol);
        if (!normalized || !stockMap.has(normalized)) {
            return;
        }
        const index = list.indexOf(normalized);
        if (index >= 0) {
            list.splice(index, 1);
        }
        list.unshift(normalized);
        list.splice(limit);
    }

    function loadJson(key, fallback) {
        try {
            const value = localStorage.getItem(key);
            return value ? JSON.parse(value) : fallback;
        } catch {
            return fallback;
        }
    }

    function saveJson(key, value) {
        localStorage.setItem(key, JSON.stringify(value));
    }

    function normalizeSymbol(value) {
        return String(value || "").trim().toUpperCase().replace(/\s+/g, "");
    }

    function numberOrNull(value) {
        const number = Number(value);
        return Number.isFinite(number) ? number : null;
    }

    function parseNumber(value) {
        const match = String(value || "").match(/[+-]?\d[\d,]*(?:\.\d+)?/);
        return match ? Number(match[0].replace(/,/g, "")) : null;
    }

    function average(values) {
        return values.length === 0 ? null : values.reduce((sum, value) => sum + value, 0) / values.length;
    }

    function tone(score) {
        if (score == null) {
            return "neutral";
        }
        if (score >= 67) {
            return "positive";
        }
        if (score >= 52) {
            return "neutral";
        }
        if (score >= 40) {
            return "caution";
        }
        return "negative";
    }

    function scoreLabel(score) {
        return score == null ? "-" : `${Math.round(score)} / 100`;
    }

    function setText(node, value) {
        if (node) {
            node.textContent = value;
        }
    }

    function escapeHtml(value) {
        return String(value ?? "")
            .replaceAll("&", "&amp;")
            .replaceAll("<", "&lt;")
            .replaceAll(">", "&gt;")
            .replaceAll('"', "&quot;")
            .replaceAll("'", "&#39;");
    }

    function escapeAttribute(value) {
        return escapeHtml(value).replaceAll("`", "&#96;");
    }
})();
