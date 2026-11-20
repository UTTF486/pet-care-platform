package com.petcare.service;

import com.petcare.common.BusinessException;
import com.petcare.common.ResultCode;
import com.petcare.common.UserContext;
import com.petcare.entity.Pet;
import com.petcare.mapper.PetMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 宠物档案服务。
 * <p>
 * 数据隔离是重点：所有写操作都带 userId 条件，
 * 防止 A 用户通过改 id 操作 B 用户的宠物（越权）。
 */
@Service
@RequiredArgsConstructor
public class PetService {

    private final PetMapper petMapper;

    public List<Pet> listMine() {
        return petMapper.selectByUserId(UserContext.getUserId());
    }

    public Long add(Pet pet) {
        pet.setId(null);
        pet.setUserId(UserContext.getUserId());   // 归属关系由后端决定，不信任前端
        pet.setPetType(pet.getPetType() == null ? "猫" : pet.getPetType());
        pet.setGender(pet.getGender() == null ? 0 : pet.getGender());
        pet.setAge(pet.getAge() == null ? 0 : pet.getAge());
        pet.setBreed(pet.getBreed() == null ? "" : pet.getBreed());
        pet.setAvatar(pet.getAvatar() == null ? "" : pet.getAvatar());
        pet.setRemark(pet.getRemark() == null ? "" : pet.getRemark());
        petMapper.insert(pet);
        return pet.getId();
    }

    public void update(Pet pet) {
        if (pet.getId() == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "缺少宠物 id");
        }
        pet.setUserId(UserContext.getUserId());
        int rows = petMapper.update(pet);
        // 影响 0 行说明该宠物不属于当前用户，或已被删除
        if (rows == 0) {
            throw new BusinessException("宠物档案不存在或无权修改");
        }
    }

    public void delete(Long id) {
        int rows = petMapper.deleteById(id, UserContext.getUserId());
        if (rows == 0) {
            throw new BusinessException("宠物档案不存在或无权删除");
        }
    }
}
