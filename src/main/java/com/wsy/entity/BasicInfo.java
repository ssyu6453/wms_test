package com.wsy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;

@Data
@TableName("t_basic_info")
public class BasicInfo {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private String productName;
    private BigDecimal price;
    private String specification;
    private String supplier;
    private String unit;
    private Integer initStock;
    private String certNo;
    private String prodDate;
    private String validDate;
    private String remark;
    private BigDecimal amount;
    private String category;
}