package com.petcare.controller;

import com.petcare.common.Result;
import com.petcare.entity.Pet;
import com.petcare.service.PetService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

/**
 * 宠物档案接口（全部需要登录，用户只能操作自己的数据）。
 */
@RestController
@RequestMapping("/api/pets")
@RequiredArgsConstructor
public class PetController {

    private final PetService petService;

    @GetMapping
    public Result<List<Pet>> list() {
        return Result.success(petService.listMine());
    }

    @PostMapping
    public Result<Long> add(@RequestBody @Valid Pet pet) {
        return Result.success("添加成功", petService.add(pet));
    }

    @PutMapping
    public Result<Void> update(@RequestBody @Valid Pet pet) {
        petService.update(pet);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        petService.delete(id);
        return Result.success();
    }
}
