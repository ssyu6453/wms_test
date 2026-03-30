package com.wsy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wsy.entity.BasicInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import java.math.BigDecimal;

@Mapper
public interface BasicInfoMapper extends BaseMapper<BasicInfo> {
    
    // 用于统计期初库存总金额
    @Select("SELECT SUM(amount) FROM t_basic_info")
    BigDecimal selectTotalAmount();
}