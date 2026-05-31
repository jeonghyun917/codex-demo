package com.kingyurina.demo.stock;

import org.apache.ibatis.annotations.Mapper;
import org.springframework.context.annotation.Profile;

@Mapper
@Profile("mariadb")
public interface FinnhubBatchRunMapper {

    void insert(FinnhubBatchRun run);

    void finish(FinnhubBatchRun run);
}
