package com.petcare.init;

import com.petcare.entity.User;
import com.petcare.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 启动数据初始化。
 * <p>
 * 解决的问题：SQL 脚本里的种子账号密码是明文 123456，
 * 但在脚本里手写 BCrypt 密文容易抄错，所以放到启动时统一加密。
 * <p>
 * 已经加密过的账号（$2a$ 开头）会被跳过，因此重复启动是幂等的。
 * 生产环境应将密码迁移放入数据库变更脚本或独立运维流程。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        List<User> plainUsers = userMapper.selectPlainPasswordUsers();
        if (plainUsers.isEmpty()) {
            return;
        }
        for (User user : plainUsers) {
            userMapper.updatePassword(user.getId(), passwordEncoder.encode(user.getPassword()));
        }
        log.info("检测到 {} 个明文密码账号，已升级为 BCrypt 密文（默认密码仍为 123456）", plainUsers.size());
    }
}
