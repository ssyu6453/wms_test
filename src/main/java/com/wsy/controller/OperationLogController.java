package com.wsy.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wsy.common.AuthSupport;
import com.wsy.common.Result;
import com.wsy.entity.OperationLog;
import com.wsy.mapper.OperationLogMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/log")
public class OperationLogController {

    @Autowired
    private OperationLogMapper logMapper;

    @Autowired
    private AuthSupport authSupport;

    @GetMapping("/list")
    public Result<?> list(HttpServletRequest request,
                          @RequestParam(defaultValue = "1") Integer page,
                          @RequestParam(defaultValue = "20") Integer size,
                          @RequestParam(required = false) String module,
                          @RequestParam(required = false) String opType,
                          @RequestParam(required = false) String operator) {
        authSupport.requireRole(request, 1);

        QueryWrapper<OperationLog> wrapper = new QueryWrapper<>();
        if (module != null && !module.isBlank()) {
            wrapper.eq("module", module);
        }
        if (opType != null && !opType.isBlank()) {
            wrapper.eq("op_type", opType);
        }
        if (operator != null && !operator.isBlank()) {
            wrapper.like("operator", operator);
        }
        wrapper.orderByDesc("create_time");

        Page<OperationLog> pageObj = new Page<>(page, size);
        Page<OperationLog> result = logMapper.selectPage(pageObj, wrapper);

        Map<String, Object> data = new HashMap<>();
        data.put("list", result.getRecords());
        data.put("total", result.getTotal());
        data.put("page", page);
        data.put("size", size);
        return Result.success(data);
    }

    @GetMapping("/recent")
    public Result<?> recent(HttpServletRequest request, @RequestParam(defaultValue = "10") Integer limit) {
        authSupport.requireRole(request, 1);

        QueryWrapper<OperationLog> wrapper = new QueryWrapper<>();
        wrapper.orderByDesc("create_time").last("LIMIT " + limit);
        return Result.success(logMapper.selectList(wrapper));
    }
}
