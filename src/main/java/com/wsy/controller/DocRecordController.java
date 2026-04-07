package com.wsy.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wsy.common.AuthSupport;
import com.wsy.common.Result;
import com.wsy.entity.DocRecord;
import com.wsy.entity.User;
import com.wsy.mapper.DocRecordMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController
@RequestMapping("/api/doc")
public class DocRecordController {

    private static final Path DOC_ROOT = Paths.get("D:/wsy/project/WMS/source").toAbsolutePath().normalize();

    @Autowired
    private AuthSupport authSupport;

    @Autowired
    private DocRecordMapper docRecordMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostMapping("/upload")
    public Result<?> upload(HttpServletRequest request, @RequestParam("file") MultipartFile file) {
        authSupport.requireLogin(request);
        if (file == null || file.isEmpty()) {
            return Result.error("上传文件不能为空");
        }
        try {
            Files.createDirectories(DOC_ROOT);
            String originalName = file.getOriginalFilename() == null ? "doc.pdf" : file.getOriginalFilename();
            String safeName = sanitizeFileName(originalName);
            String prefix = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
            String storedName = prefix + "_" + safeName;
            Path target = DOC_ROOT.resolve(storedName).normalize();
            if (!target.startsWith(DOC_ROOT)) {
                return Result.error("非法文件路径");
            }
            file.transferTo(target.toFile());

            Map<String, Object> data = new HashMap<>();
            data.put("fileName", safeName);
            data.put("relativePath", "source/" + storedName);
            data.put("absolutePath", target.toString());
            return Result.success(data);
        } catch (Exception e) {
            return Result.error("上传失败: " + e.getMessage());
        }
    }

    @PostMapping("/record/add")
    public Result<?> addRecord(HttpServletRequest request, @RequestBody Map<String, Object> payload) {
        User user = authSupport.requireLogin(request);
        String type = value(payload.get("type"));
        String docNo = value(payload.get("docNo"));
        String fileName = value(payload.get("fileName"));
        String relativePath = value(payload.get("relativePath"));
        String status = value(payload.get("status"));
        if (status == null || status.isBlank()) status = "ACTIVE";

        if (type == null || type.isBlank() || docNo == null || docNo.isBlank() || relativePath == null || relativePath.isBlank()) {
            return Result.error("缺少必要参数");
        }

        String sourceIds = "[]";
        Object sourceObj = payload.get("sourceIds");
        try {
            if (sourceObj != null) {
                sourceIds = objectMapper.writeValueAsString(sourceObj);
            }
        } catch (Exception ignored) {
        }

        DocRecord record = new DocRecord();
        record.setType(type);
        record.setDocNo(docNo);
        record.setFileName(fileName);
        record.setRelativePath(relativePath);
        record.setStatus(status);
        record.setSourceIds(sourceIds);
        record.setOperator(user.getUsername());
        record.setCreatedAt(new Date());
        docRecordMapper.insert(record);

        Map<String, Object> data = toRecordMap(record);
        return Result.success(data);
    }

    @GetMapping("/record/list")
    public Result<?> listRecords(HttpServletRequest request) {
        authSupport.requireLogin(request);
        QueryWrapper<DocRecord> wrapper = new QueryWrapper<>();
        wrapper.orderByDesc("created_at");
        List<DocRecord> list = docRecordMapper.selectList(wrapper);
        List<Map<String, Object>> data = new ArrayList<>();
        for (DocRecord item : list) {
            data.add(toRecordMap(item));
        }
        return Result.success(data);
    }

    @PostMapping("/record/void/{id}")
    public Result<?> voidRecord(HttpServletRequest request, @PathVariable Long id) {
        authSupport.requireLogin(request);
        DocRecord record = docRecordMapper.selectById(id);
        if (record == null) {
            return Result.error("单据记录不存在");
        }
        if (!"VOID".equalsIgnoreCase(record.getStatus())) {
            record.setStatus("VOID");
            record.setVoidTime(new Date());
            docRecordMapper.updateById(record);
        }
        return Result.success("作废成功");
    }

