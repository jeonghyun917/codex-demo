package com.kingyurina.demo.menu;

import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class MenuService {

    private final ObjectProvider<MainMenuRepository> mainMenuRepository;
    private final ObjectProvider<SideMenuRepository> sideMenuRepository;

    public MenuService(ObjectProvider<MainMenuRepository> mainMenuRepository,
            ObjectProvider<SideMenuRepository> sideMenuRepository) {
        this.mainMenuRepository = mainMenuRepository;
        this.sideMenuRepository = sideMenuRepository;
    }

    public List<MenuItem> mainMenus() {
        MainMenuRepository repository = mainMenuRepository.getIfAvailable();
        if (repository == null) {
            return fallbackMainMenus();
        }
        return repository.findEnabledMenus()
                .stream()
                .map(menu -> new MenuItem(menu.getLabel(), menu.getHref()))
                .toList();
    }

    public List<MenuItem> sideMenus() {
        SideMenuRepository repository = sideMenuRepository.getIfAvailable();
        if (repository == null) {
            return fallbackSideMenus();
        }
        return repository.findEnabledMenus()
                .stream()
                .map(menu -> new MenuItem(menu.getLabel(), menu.getHref()))
                .toList();
    }

    private static List<MenuItem> fallbackMainMenus() {
        return List.of(
                new MenuItem("Quant", "/quant"),
                new MenuItem("Stocks", "/stocks"),
                new MenuItem("Heatmap", "/stocks/heatmap"),
                new MenuItem("ETF", "/etfs"),
                new MenuItem("Atelier", "/atelier"),
                new MenuItem("Systems", "#systems"),
                new MenuItem("Signal", "/signals/backtest"),
                new MenuItem("Contact", "#contact"));
    }

    private static List<MenuItem> fallbackSideMenus() {
        return List.of(
                new MenuItem("Overview", "#overview"),
                new MenuItem("Stocks", "/stocks"),
                new MenuItem("Reports", "#reports"),
                new MenuItem("Settings", "#settings"),
                new MenuItem("Archive", "#archive"));
    }
}
