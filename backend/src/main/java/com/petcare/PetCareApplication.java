package com.petcare;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.ZoneId;
import java.util.TimeZone;

/**
 * 启动类。
 * <p>
 * {@code @MapperScan}：扫描 Mapper 接口，省去每个接口都写 @Mapper 的麻烦。
 * {@code @EnableScheduling}：开启定时任务，订单超时自动取消依赖它。
 */
@SpringBootApplication
@MapperScan("com.petcare.mapper")
@EnableScheduling
public class PetCareApplication {

    public static void main(String[] args) {
        // 在 Spring/MySQL 连接池初始化之前统一 JVM 时间基准，避免应用时间与数据库 NOW() 相差 8 小时。
        TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of("Asia/Shanghai")));
        SpringApplication.run(PetCareApplication.class, args);
        System.out.println("=================================================");
        System.out.println("  智慧宠物服务上门平台后端启动成功");
        System.out.println("  接口地址: http://localhost:8080/api");
        System.out.println("=================================================");
    }
}
