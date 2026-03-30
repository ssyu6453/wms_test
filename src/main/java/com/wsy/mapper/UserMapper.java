package com.wsy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wsy.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {
}