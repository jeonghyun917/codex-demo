package com.kingyurina.demo.stock;

import org.apache.ibatis.annotations.Mapper;
import org.springframework.context.annotation.Profile;

@Mapper
@Profile("mariadb")
public interface YahooCandleBatchRunMapper {

    void insert(YahooCandleBatchRun run);

    void finish(YahooCandleBatchRun run);
}
