package com.petcare.mapper;

import com.petcare.entity.User;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 用户 Mapper，SQL 见 resources/mapper/UserMapper.xml。
 */
public interface UserMapper {

    User selectByUsername(@Param("username") String username);

    User selectById(@Param("id") Long id);

    int insert(User user);

    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

    int updatePassword(@Param("id") Long id, @Param("password") String password);

    List<User> selectPage(@Param("keyword") String keyword,
                          @Param("offset") int offset,
                          @Param("size") int size);

    long countPage(@Param("keyword") String keyword);

    /**
     * 查出密码还是明文的账号（初始化脚本里写的是 123456）。
     * 启动时会把它们统一加密，避免脚本里塞 bcrypt 串抄错。
     */
    List<User> selectPlainPasswordUsers();
}
