package com.wsy.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.wsy.common.AuthSupport;
import com.wsy.common.Result;
import com.wsy.entity.User;
import com.wsy.mapper.UserMapper;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.DigestUtils;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/user")
public class UserController {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private AuthSupport authSupport;

    // 登录接口
    @PostMapping("/login")
    public Result<?> login(@RequestBody Map<String, String> map, HttpServletResponse response) {
        String username = map.get("username");
        String password = map.get("password");

        // 使用 MD5 加密用户输入的密码进行比对
        String md5Password = DigestUtils.md5DigestAsHex(password.getBytes());

        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("username", username).eq("password", md5Password);
        User user = userMapper.selectOne(queryWrapper);

        if (user == null) {
            return Result.error("用户名或密码错误");
        }
        if (user.getStatus() == 0) {
            return Result.error("账号正在审核中，请联系管理员");
        }
        if (user.getStatus() == 2) {
            return Result.error("账号审核被拒绝");
        }
        
        user.setPassword(null); // 返回时隐藏密码
        if (user.getPermissionConfig() == null || user.getPermissionConfig().isBlank()) {
            user.setPermissionConfig(defaultPermissionConfig(user.getRoleType()));
        }

        Cookie cookie = new Cookie("wms_user_id", String.valueOf(user.getId()));
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setMaxAge(-1);
        response.addCookie(cookie);
        authSupport.registerLogin(user.getId());

        return Result.success(user);
    }

    @PostMapping("/logout")
    public Result<?> logout(HttpServletRequest request, HttpServletResponse response) {
        try {
            User user = authSupport.requireLogin(request);
            authSupport.clearLogin(user.getId());
        } catch (Exception ignored) {
        }
        Cookie cookie = new Cookie("wms_user_id", "");
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setMaxAge(0);
        response.addCookie(cookie);
        return Result.success("已退出登录");
    }

    // 注册接口
    @PostMapping("/register")
    public Result<?> register(@RequestBody User user) {
        if (user.getUsername() == null || user.getUsername().isBlank() || user.getPassword() == null || user.getPassword().isBlank()) {
            return Result.error("用户名和密码不能为空");
        }

        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("username", user.getUsername());
        if (userMapper.selectOne(queryWrapper) != null) {
            return Result.error("用户名已存在");
        }

        // 默认注册为普通用户，状态为待审核
        user.setPassword(DigestUtils.md5DigestAsHex(user.getPassword().getBytes()));
        user.setRoleType(0);
        user.setStatus(0);
        user.setCreateTime(new Date());
        
        userMapper.insert(user);
        return Result.success("注册成功，请等待管理员审核");
    }

    @GetMapping("/me")
    public Result<?> me(HttpServletRequest request) {
        User user = authSupport.requireLogin(request);
        user.setPassword(null);
        if (user.getPermissionConfig() == null || user.getPermissionConfig().isBlank()) {
            user.setPermissionConfig(defaultPermissionConfig(user.getRoleType()));
        }
        return Result.success(user);
    }

    @GetMapping("/pending")
    public Result<?> pendingUsers(HttpServletRequest request) {
        authSupport.requireRole(request, 2);
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.eq("status", 0).orderByDesc("create_time");
        List<User> users = userMapper.selectList(wrapper).stream().peek(item -> {
            item.setPassword(null);
            if (item.getPermissionConfig() == null || item.getPermissionConfig().isBlank()) {
                item.setPermissionConfig(defaultPermissionConfig(item.getRoleType()));
            }
        }).collect(Collectors.toList());
        return Result.success(users);
    }

    @GetMapping("/list")
    public Result<?> listUsers(HttpServletRequest request) {
        authSupport.requireRole(request, 2);
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.orderByDesc("create_time");
        List<User> users = userMapper.selectList(wrapper).stream().peek(item -> {
            item.setPassword(null);
            if (item.getPermissionConfig() == null || item.getPermissionConfig().isBlank()) {
                item.setPermissionConfig(defaultPermissionConfig(item.getRoleType()));
            }
        }).collect(Collectors.toList());
        return Result.success(users);
    }

