package com.wsy.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.wsy.common.AuthSupport;
import com.wsy.common.Result;
import com.wsy.entity.DocTemplate;
import com.wsy.entity.OperationLog;
import com.wsy.entity.TemplateProfile;
import com.wsy.entity.User;
import com.wsy.mapper.DocTemplateMapper;
import com.wsy.mapper.OperationLogMapper;
import com.wsy.mapper.TemplateProfileMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.List;

@RestController
@RequestMapping("/api/template")
public class DocTemplateController {

    @Autowired
    private DocTemplateMapper templateMapper;

    @Autowired
    private OperationLogMapper logMapper;

    @Autowired
    private TemplateProfileMapper templateProfileMapper;

    @Autowired
    private AuthSupport authSupport;

    @GetMapping("/list")
    public Result<?> list(HttpServletRequest request, @RequestParam(required = false) String type) {
        authSupport.requireLogin(request);
        QueryWrapper<DocTemplate> wrapper = new QueryWrapper<>();
        if (type != null && !type.isBlank()) {
            wrapper.eq("template_type", type);
        }
        wrapper.orderByDesc("is_default").orderByAsc("id");
        List<DocTemplate> list = templateMapper.selectList(wrapper);
        return Result.success(list);
    }

    @GetMapping("/{id}")
    public Result<?> getById(HttpServletRequest request, @PathVariable Integer id) {
        authSupport.requireLogin(request);
        DocTemplate template = templateMapper.selectById(id);
        if (template == null) {
            return Result.error("模板不存在");
        }
        return Result.success(template);
    }

    @GetMapping("/default")
    public Result<?> getDefault(HttpServletRequest request, @RequestParam String type) {
        authSupport.requireLogin(request);

        QueryWrapper<TemplateProfile> profileWrapper = new QueryWrapper<>();
        profileWrapper.eq("template_type", type).last("LIMIT 1");
        TemplateProfile profile = templateProfileMapper.selectOne(profileWrapper);
        if (profile != null) {
            DocTemplate data = new DocTemplate();
            data.setId(profile.getId());
            data.setTemplateType(profile.getTemplateType());
            data.setTemplateName((profile.getTitle() == null || profile.getTitle().isBlank()) ? "默认模板" : profile.getTitle());
            data.setPrefix(profile.getPrefix());
            data.setDateFormat(profile.getDateFormat());
            data.setSeqLength(profile.getSeqLength());
            data.setTitle(profile.getTitle());
            data.setCompanyName(profile.getCompanyName());
            data.setLayoutType("standard");
            data.setFieldConfig(profile.getFieldConfig());
            data.setFooterConfig(profile.getFooterConfig());
            data.setIsDefault(1);
            data.setCreateBy(profile.getUpdatedBy());
            data.setUpdateTime(profile.getUpdateTime());
            return Result.success(data);
        }

        QueryWrapper<DocTemplate> wrapper = new QueryWrapper<>();
        wrapper.eq("template_type", type).eq("is_default", 1);
        DocTemplate template = templateMapper.selectOne(wrapper);
        return Result.success(template);
    }

    @PostMapping("/save")
    public Result<?> save(HttpServletRequest request, @RequestBody DocTemplate template) {
        User user = authSupport.requireRole(request, 1);

        if (template.getTemplateType() == null || template.getTemplateType().isBlank()) {
            return Result.error("模板类型不能为空");
        }
        if (template.getTemplateName() == null || template.getTemplateName().isBlank()) {
            return Result.error("模板名称不能为空");
        }

        boolean isNew = template.getId() == null;
        template.setCreateBy(user.getUsername());

        if (isNew) {
            templateMapper.insert(template);
            addLog(user.getUsername(), "ADD", "template", "新增模板: " + template.getTemplateName(), template.getId());
        } else {
            templateMapper.updateById(template);
            addLog(user.getUsername(), "UPDATE", "template", "更新模板: " + template.getTemplateName(), template.getId());
        }

        QueryWrapper<TemplateProfile> profileWrapper = new QueryWrapper<>();
        profileWrapper.eq("template_type", template.getTemplateType()).last("LIMIT 1");
        TemplateProfile profile = templateProfileMapper.selectOne(profileWrapper);
        if (profile == null) {
            profile = new TemplateProfile();
            profile.setTemplateType(template.getTemplateType());
        }
        profile.setTitle(template.getTitle());
        profile.setPrefix(template.getPrefix());
        profile.setDateFormat(template.getDateFormat());
        profile.setSeqLength(template.getSeqLength());
        profile.setCompanyName(template.getCompanyName());
        profile.setFieldConfig(template.getFieldConfig());
        profile.setFooterConfig(template.getFooterConfig());
        profile.setUpdatedBy(user.getUsername());
        profile.setUpdateTime(new Date());
        if (profile.getId() == null) {
            templateProfileMapper.insert(profile);
        } else {
            templateProfileMapper.updateById(profile);
        }

        return Result.success(isNew ? "模板创建成功" : "模板更新成功");
    }

    @DeleteMapping("/{id}")
    public Result<?> delete(HttpServletRequest request, @PathVariable Integer id) {
        User user = authSupport.requireRole(request, 1);

        DocTemplate template = templateMapper.selectById(id);
        if (template == null) {
            return Result.error("模板不存在");
        }
        if (template.getIsDefault() != null && template.getIsDefault() == 1) {
            return Result.error("不能删除默认模板");
        }

        templateMapper.deleteById(id);
        addLog(user.getUsername(), "DELETE", "template", "删除模板: " + template.getTemplateName(), id);
        return Result.success("模板删除成功");
    }

    @PostMapping("/set-default/{id}")
    public Result<?> setDefault(HttpServletRequest request, @PathVariable Integer id) {
        User user = authSupport.requireRole(request, 1);

        DocTemplate template = templateMapper.selectById(id);
        if (template == null) {
            return Result.error("模板不存在");
        }

        // 先将同类型的所有模板设为非默认
        UpdateWrapper<DocTemplate> wrapper = new UpdateWrapper<>();
        wrapper.eq("template_type", template.getTemplateType()).set("is_default", 0);
        templateMapper.update(null, wrapper);

        // 设置当前模板为默认
        template.setIsDefault(1);
        templateMapper.updateById(template);

        addLog(user.getUsername(), "UPDATE", "template", "设置默认模板: " + template.getTemplateName(), id);
        return Result.success("已设为默认模板");
    }

    private void addLog(String operator, String opType, String module, String description, Integer targetId) {
        OperationLog log = new OperationLog();
        log.setOperator(operator);
        log.setOpType(opType);
        log.setModule(module);
        log.setDescription(description);
        log.setTargetId(targetId);
        log.setCreateTime(new Date());
        logMapper.insert(log);
    }
}
