package com.wsy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("t_operation_log")
public class OperationLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String operator;        // 操作人
    private String opType;          // ADD/UPDATE/DELETE/EXPORT
    private String module;          // inbound/outbound/inventory/template/basic_info
    private String description;     // 操作描述
    private Integer targetId;       // 目标记录ID
    private Date createTime;
}
