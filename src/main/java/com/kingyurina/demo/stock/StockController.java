package com.kingyurina.demo.stock;

import com.kingyurina.demo.menu.MenuService;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class StockController {

    private final StockAnalysisService stockAnalysisService;
    private final StockInfoViewService stockInfoViewService;
    private final StockMarketViewService stockMarketViewService;
    private final StockHeatmapViewService stockHeatmapViewService;
    private final StockSignalService stockSignalService;
    private final StockQuantOpinionService stockQuantOpinionService;
    private final StockBacktestService stockBacktestService;
    private final StockPortfolioBacktestService stockPortfolioBacktestService;
    private final StockSearchService stockSearchService;
    private final MenuService menuService;

    public StockController(StockAnalysisService stockAnalysisService, StockInfoViewService stockInfoViewService,
            StockMarketViewService stockMarketViewService, StockHeatmapViewService stockHeatmapViewService,
            StockSignalService stockSignalService, StockQuantOpinionService stockQuantOpinionService,
            StockBacktestService stockBacktestService,
            StockPortfolioBacktestService stockPortfolioBacktestService, StockSearchService stockSearchService,
            MenuService menuService) {
        this.stockAnalysisService = stockAnalysisService;
        this.stockInfoViewService = stockInfoViewService;
        this.stockMarketViewService = stockMarketViewService;
        this.stockHeatmapViewService = stockHeatmapViewService;
        this.stockSignalService = stockSignalService;
        this.stockQuantOpinionService = stockQuantOpinionService;
        this.stockBacktestService = stockBacktestService;
        this.stockPortfolioBacktestService = stockPortfolioBacktestService;
        this.stockSearchService = stockSearchService;
        this.menuService = menuService;
    }

    @GetMapping("/stocks")
    public String stocks(@RequestParam(required = false) String symbol, Model model) {
        if (symbol != null && !symbol.isBlank()) {
            return "redirect:/stocks/" + stockSearchService.resolveSymbol(symbol);
        }
        model.addAttribute("mainMenus", menuService.mainMenus());
        model.addAttribute("market", stockMarketViewService.sp500());
        return "stocks-index";
    }

    @GetMapping("/atelier")
    public String atelier(Model model) {
        model.addAttribute("mainMenus", menuService.mainMenus());
        model.addAttribute("market", stockMarketViewService.sp500());
        return "atelier";
    }

    @GetMapping("/signals/backtest")
    public String signalBacktest(Model model) {
        model.addAttribute("mainMenus", menuService.mainMenus());
        model.addAttribute("backtest", stockBacktestService.build("SP500"));
        return "stock-backtest";
    }

    @GetMapping("/signals/portfolio")
    public String portfolioBacktest(Model model) {
        model.addAttribute("mainMenus", menuService.mainMenus());
        model.addAttribute("portfolio", stockPortfolioBacktestService.build("SP500"));
        return "portfolio-backtest";
    }

    @GetMapping("/stocks/{symbol}")
    public String stockDetail(@PathVariable String symbol, Model model) {
        String resolvedSymbol = stockSearchService.resolveSymbol(symbol);
        if (stockSearchService.isDifferentSymbol(symbol, resolvedSymbol)) {
            return "redirect:/stocks/" + resolvedSymbol;
        }
        StockDashboard dashboard = stockAnalysisService.dashboard(resolvedSymbol);
        model.addAttribute("mainMenus", menuService.mainMenus());
        model.addAttribute("sideMenus", menuService.sideMenus());
        model.addAttribute("dashboard", dashboard);
        model.addAttribute("info", stockInfoViewService.build(dashboard));
        model.addAttribute("signal", stockSignalService.buildStored(dashboard.symbol()));
        model.addAttribute("quantOpinion", stockQuantOpinionService.build(dashboard.symbol()));
        return "stocks";
    }

    @GetMapping("/stocks/heatmap")
    public String heatmap(@RequestParam(defaultValue = "SP500") String index, Model model) {
        model.addAttribute("mainMenus", menuService.mainMenus());
        model.addAttribute("heatmap", stockHeatmapViewService.build(index));
        return "stocks-heatmap";
    }
}
