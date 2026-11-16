package com.kingyurina.demo.web;

import com.kingyurina.demo.menu.MenuService;
import com.kingyurina.demo.stock.StockDashboardViewPayload;
import com.kingyurina.demo.stock.StockDashboardViewSnapshotService;
import com.kingyurina.demo.stock.StockForwardValidationStatusService;
import com.kingyurina.demo.stock.QuantBuyCandidatesService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class HomeController {

    private final MenuService menuService;
    private final StockDashboardViewSnapshotService stockDashboardViewSnapshotService;
    private final StockForwardValidationStatusService forwardValidationStatusService;
    private final QuantBuyCandidatesService buyCandidatesService;

    public HomeController(MenuService menuService, StockDashboardViewSnapshotService stockDashboardViewSnapshotService) {
        this(menuService, stockDashboardViewSnapshotService, null);
    }

    public HomeController(MenuService menuService, StockDashboardViewSnapshotService stockDashboardViewSnapshotService,
            StockForwardValidationStatusService forwardValidationStatusService) {
        this(menuService, stockDashboardViewSnapshotService, forwardValidationStatusService, null);
    }

    @Autowired
    public HomeController(MenuService menuService, StockDashboardViewSnapshotService stockDashboardViewSnapshotService,
            StockForwardValidationStatusService forwardValidationStatusService, QuantBuyCandidatesService buyCandidatesService) {
        this.menuService = menuService;
        this.stockDashboardViewSnapshotService = stockDashboardViewSnapshotService;
        this.forwardValidationStatusService = forwardValidationStatusService;
        this.buyCandidatesService = buyCandidatesService;
    }

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("mainMenus", menuService.mainMenus());
        return "index";
    }

    @GetMapping("/contact")
    public String contact() {
        return "contact";
    }

    @GetMapping("/contact1")
    public String contactOne() {
        return "contact1";
    }

    @GetMapping("/contact2")
    public String contactTwo() {
        return "contact2";
    }

    @GetMapping("/contact3")
    public String contactThree() {
        return "contact3";
    }

    @GetMapping("/contact4")
    public String contactFour() {
        return "contact4";
    }

    public String quant(String index, Model model) {
        return quant(index, null, model);
    }

    @GetMapping({"/quant", "/dashboard"})
    public String quant(@RequestParam(defaultValue = "SP500") String index,
            @RequestParam(required = false) String decisionDate, Model model) {
        String activeIndex = normalizeIndex(index);
        StockDashboardViewPayload dashboard = stockDashboardViewSnapshotService.build(activeIndex);
        model.addAttribute("mainMenus", menuService.mainMenus());
        model.addAttribute("activeIndex", dashboard.activeIndex());
        model.addAttribute("buyCandidates", buyCandidatesService == null
                ? QuantBuyCandidatesService.unavailable() : decisionDate == null
                    ? buyCandidatesService.build(activeIndex) : buyCandidatesService.build(activeIndex, decisionDate));
        model.addAttribute("market", dashboard.market());
        model.addAttribute("macroRegime", dashboard.macroRegime());
        model.addAttribute("modelHealth", dashboard.modelHealth());
        model.addAttribute("dataSources", dashboard.dataSources());
        model.addAttribute("expectedReturnEvaluation", dashboard.expectedReturnEvaluation());
        model.addAttribute("forwardValidation", forwardValidationStatusService == null
                ? StockForwardValidationStatusService.unavailable() : forwardValidationStatusService.build(activeIndex));
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
