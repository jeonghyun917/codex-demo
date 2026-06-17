package com.kingyurina.demo.stock;

import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface Institution13fBatchRunMapper {

    void insert(Institution13fBatchRun run);

    void finish(Institution13fBatchRun run);
}
