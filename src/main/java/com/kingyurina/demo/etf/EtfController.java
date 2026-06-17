package com.kingyurina.demo.etf;

import com.kingyurina.demo.menu.MenuService;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class EtfController {

    private final EtfMarketViewService etfMarketViewService;
    private final MenuService menuService;

    public EtfController(EtfMarketViewService etfMarketViewService, MenuService menuService) {
        this.etfMarketViewService = etfMarketViewService;
        this.menuService = menuService;
    }

    @GetMapping("/etfs")
    public String etfs(@RequestParam(required = false) String symbol, Model model) {
        if (symbol != null && !symbol.isBlank()) {
            return "redirect:/etfs/" + symbol.trim().toUpperCase();
        }
        model.addAttribute("mainMenus", menuService.mainMenus());
        model.addAttribute("market", etfMarketViewService.market());
        return "etfs-index";
    }

    @GetMapping("/etfs/{symbol}")
    public String etfDetail(@PathVariable String symbol, Model model) {
        model.addAttribute("mainMenus", menuService.mainMenus());
        model.addAttribute("etf", etfMarketViewService.detail(symbol));
        return "etfs";
    }
}
