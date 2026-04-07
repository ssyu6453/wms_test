package com.wsy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.util.Date;

@Data
@TableName("t_user")
public class User {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private String username;
    private String password;
    private Integer roleType; // 0=普通用户, 1=库存管理员, 2=超级管理员
    private Integer status; // 0=待审核, 1=已审核通过, 2=已拒绝
    private String permissionConfig; // 细分权限JSON
    private Date createTime;
}