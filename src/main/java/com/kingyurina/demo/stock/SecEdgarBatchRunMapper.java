package com.kingyurina.demo.stock;

import org.apache.ibatis.annotations.Mapper;
import org.springframework.context.annotation.Profile;

@Mapper
@Profile("mariadb")
public interface SecEdgarBatchRunMapper {

    void insert(SecEdgarBatchRun run);

    void finish(SecEdgarBatchRun run);
}
