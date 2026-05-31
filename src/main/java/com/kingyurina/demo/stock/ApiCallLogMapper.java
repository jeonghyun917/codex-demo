package com.kingyurina.demo.stock;

import java.time.LocalDateTime;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.context.annotation.Profile;

@Mapper
@Profile("mariadb")
public interface ApiCallLogMapper {

    void insert(ApiCallLog log);

    LocalDateTime findLatestSuccessfulCallAt(@Param("endpoint") String endpoint, @Param("symbol") String symbol);
}
