package com.yingliu.service.content.video.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yingliu.domain.content.video.AdminUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AdminUserMapper extends BaseMapper<AdminUser> {
    @Select("SELECT * FROM sys_admin_user WHERE id = #{id} FOR UPDATE")
    AdminUser selectForAuthorBinding(@Param("id") long id);

    @org.apache.ibatis.annotations.Update("UPDATE sys_admin_user SET user_id = #{userId}, updated_at = NOW(3) WHERE id = #{id} AND user_id IS NULL")
    int bindAuthor(@Param("id") long id, @Param("userId") long userId);


    @Select("SELECT r.role_code FROM sys_admin_user_role aur " +
            "JOIN sys_role r ON r.id = aur.role_id " +
            "WHERE aur.admin_user_id = #{adminId} AND r.status = 'NORMAL'")
    List<String> selectRoleCodes(@Param("adminId") Long adminId);

    @Select("SELECT r.id, r.role_code, r.role_name FROM sys_admin_user_role aur " +
            "JOIN sys_role r ON r.id = aur.role_id " +
            "WHERE aur.admin_user_id = #{adminId}")
    List<RoleView> selectRoles(@Param("adminId") Long adminId);

    @Select("SELECT id, role_code, role_name FROM sys_role WHERE status = 'NORMAL' ORDER BY id")
    List<RoleView> selectAllActiveRoles();

    record RoleView(Long id, String roleCode, String roleName) {}
}
