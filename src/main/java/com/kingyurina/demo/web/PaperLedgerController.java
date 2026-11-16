package com.kingyurina.demo.web;

import java.util.List;
import java.util.Map;
import com.kingyurina.demo.menu.MenuService;
import com.kingyurina.demo.stock.PaperLedgerViewService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class PaperLedgerController {
    private final MenuService menus;
    private final PaperLedgerViewService ledger;

    public PaperLedgerController(MenuService menus, PaperLedgerViewService ledger) {
        this.menus = menus;
        this.ledger = ledger;
    }

    @GetMapping("/paper-trading")
    @SuppressWarnings("unchecked")
    public String page(@RequestParam(defaultValue = "") String day,
                       @RequestParam(defaultValue = "0.001") String fee, Model model) {
        var view = ledger.read();
        var days = (List<Map<String, Object>>) view.get("days");
        var accounts = (List<Map<String, Object>>) view.get("accounts");
        model.addAttribute("mainMenus", menus.mainMenus());
        model.addAttribute("ledger", view);
        model.addAttribute("selectedDay", days.stream().filter(d -> day.equals(d.get("date"))).findFirst()
                .orElse(days.isEmpty() ? null : days.getFirst()));
        model.addAttribute("account", accounts.stream().filter(a -> fee.equals(a.get("feeKey"))).findFirst()
                .orElse(accounts.isEmpty() ? null : accounts.getFirst()));
        return "paper-trading";
    }
}
