package com.petcare.service;

import com.petcare.common.BusinessException;
import com.petcare.common.ResultCode;
import com.petcare.common.UserContext;
import com.petcare.entity.Pet;
import com.petcare.mapper.OrderMapper;
import com.petcare.mapper.PetMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

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
    private final OrderMapper orderMapper;

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
        if (!hasUpdatableField(pet)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "没有可更新的宠物字段");
        }
        pet.setUserId(UserContext.getUserId());
        int rows = petMapper.update(pet);
        // 影响 0 行说明该宠物不属于当前用户，或已被删除
        if (rows == 0) {
            throw new BusinessException("宠物档案不存在或无权修改");
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Pet pet = petMapper.selectByIdForUpdate(id, UserContext.getUserId());
        if (pet == null) {
            throw new BusinessException("宠物档案不存在或无权删除");
        }
        if (orderMapper.countByPetId(id) > 0) {
            throw new BusinessException("该宠物已有历史订单，不能删除；请保留档案以维持订单展示");
        }
        int rows = petMapper.deleteById(id, UserContext.getUserId());
        if (rows == 0) {
            throw new BusinessException("宠物档案不存在或无权删除");
        }
    }

    private boolean hasUpdatableField(Pet pet) {
        return StringUtils.hasText(pet.getName())
                || StringUtils.hasText(pet.getPetType())
                || pet.getBreed() != null
                || pet.getGender() != null
                || pet.getAge() != null
                || pet.getWeight() != null
                || pet.getAvatar() != null
                || pet.getRemark() != null;
    }
}
