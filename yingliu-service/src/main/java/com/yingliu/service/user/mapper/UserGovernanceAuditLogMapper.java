package com.yingliu.service.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yingliu.domain.user.management.UserGovernanceAuditLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserGovernanceAuditLogMapper extends BaseMapper<UserGovernanceAuditLog> { }
