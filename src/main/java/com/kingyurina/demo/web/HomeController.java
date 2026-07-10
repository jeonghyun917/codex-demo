package com.kingyurina.demo.web;

import com.kingyurina.demo.menu.MenuService;
import com.kingyurina.demo.stock.StockDashboardViewPayload;
import com.kingyurina.demo.stock.StockDashboardViewSnapshotService;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class HomeController {

    private final MenuService menuService;
    private final StockDashboardViewSnapshotService stockDashboardViewSnapshotService;

    public HomeController(MenuService menuService, StockDashboardViewSnapshotService stockDashboardViewSnapshotService) {
        this.menuService = menuService;
        this.stockDashboardViewSnapshotService = stockDashboardViewSnapshotService;
    }

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("mainMenus", menuService.mainMenus());
        return "index";
    }

    @GetMapping({"/quant", "/dashboard"})
    public String quant(@RequestParam(defaultValue = "SP500") String index, Model model) {
        String activeIndex = normalizeIndex(index);
        StockDashboardViewPayload dashboard = stockDashboardViewSnapshotService.build(activeIndex);
        model.addAttribute("mainMenus", menuService.mainMenus());
        model.addAttribute("activeIndex", dashboard.activeIndex());
        model.addAttribute("market", dashboard.market());
        model.addAttribute("macroRegime", dashboard.macroRegime());
        model.addAttribute("modelHealth", dashboard.modelHealth());
        model.addAttribute("dataSources", dashboard.dataSources());
        return "dashboard";
    }

    private static String normalizeIndex(String value) {
        if (value == null || value.isBlank()) {
            return "SP500";
        }
        String normalized = value.trim().toUpperCase().replace("-", "").replace("_", "");
        if ("NASDAQ100".equals(normalized) || "NDX".equals(normalized)) {
            return "NASDAQ100";
        }
        if ("DOW30".equals(normalized) || "DOWJONES30".equals(normalized) || "DJI".equals(normalized)) {
            return "DOW30";
        }
        return "SP500";
    }
}
