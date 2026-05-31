package com.kingyurina.demo.menu;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.springframework.context.annotation.Profile;

@Mapper
@Profile("mariadb")
public interface SideMenuRepository {

    List<SideMenu> findEnabledMenus();
}
