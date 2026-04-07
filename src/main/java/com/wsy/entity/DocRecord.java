package com.wsy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("t_doc_record")
public class DocRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String type;
    private String docNo;
    private String fileName;
    private String relativePath;
    private String status;
    private String sourceIds;
    private String operator;
    private Date createdAt;
    private Date voidTime;
}
