package com.wsy;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.wsy.entity.User;
import com.wsy.mapper.UserMapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.util.DigestUtils;

@SpringBootApplication
@MapperScan("com.wsy.mapper")
public class WmsApplication implements CommandLineRunner {

    @Autowired
    private UserMapper userMapper;

    public static void main(String[] args) {
        SpringApplication.run(WmsApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        // 自动检查并修复 admin 账号，确保初始密码一定能够登录
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("username", "admin");
        User adminUser = userMapper.selectOne(queryWrapper);
        String correctMd5 = DigestUtils.md5DigestAsHex("admin5687".getBytes());
        
        if (adminUser == null) {
            adminUser = new User();
            adminUser.setUsername("admin");
            adminUser.setPassword(correctMd5);
            adminUser.setRoleType(2); // 超级管理员
            adminUser.setStatus(1); // 已审核通过
            userMapper.insert(adminUser);
            System.out.println("====== 已自动创建超级管理员 admin 账号 ======");
        } else if (!adminUser.getPassword().equals(correctMd5)) {
            adminUser.setPassword(correctMd5);
            adminUser.setRoleType(2);
            adminUser.setStatus(1);
            userMapper.updateById(adminUser);
            System.out.println("====== 已自动修复超级管理员 admin 密码为 admin5687 ======");
        }
    }
}