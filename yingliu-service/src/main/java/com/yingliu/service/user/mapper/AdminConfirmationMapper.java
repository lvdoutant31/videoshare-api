package com.yingliu.service.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yingliu.domain.user.management.AdminConfirmation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface AdminConfirmationMapper extends BaseMapper<AdminConfirmation> {
    @Update("""
            UPDATE sys_admin_confirmation
            SET consumed_at = #{now}
            WHERE token_hash = #{tokenHash}
              AND actor_admin_id = #{actorAdminId}
              AND action = #{action}
              AND target_type = #{targetType}
              AND target_id = #{targetId}
              AND consumed_at IS NULL
              AND expires_at > #{now}
            """)
    int consume(@Param("tokenHash") String tokenHash, @Param("actorAdminId") long actorAdminId,
                @Param("action") String action, @Param("targetType") String targetType,
                @Param("targetId") long targetId, @Param("now") LocalDateTime now);
}
