package com.petcare.mapper;

import com.petcare.entity.Category;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 服务品类 Mapper。
 */
public interface CategoryMapper {

    /** 用户端：只查启用的，按 sort 升序 */
    List<Category> selectAllEnabled();

    List<Category> selectAll();

    Category selectById(@Param("id") Long id);
}
