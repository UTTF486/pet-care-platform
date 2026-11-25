package com.petcare.mapper;

import com.petcare.entity.Pet;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 宠物档案 Mapper。
 */
public interface PetMapper {

    int insert(Pet pet);

    int update(Pet pet);

    int deleteById(@Param("id") Long id, @Param("userId") Long userId);

    Pet selectById(@Param("id") Long id);

    /** 下单和删除宠物共用这把行锁，避免“检查后插入新订单”的竞态。 */
    Pet selectByIdForUpdate(@Param("id") Long id, @Param("userId") Long userId);

    List<Pet> selectByUserId(@Param("userId") Long userId);

    long countByUserId(@Param("userId") Long userId);
}
