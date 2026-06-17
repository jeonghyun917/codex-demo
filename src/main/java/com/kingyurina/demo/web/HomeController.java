package com.kingyurina.demo.web;

import com.kingyurina.demo.menu.MenuService;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    private final MenuService menuService;

    public HomeController(MenuService menuService) {
        this.menuService = menuService;
    }

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("mainMenus", menuService.mainMenus());
        return "index";
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        model.addAttribute("mainMenus", menuService.mainMenus());
        return "dashboard";
    }
}