    @PostMapping("/record/voidByBiz")
    public Result<?> voidByBiz(HttpServletRequest request, @RequestBody Map<String, Object> payload) {
        authSupport.requireLogin(request);
        String type = value(payload.get("type"));
        Integer bizId = parseInt(payload.get("bizId"));
        if (type == null || type.isBlank() || bizId == null) {
            return Result.error("参数错误");
        }

        QueryWrapper<DocRecord> wrapper = new QueryWrapper<>();
        wrapper.eq("type", type).eq("status", "ACTIVE");
        List<DocRecord> list = docRecordMapper.selectList(wrapper);
        int count = 0;
        for (DocRecord item : list) {
            if (containsBizId(item.getSourceIds(), bizId)) {
                item.setStatus("VOID");
                item.setVoidTime(new Date());
                docRecordMapper.updateById(item);
                count++;
            }
        }
        return Result.success("已作废 " + count + " 条关联单据");
    }

    @GetMapping("/record/file/{id}")
    public ResponseEntity<?> downloadByRecord(HttpServletRequest request, @PathVariable Long id) {
        authSupport.requireLogin(request);
        DocRecord record = docRecordMapper.selectById(id);
        if (record == null || record.getRelativePath() == null || record.getRelativePath().isBlank()) {
            return ResponseEntity.notFound().build();
        }
        Path target = DOC_ROOT.getParent().resolve(record.getRelativePath()).normalize();
        if (!target.startsWith(DOC_ROOT) || !Files.exists(target)) {
            return ResponseEntity.notFound().build();
        }
        try {
            InputStream inputStream = Files.newInputStream(target);
            String downloadName = record.getFileName() == null || record.getFileName().isBlank() ? target.getFileName().toString() : record.getFileName();
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + downloadName + "\"")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(new InputStreamResource(inputStream));
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body("读取文件失败");
        }
    }

    private Map<String, Object> toRecordMap(DocRecord record) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", record.getId());
        map.put("type", record.getType());
        map.put("docNo", record.getDocNo());
        map.put("fileName", record.getFileName());
        map.put("relativePath", record.getRelativePath());
        map.put("filePath", DOC_ROOT.getParent().resolve(record.getRelativePath()).normalize().toString());
        map.put("status", record.getStatus());
        map.put("sourceIds", safeSourceIds(record.getSourceIds()));
        map.put("operator", record.getOperator());
        map.put("createdAt", record.getCreatedAt());
        map.put("voidTime", record.getVoidTime());
        map.put("downloadUrl", "/api/doc/record/file/" + record.getId());
        return map;
    }

    private List<Integer> safeSourceIds(String sourceIds) {
        if (sourceIds == null || sourceIds.isBlank()) return new ArrayList<>();
        try {
            List<Integer> ids = objectMapper.readValue(sourceIds, new TypeReference<List<Integer>>() {});
            return ids == null ? new ArrayList<>() : ids;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private boolean containsBizId(String sourceIds, Integer bizId) {
        List<Integer> ids = safeSourceIds(sourceIds);
        return ids.contains(bizId);
    }

    private Integer parseInt(Object obj) {
        if (obj == null) return null;
        try {
            return Integer.parseInt(String.valueOf(obj));
        } catch (Exception e) {
            return null;
        }
    }

    private String value(Object obj) {
        return obj == null ? null : String.valueOf(obj);
    }

    private String sanitizeFileName(String fileName) {
        String safe = fileName.replace('\\', '_').replace('/', '_').replace(':', '_').replace('*', '_')
                .replace('?', '_').replace('"', '_').replace('<', '_').replace('>', '_').replace('|', '_');
        if (!safe.toLowerCase().endsWith(".pdf")) {
            safe = safe + ".pdf";
        }
        return safe;
    }
}
