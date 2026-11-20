package com.petcare.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户实体，对应 t_user。
 */
@Data
public class User {

    private Long id;
    private String username;

    /**
     * 密码永不返回给前端。
     * @JsonIgnore 只影响 JSON 序列化，MyBatis 读写不受影响。
     */
    @JsonIgnore
    private String password;

    private String nickname;
    private String phone;
    private String avatar;

    /** 0-普通用户 1-商家管理员 */
    private Integer role;
    /** 1-正常 0-禁用 */
    private Integer status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
