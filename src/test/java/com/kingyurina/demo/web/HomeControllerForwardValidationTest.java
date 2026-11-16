package com.kingyurina.demo.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;
import com.kingyurina.demo.menu.MenuService;
import com.kingyurina.demo.stock.StockDashboardViewPayload;
import com.kingyurina.demo.stock.StockDashboardViewSnapshotService;
import com.kingyurina.demo.stock.StockForwardValidationStatusService;

class HomeControllerForwardValidationTest {
    @Test
    void quantExposesSeparateReadOnlyValidationStatusAlongsideExistingDashboard() {
        var snapshots = mock(StockDashboardViewSnapshotService.class);
        var validation = mock(StockForwardValidationStatusService.class);
        var view = StockForwardValidationStatusService.unavailable();
        when(snapshots.build("DOW30")).thenReturn(new StockDashboardViewPayload("DOW30", null, null, null, null, null));
        when(validation.build("DOW30")).thenReturn(view);
        var model = new ExtendedModelMap();
        var controller = new HomeController(mock(MenuService.class), snapshots, validation);

        assertThat(controller.quant("DJI", model)).isEqualTo("dashboard");
        assertThat(model.get("activeIndex")).isEqualTo("DOW30");
        assertThat(model.get("forwardValidation")).isSameAs(view);
    }
}
