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
    private final MenuService menuService;

    public StockController(StockAnalysisService stockAnalysisService, StockInfoViewService stockInfoViewService,
            StockMarketViewService stockMarketViewService, StockHeatmapViewService stockHeatmapViewService,
            StockSignalService stockSignalService, MenuService menuService) {
        this.stockAnalysisService = stockAnalysisService;
        this.stockInfoViewService = stockInfoViewService;
        this.stockMarketViewService = stockMarketViewService;
        this.stockHeatmapViewService = stockHeatmapViewService;
        this.stockSignalService = stockSignalService;
        this.menuService = menuService;
    }

    @GetMapping("/stocks")
    public String stocks(@RequestParam(required = false) String symbol, Model model) {
        if (symbol != null && !symbol.isBlank()) {
            return "redirect:/stocks/" + symbol.trim().toUpperCase();
        }
        model.addAttribute("mainMenus", menuService.mainMenus());
        model.addAttribute("market", stockMarketViewService.sp500());
        return "stocks-index";
    }

    @GetMapping("/stocks/{symbol}")
    public String stockDetail(@PathVariable String symbol, Model model) {
        StockDashboard dashboard = stockAnalysisService.dashboard(symbol);
        model.addAttribute("mainMenus", menuService.mainMenus());
        model.addAttribute("sideMenus", menuService.sideMenus());
        model.addAttribute("dashboard", dashboard);
        model.addAttribute("info", stockInfoViewService.build(dashboard));
        model.addAttribute("signal", stockSignalService.buildStored(dashboard.symbol()));
        return "stocks";
    }

    @GetMapping("/stocks/heatmap")
    public String heatmap(@RequestParam(defaultValue = "SP500") String index, Model model) {
        model.addAttribute("mainMenus", menuService.mainMenus());
        model.addAttribute("heatmap", stockHeatmapViewService.build(index));
        return "stocks-heatmap";
    }
}
