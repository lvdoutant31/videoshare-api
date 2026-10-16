package com.yingliu.service.app.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yingliu.domain.user.SysUserAgreement;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SysUserAgreementMapper extends BaseMapper<SysUserAgreement> {

    @Select("SELECT * FROM sys_user_agreement WHERE user_id = #{userId} AND agreement_type = #{type} ORDER BY agreed_at DESC LIMIT 1")
    SysUserAgreement selectLatest(@Param("userId") long userId, @Param("type") String type);
}
