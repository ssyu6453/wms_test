package com.wsy.common;

import com.wsy.entity.User;
import com.wsy.mapper.UserMapper;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AuthSupport {

    private static final long SESSION_TIMEOUT_MS = 30L * 60L * 1000L;
    private static final Map<Integer, Long> LOGIN_SESSION = new ConcurrentHashMap<>();

    @Autowired
    private UserMapper userMapper;

    public void registerLogin(Integer userId) {
        if (userId != null) {
            LOGIN_SESSION.put(userId, System.currentTimeMillis());
        }
    }

    public void clearLogin(Integer userId) {
        if (userId != null) {
            LOGIN_SESSION.remove(userId);
        }
    }

    private void ensureActiveSession(Integer userId) {
        Long lastSeen = LOGIN_SESSION.get(userId);
        long now = System.currentTimeMillis();
        if (lastSeen == null) {
            throw new RuntimeException("登录已失效，请重新登录");
        }
        if (now - lastSeen > SESSION_TIMEOUT_MS) {
            LOGIN_SESSION.remove(userId);
            throw new RuntimeException("登录超时，请重新登录");
        }
        LOGIN_SESSION.put(userId, now);
    }

    public User requireLogin(HttpServletRequest request) {
        String userIdStr = request.getHeader("X-User-Id");
        if (userIdStr == null || userIdStr.isBlank()) {
            Cookie[] cookies = request.getCookies();
            if (cookies != null) {
                for (Cookie cookie : cookies) {
                    if ("wms_user_id".equals(cookie.getName())) {
                        userIdStr = cookie.getValue();
                        break;
                    }
                }
            }
        }
        if (userIdStr == null || userIdStr.isBlank()) {
            throw new RuntimeException("未登录或登录已失效");
        }
        Integer userId;
        try {
            userId = Integer.parseInt(userIdStr);
        } catch (NumberFormatException e) {
            throw new RuntimeException("登录信息无效");
        }

        ensureActiveSession(userId);

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
