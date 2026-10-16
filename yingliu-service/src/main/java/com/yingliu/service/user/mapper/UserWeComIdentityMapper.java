package com.yingliu.service.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yingliu.domain.user.management.UserWeComIdentity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserWeComIdentityMapper extends BaseMapper<UserWeComIdentity> {
    @Select("SELECT * FROM sys_user_wecom_identity WHERE corp_id = #{corpId} AND wecom_user_id_hmac = #{hmac}")
    UserWeComIdentity findMember(@Param("corpId") String corpId, @Param("hmac") String hmac);


    @Select("SELECT COUNT(*) FROM sys_user_wecom_identity WHERE user_id = #{userId} AND bind_status = 'ACTIVE'")
    int countActiveBindings(@Param("userId") long userId);
}
