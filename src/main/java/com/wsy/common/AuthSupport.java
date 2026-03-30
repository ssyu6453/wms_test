package com.wsy.common;

import com.wsy.entity.User;
import com.wsy.mapper.UserMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class AuthSupport {

    @Autowired
    private UserMapper userMapper;

    public User requireLogin(HttpServletRequest request) {
        String userIdStr = request.getHeader("X-User-Id");
        if (userIdStr == null || userIdStr.isBlank()) {
            throw new RuntimeException("未登录或登录已失效");
        }
        Integer userId;
        try {
            userId = Integer.parseInt(userIdStr);
        } catch (NumberFormatException e) {
            throw new RuntimeException("登录信息无效");
        }

        User user = userMapper.selectById(userId);
        if (user == null || user.getStatus() == null || user.getStatus() != 1) {
            throw new RuntimeException("账号不存在或未审核通过");
        }
        return user;
    }

    public User requireRole(HttpServletRequest request, int minRole) {
        User user = requireLogin(request);
        if (user.getRoleType() == null || user.getRoleType() < minRole) {
            throw new RuntimeException("无权限执行该操作");
        }
        return user;
    }
}
