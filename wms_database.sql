-- ===============================
-- WMS 数据库（按你给的字段顺序重写）
-- 可直接导入旧数据；序号使用自增ID（从1开始，后续自动接续）
-- ===============================

CREATE DATABASE IF NOT EXISTS `wms_db` CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE `wms_db`;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS `t_io_overview`;
DROP TABLE IF EXISTS `t_basic_info`;
DROP TABLE IF EXISTS `t_outbound`;
DROP TABLE IF EXISTS `t_inbound`;
DROP TABLE IF EXISTS `t_inventory`;
DROP TABLE IF EXISTS `t_user`;

SET FOREIGN_KEY_CHECKS = 1;

-- 1) 用户表（登录/注册/审核/权限）
CREATE TABLE `t_user` (
  `id` INT NOT NULL AUTO_INCREMENT COMMENT '序号',
  `username` VARCHAR(50) NOT NULL COMMENT '用户名',
  `password` VARCHAR(255) NOT NULL COMMENT '密码(MD5加密)',
  `role_type` INT NOT NULL DEFAULT 0 COMMENT '角色: 0=普通用户,1=库存管理员,2=超级管理员',
  `status` INT NOT NULL DEFAULT 0 COMMENT '状态: 0=待审核,1=通过,2=拒绝',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- admin5687 的 MD5: 20c9220233f13bfc9b4dddad65a617cc
INSERT INTO `t_user` (`username`, `password`, `role_type`, `status`)
VALUES ('admin', '20c9220233f13bfc9b4dddad65a617cc', 2, 1);

-- 2) 库存明细表（字段顺序按你的要求）
CREATE TABLE `t_inventory` (
  `id` INT NOT NULL AUTO_INCREMENT COMMENT '序号',
  `product_name` VARCHAR(100) NOT NULL COMMENT '品名',
  `price` DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '价格',
  `specification` VARCHAR(100) DEFAULT NULL COMMENT '规格型号',
  `supplier` VARCHAR(100) DEFAULT NULL COMMENT '供应商',
  `unit` VARCHAR(20) DEFAULT NULL COMMENT '单位(物品)',
  `init_stock_qty` INT NOT NULL DEFAULT 0 COMMENT '期初库存',
  `init_stock_amount` DECIMAL(14,2) NOT NULL DEFAULT 0.00 COMMENT '期初金额',
  `inbound_qty` INT NOT NULL DEFAULT 0 COMMENT '入库数量(汇总)',
  `inbound_amount` DECIMAL(14,2) NOT NULL DEFAULT 0.00 COMMENT '入库金额(汇总)',
  `outbound_qty` INT NOT NULL DEFAULT 0 COMMENT '出库数量(汇总)',
  `outbound_amount` DECIMAL(14,2) NOT NULL DEFAULT 0.00 COMMENT '出库金额(汇总)',
  `current_stock_qty` INT NOT NULL DEFAULT 0 COMMENT '现有库存数量',
  `safe_stock_qty` INT NOT NULL DEFAULT 5 COMMENT '安全库存数量',
  `stock_amount` DECIMAL(14,2) NOT NULL DEFAULT 0.00 COMMENT '库存金额',
  `remark` VARCHAR(255) DEFAULT NULL COMMENT '备注',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_inventory_product` (`product_name`),
  KEY `idx_inventory_stock` (`current_stock_qty`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COMMENT='库存明细表';

-- 3) 入库明细表（字段顺序按你的要求）
CREATE TABLE `t_inbound` (
  `id` INT NOT NULL AUTO_INCREMENT COMMENT '序号',
  `inbound_date` DATE NOT NULL COMMENT '入库日期',
  `product_name` VARCHAR(100) NOT NULL COMMENT '品名',
  `price` DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '价格',
  `specification` VARCHAR(100) DEFAULT NULL COMMENT '规格型号',
  `supplier` VARCHAR(100) DEFAULT NULL COMMENT '供应商',
  `unit` VARCHAR(20) DEFAULT NULL COMMENT '单位',
  `init_stock_qty` INT NOT NULL DEFAULT 0 COMMENT '期初库存',
  `cert_no` VARCHAR(100) DEFAULT NULL COMMENT '产品证书编号',
  `inbound_qty` INT NOT NULL DEFAULT 0 COMMENT '入库数量',
  `inbound_amount` DECIMAL(14,2) NOT NULL DEFAULT 0.00 COMMENT '入库金额',
  `production_date` VARCHAR(50) DEFAULT NULL COMMENT '生产日期(支持年月格式如2024.03)',
  `valid_date` VARCHAR(50) DEFAULT NULL COMMENT '有效期(支持年月格式)',
  `remark` VARCHAR(255) DEFAULT NULL COMMENT '备注',
  `operator` VARCHAR(50) DEFAULT NULL COMMENT '操作人',
  `inventory_id` INT DEFAULT NULL COMMENT '兼容字段: 关联库存ID(可为空)',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_inbound_date` (`inbound_date`),
  KEY `idx_inbound_product` (`product_name`),
  KEY `idx_inbound_inventory` (`inventory_id`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COMMENT='入库明细表';

-- 4) 出库明细表（字段顺序按你的要求）
CREATE TABLE `t_outbound` (
  `id` INT NOT NULL AUTO_INCREMENT COMMENT '序号',
  `outbound_date` DATE NOT NULL COMMENT '出库日期',
  `product_name` VARCHAR(100) NOT NULL COMMENT '品名',
  `price` DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '价格',
  `specification` VARCHAR(100) DEFAULT NULL COMMENT '规格型号',
  `supplier` VARCHAR(100) DEFAULT NULL COMMENT '供应商',
  `unit` VARCHAR(20) DEFAULT NULL COMMENT '单位',
  `outbound_qty` INT NOT NULL DEFAULT 0 COMMENT '出库数量',
  `outbound_amount` DECIMAL(14,2) NOT NULL DEFAULT 0.00 COMMENT '出库金额',
  `purpose` VARCHAR(200) DEFAULT NULL COMMENT '备品用途',
  `remark` VARCHAR(255) DEFAULT NULL COMMENT '备注',
  `operator` VARCHAR(50) DEFAULT NULL COMMENT '操作人',
  `inventory_id` INT DEFAULT NULL COMMENT '兼容字段: 关联库存ID(可为空)',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_outbound_date` (`outbound_date`),
  KEY `idx_outbound_product` (`product_name`),
  KEY `idx_outbound_inventory` (`inventory_id`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COMMENT='出库明细表';

-- 5) 出入库总览表（按年+物品）
CREATE TABLE `t_io_overview` (
  `id` INT NOT NULL AUTO_INCREMENT COMMENT '序号',
  `stat_year` INT NOT NULL COMMENT '统计年份',
  `product_name` VARCHAR(100) NOT NULL COMMENT '物品名称',
  `init_stock_qty` INT NOT NULL DEFAULT 0 COMMENT '期初库存',
  `init_stock_amount` DECIMAL(14,2) NOT NULL DEFAULT 0.00 COMMENT '期初库存金额',

  `m1_in_qty` INT NOT NULL DEFAULT 0, `m1_out_qty` INT NOT NULL DEFAULT 0, `m1_balance_qty` INT NOT NULL DEFAULT 0,
  `m1_in_amount` DECIMAL(14,2) NOT NULL DEFAULT 0.00, `m1_out_amount` DECIMAL(14,2) NOT NULL DEFAULT 0.00, `m1_balance_amount` DECIMAL(14,2) NOT NULL DEFAULT 0.00,
  `m2_in_qty` INT NOT NULL DEFAULT 0, `m2_out_qty` INT NOT NULL DEFAULT 0, `m2_balance_qty` INT NOT NULL DEFAULT 0,
  `m2_in_amount` DECIMAL(14,2) NOT NULL DEFAULT 0.00, `m2_out_amount` DECIMAL(14,2) NOT NULL DEFAULT 0.00, `m2_balance_amount` DECIMAL(14,2) NOT NULL DEFAULT 0.00,
  `m3_in_qty` INT NOT NULL DEFAULT 0, `m3_out_qty` INT NOT NULL DEFAULT 0, `m3_balance_qty` INT NOT NULL DEFAULT 0,
  `m3_in_amount` DECIMAL(14,2) NOT NULL DEFAULT 0.00, `m3_out_amount` DECIMAL(14,2) NOT NULL DEFAULT 0.00, `m3_balance_amount` DECIMAL(14,2) NOT NULL DEFAULT 0.00,
  `m4_in_qty` INT NOT NULL DEFAULT 0, `m4_out_qty` INT NOT NULL DEFAULT 0, `m4_balance_qty` INT NOT NULL DEFAULT 0,
  `m4_in_amount` DECIMAL(14,2) NOT NULL DEFAULT 0.00, `m4_out_amount` DECIMAL(14,2) NOT NULL DEFAULT 0.00, `m4_balance_amount` DECIMAL(14,2) NOT NULL DEFAULT 0.00,
  `m5_in_qty` INT NOT NULL DEFAULT 0, `m5_out_qty` INT NOT NULL DEFAULT 0, `m5_balance_qty` INT NOT NULL DEFAULT 0,
  `m5_in_amount` DECIMAL(14,2) NOT NULL DEFAULT 0.00, `m5_out_amount` DECIMAL(14,2) NOT NULL DEFAULT 0.00, `m5_balance_amount` DECIMAL(14,2) NOT NULL DEFAULT 0.00,
  `m6_in_qty` INT NOT NULL DEFAULT 0, `m6_out_qty` INT NOT NULL DEFAULT 0, `m6_balance_qty` INT NOT NULL DEFAULT 0,
  `m6_in_amount` DECIMAL(14,2) NOT NULL DEFAULT 0.00, `m6_out_amount` DECIMAL(14,2) NOT NULL DEFAULT 0.00, `m6_balance_amount` DECIMAL(14,2) NOT NULL DEFAULT 0.00,
  `m7_in_qty` INT NOT NULL DEFAULT 0, `m7_out_qty` INT NOT NULL DEFAULT 0, `m7_balance_qty` INT NOT NULL DEFAULT 0,
  `m7_in_amount` DECIMAL(14,2) NOT NULL DEFAULT 0.00, `m7_out_amount` DECIMAL(14,2) NOT NULL DEFAULT 0.00, `m7_balance_amount` DECIMAL(14,2) NOT NULL DEFAULT 0.00,
  `m8_in_qty` INT NOT NULL DEFAULT 0, `m8_out_qty` INT NOT NULL DEFAULT 0, `m8_balance_qty` INT NOT NULL DEFAULT 0,
  `m8_in_amount` DECIMAL(14,2) NOT NULL DEFAULT 0.00, `m8_out_amount` DECIMAL(14,2) NOT NULL DEFAULT 0.00, `m8_balance_amount` DECIMAL(14,2) NOT NULL DEFAULT 0.00,
  `m9_in_qty` INT NOT NULL DEFAULT 0, `m9_out_qty` INT NOT NULL DEFAULT 0, `m9_balance_qty` INT NOT NULL DEFAULT 0,
  `m9_in_amount` DECIMAL(14,2) NOT NULL DEFAULT 0.00, `m9_out_amount` DECIMAL(14,2) NOT NULL DEFAULT 0.00, `m9_balance_amount` DECIMAL(14,2) NOT NULL DEFAULT 0.00,
  `m10_in_qty` INT NOT NULL DEFAULT 0, `m10_out_qty` INT NOT NULL DEFAULT 0, `m10_balance_qty` INT NOT NULL DEFAULT 0,
  `m10_in_amount` DECIMAL(14,2) NOT NULL DEFAULT 0.00, `m10_out_amount` DECIMAL(14,2) NOT NULL DEFAULT 0.00, `m10_balance_amount` DECIMAL(14,2) NOT NULL DEFAULT 0.00,
  `m11_in_qty` INT NOT NULL DEFAULT 0, `m11_out_qty` INT NOT NULL DEFAULT 0, `m11_balance_qty` INT NOT NULL DEFAULT 0,
  `m11_in_amount` DECIMAL(14,2) NOT NULL DEFAULT 0.00, `m11_out_amount` DECIMAL(14,2) NOT NULL DEFAULT 0.00, `m11_balance_amount` DECIMAL(14,2) NOT NULL DEFAULT 0.00,
  `m12_in_qty` INT NOT NULL DEFAULT 0, `m12_out_qty` INT NOT NULL DEFAULT 0, `m12_balance_qty` INT NOT NULL DEFAULT 0,
  `m12_in_amount` DECIMAL(14,2) NOT NULL DEFAULT 0.00, `m12_out_amount` DECIMAL(14,2) NOT NULL DEFAULT 0.00, `m12_balance_amount` DECIMAL(14,2) NOT NULL DEFAULT 0.00,

  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_year_product` (`stat_year`, `product_name`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COMMENT='出入库总览表';

-- 6) 基础信息表（对应 BasicInfoController）
CREATE TABLE `t_basic_info` (
  `id` INT NOT NULL AUTO_INCREMENT COMMENT '序号',
  `product_name` VARCHAR(100) NOT NULL COMMENT '品名',
  `price` DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '价格',
  `specification` VARCHAR(100) DEFAULT NULL COMMENT '规格型号',
  `supplier` VARCHAR(100) DEFAULT NULL COMMENT '供应商',
  `unit` VARCHAR(20) DEFAULT NULL COMMENT '单位',
  `init_stock` INT NOT NULL DEFAULT 0 COMMENT '期初库存',
  `cert_no` VARCHAR(100) DEFAULT NULL COMMENT '产品证书编号',
  `prod_date` VARCHAR(50) DEFAULT NULL COMMENT '生产日期(支持年月格式如2024.03)',
  `valid_date` VARCHAR(50) DEFAULT NULL COMMENT '有效期(支持年月格式)',
  `remark` VARCHAR(255) DEFAULT NULL COMMENT '备注',
  `amount` DECIMAL(14,2) NOT NULL DEFAULT 0.00 COMMENT '金额',
  `category` VARCHAR(100) DEFAULT NULL COMMENT '分类',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_basic_product` (`product_name`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COMMENT='基础信息表';

-- 你给的示例数据（可直接执行）
INSERT INTO `t_basic_info`
(`id`, `product_name`, `price`, `specification`, `supplier`, `unit`, `init_stock`, `cert_no`, `prod_date`, `valid_date`, `remark`, `amount`, `category`, `create_time`)
VALUES
(1, '口粮（荣盛）', 5.50, 'KL-99', '嘉兴荣盛', '包', 285, NULL, NULL, NULL, NULL, 1567.50, NULL, '2026-03-22 18:27:08');

-- 7) 用于直观查询的视图（可直接看效果）
CREATE OR REPLACE VIEW `v_inventory_warning` AS
SELECT
  id,
  product_name,
  current_stock_qty,
  safe_stock_qty,
  CASE
    WHEN current_stock_qty < 0 THEN '小于0'
    WHEN current_stock_qty = 0 THEN '等于0'
    WHEN current_stock_qty < 5 THEN '小于5'
    ELSE '正常'
  END AS warning_level
FROM t_inventory;

-- ========================================
-- 如果已有数据库需要修改日期字段类型，执行以下语句：
-- ========================================
-- ALTER TABLE `t_basic_info` MODIFY COLUMN `prod_date` VARCHAR(50) DEFAULT NULL COMMENT '生产日期(支持年月格式如2024.03)';
-- ALTER TABLE `t_basic_info` MODIFY COLUMN `valid_date` VARCHAR(50) DEFAULT NULL COMMENT '有效期(支持年月格式)';
-- ALTER TABLE `t_inbound` MODIFY COLUMN `production_date` VARCHAR(50) DEFAULT NULL COMMENT '生产日期(支持年月格式如2024.03)';
-- ALTER TABLE `t_inbound` MODIFY COLUMN `valid_date` VARCHAR(50) DEFAULT NULL COMMENT '有效期(支持年月格式)';
