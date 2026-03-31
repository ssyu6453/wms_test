package com.wsy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("t_doc_template")
public class DocTemplate {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private String templateType;    // inbound/outbound/completion/settlement/quote
    private String templateName;
    private String prefix;          // 单据前缀
    private String dateFormat;      // YYYYMMDD/YYMMDD/空
    private Integer seqLength;      // 流水号位数
    private String title;           // 单据标题
    private String companyName;     // 公司名称
    private String layoutType;      // standard/compact/detailed
    private String fieldConfig;     // JSON字符串
    private String footerConfig;    // JSON字符串
    private Integer isDefault;      // 0=否,1=是
    private String createBy;
    private Date createTime;
    private Date updateTime;
}