    @PostMapping("/audit")
    public Result<?> auditUser(HttpServletRequest request, @RequestBody Map<String, Object> map) {
        authSupport.requireRole(request, 2);
        Integer userId = toInt(map.get("userId"));
        Integer status = toInt(map.get("status"));
        Integer roleType = map.get("roleType") == null ? 0 : toInt(map.get("roleType"));

        if (userId == null || status == null || !(status == 1 || status == 2)) {
            return Result.error("参数错误");
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            return Result.error("用户不存在");
        }
        if ("admin".equals(user.getUsername()) && status == 2) {
            return Result.error("不能拒绝初始管理员账号");
        }

        user.setStatus(status);
        if (status == 1) {
            if (roleType < 0 || roleType > 2) {
                return Result.error("角色类型无效");
            }
            user.setRoleType(roleType);
            if (user.getPermissionConfig() == null || user.getPermissionConfig().isBlank()) {
                user.setPermissionConfig(defaultPermissionConfig(roleType));
            }
        }
        userMapper.updateById(user);
        return Result.success("审核操作成功");
    }

    @PostMapping("/role")
    public Result<?> changeRole(HttpServletRequest request, @RequestBody Map<String, Object> map) {
        authSupport.requireRole(request, 2);
        Integer userId = toInt(map.get("userId"));
        Integer roleType = toInt(map.get("roleType"));

        if (userId == null || roleType == null || roleType < 0 || roleType > 2) {
            return Result.error("参数错误");
        }

        User user = userMapper.selectById(userId);
        if (user == null) {
            return Result.error("用户不存在");
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            return Result.error("仅可修改已通过审核的账号角色");
        }
        if ("admin".equals(user.getUsername()) && roleType != 2) {
            return Result.error("不能修改初始管理员角色");
        }

        user.setRoleType(roleType);
        if (user.getPermissionConfig() == null || user.getPermissionConfig().isBlank()) {
            user.setPermissionConfig(defaultPermissionConfig(roleType));
        }
        userMapper.updateById(user);
        return Result.success("角色分配成功");
    }

    @GetMapping("/permissions/{userId}")
    public Result<?> getPermissions(HttpServletRequest request, @PathVariable Integer userId) {
        authSupport.requireRole(request, 2);
        User user = userMapper.selectById(userId);
        if (user == null) {
            return Result.error("用户不存在");
        }
        Map<String, Object> data = new HashMap<>();
        data.put("userId", user.getId());
        data.put("username", user.getUsername());
        data.put("permissionConfig", user.getPermissionConfig() == null || user.getPermissionConfig().isBlank()
                ? defaultPermissionConfig(user.getRoleType())
                : user.getPermissionConfig());
        return Result.success(data);
    }

    @PostMapping("/permissions")
    public Result<?> savePermissions(HttpServletRequest request, @RequestBody Map<String, Object> map) {
        authSupport.requireRole(request, 2);
        Integer userId = toInt(map.get("userId"));
        String permissionConfig = map.get("permissionConfig") == null ? null : String.valueOf(map.get("permissionConfig"));
        if (userId == null || permissionConfig == null || permissionConfig.isBlank()) {
            return Result.error("参数错误");
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            return Result.error("用户不存在");
        }
        user.setPermissionConfig(permissionConfig);
        userMapper.updateById(user);
        return Result.success("权限配置保存成功");
    }

    private String defaultPermissionConfig(Integer roleType) {
        int role = roleType == null ? 0 : roleType;
        if (role >= 2) {
            return "{\"moduleView\":{\"dashboard\":true,\"basicInfo\":true,\"inbound\":true,\"outbound\":true,\"inventory\":true,\"overview\":true,\"purchase\":true},\"moduleEdit\":{\"basicInfo\":true,\"inbound\":true,\"outbound\":true,\"inventory\":true,\"purchase\":true},\"amountView\":{\"basicInfo\":true,\"inbound\":true,\"outbound\":true,\"inventory\":true,\"overview\":true}}";
        }
        if (role == 1) {
            return "{\"moduleView\":{\"dashboard\":true,\"basicInfo\":true,\"inbound\":true,\"outbound\":true,\"inventory\":true,\"overview\":true,\"purchase\":true},\"moduleEdit\":{\"basicInfo\":true,\"inbound\":true,\"outbound\":true,\"inventory\":true,\"purchase\":true},\"amountView\":{\"basicInfo\":false,\"inbound\":false,\"outbound\":false,\"inventory\":false,\"overview\":false}}";
        }
        return "{\"moduleView\":{\"dashboard\":true,\"basicInfo\":true,\"inbound\":true,\"outbound\":true,\"inventory\":true,\"overview\":true,\"purchase\":true},\"moduleEdit\":{\"basicInfo\":false,\"inbound\":false,\"outbound\":false,\"inventory\":false,\"purchase\":false},\"amountView\":{\"basicInfo\":false,\"inbound\":false,\"outbound\":false,\"inventory\":false,\"overview\":false}}";
    }

    private Integer toInt(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return null;
        }
    }
}