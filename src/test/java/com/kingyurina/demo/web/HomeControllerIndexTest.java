package com.kingyurina.demo.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import com.kingyurina.demo.menu.MenuItem;
import com.kingyurina.demo.menu.MenuService;
import com.kingyurina.demo.stock.StockDashboardViewSnapshotService;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;

class HomeControllerIndexTest {

    @Test
    void indexExposesMainMenusWithoutBuildingDashboardSnapshot() {
        MenuService menuService = mock(MenuService.class);
        StockDashboardViewSnapshotService snapshots = mock(StockDashboardViewSnapshotService.class);
        List<MenuItem> menus = List.of(new MenuItem("Quant", "/quant"));
        when(menuService.mainMenus()).thenReturn(menus);
        HomeController controller = new HomeController(menuService, snapshots);
        ExtendedModelMap model = new ExtendedModelMap();

        assertEquals("index", controller.index(model));
        assertSame(menus, model.get("mainMenus"));
        verify(menuService).mainMenus();
        verifyNoInteractions(snapshots);
    }
}
