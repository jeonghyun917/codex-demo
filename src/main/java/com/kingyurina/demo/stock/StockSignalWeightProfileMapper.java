package com.kingyurina.demo.stock;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.context.annotation.Profile;

@Mapper
@Profile("mariadb")
public interface StockSignalWeightProfileMapper {

    List<StockSignalWeightProfile> findProfiles();

    List<StockSignalWeightProfileItem> findItemsByProfile(@Param("profileCode") String profileCode);

    void upsertProfile(StockSignalWeightProfile profile);

    void upsertItem(StockSignalWeightProfileItem item);
}
