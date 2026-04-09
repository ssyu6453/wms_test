package com.wsy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("t_template_profile")
public class TemplateProfile {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private String templateType;
    private String title;
    private String prefix;
    private String dateFormat;
    private Integer seqLength;
    private String companyName;
    private String fieldConfig;
    private String footerConfig;
    private String updatedBy;
    private Date updateTime;
}
