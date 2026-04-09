package com.wsy;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.wsy.entity.User;
import com.wsy.mapper.UserMapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.DigestUtils;

@SpringBootApplication
@MapperScan("com.wsy.mapper")
public class WmsApplication implements CommandLineRunner {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public static void main(String[] args) {
        SpringApplication.run(WmsApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        ensureUserPermissionColumn();
        ensureDocRecordTable();
        ensureTemplateProfileTable();

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
            if (adminUser.getPermissionConfig() == null || adminUser.getPermissionConfig().isBlank()) {
                adminUser.setPermissionConfig(defaultPermissionConfig(2));
            }
            userMapper.updateById(adminUser);
            System.out.println("====== 已自动修复超级管理员 admin 密码为 admin5687 ======");
        }
    }

    private void ensureUserPermissionColumn() {
        Integer exists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_user' AND COLUMN_NAME = 'permission_config'",
                Integer.class
        );
        if (exists == null || exists == 0) {
            jdbcTemplate.execute("ALTER TABLE t_user ADD COLUMN permission_config TEXT NULL COMMENT '细分权限JSON'");
            System.out.println("====== 已自动补齐 t_user.permission_config 字段 ======");
        }
    }

    private void ensureDocRecordTable() {
        Integer exists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_doc_record'",
                Integer.class
        );
        if (exists == null || exists == 0) {
            jdbcTemplate.execute("CREATE TABLE t_doc_record (" +
                    "id BIGINT NOT NULL AUTO_INCREMENT COMMENT '单据记录ID'," +
                    "type VARCHAR(20) NOT NULL COMMENT '类型: inbound/outbound'," +
                    "doc_no VARCHAR(80) NOT NULL COMMENT '单据编号'," +
                    "file_name VARCHAR(255) DEFAULT NULL COMMENT '文件名'," +
                    "relative_path VARCHAR(255) NOT NULL COMMENT '相对路径, 例: source/xxx.pdf'," +
                    "status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE/VOID'," +
                    "source_ids JSON DEFAULT NULL COMMENT '来源业务ID数组'," +
                    "operator VARCHAR(50) DEFAULT NULL COMMENT '操作员'," +
                    "created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'," +
                    "void_time DATETIME DEFAULT NULL COMMENT '作废时间'," +
                    "PRIMARY KEY (id)," +
                    "KEY idx_doc_record_type (type)," +
                    "KEY idx_doc_record_doc_no (doc_no)," +
                    "KEY idx_doc_record_status (status)," +
                    "KEY idx_doc_record_created_at (created_at)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='单据记录表'");
            System.out.println("====== 已自动创建 t_doc_record 表 ======");
        }
    }

    private void ensureTemplateProfileTable() {
        Integer exists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_template_profile'",
                Integer.class
        );
        if (exists == null || exists == 0) {
            jdbcTemplate.execute("CREATE TABLE t_template_profile (" +
                    "id INT NOT NULL AUTO_INCREMENT COMMENT '模板配置ID'," +
                    "template_type VARCHAR(20) NOT NULL COMMENT '模板类型: inbound/outbound/purchase'," +
                    "title VARCHAR(50) DEFAULT NULL COMMENT '模板标题'," +
                    "prefix VARCHAR(10) DEFAULT '' COMMENT '单据前缀'," +
                    "date_format VARCHAR(20) DEFAULT 'YYYYMMDD' COMMENT '日期格式'," +
                    "seq_length INT DEFAULT 2 COMMENT '流水号位数'," +
                    "company_name VARCHAR(100) DEFAULT '' COMMENT '公司名称'," +
                    "field_config JSON COMMENT '字段配置JSON'," +
                    "footer_config JSON COMMENT '页脚配置JSON'," +
                    "updated_by VARCHAR(50) DEFAULT NULL COMMENT '更新人'," +
                    "update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'," +
                    "PRIMARY KEY (id)," +
                    "UNIQUE KEY uk_template_type (template_type)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模板配置持久化表'");
            System.out.println("====== 已自动创建 t_template_profile 表 ======");
        }
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
}