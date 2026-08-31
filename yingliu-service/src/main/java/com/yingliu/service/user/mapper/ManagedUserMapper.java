package com.yingliu.service.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yingliu.domain.user.management.ManagedUser;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ManagedUserMapper extends BaseMapper<ManagedUser> { }
