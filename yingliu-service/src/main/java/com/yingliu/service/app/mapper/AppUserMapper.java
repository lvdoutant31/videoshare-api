package com.yingliu.service.app.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yingliu.domain.user.AppUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface AppUserMapper extends BaseMapper<AppUser> {

    @Select("SELECT * FROM sys_user WHERE phone_hmac = #{phoneHmac} AND deleted = 0 LIMIT 1")
    AppUser selectByPhoneHmac(@Param("phoneHmac") String phoneHmac);

    /** 用户是否为正常状态（公开内容可见性校验用） */
    @Select("SELECT COUNT(*) FROM sys_user WHERE id = #{userId} AND deleted = 0 AND status = 'NORMAL'")
    int countNormal(@Param("userId") long userId);

    /** 站内转发接收人搜索：昵称或用户 ID 模糊匹配，按粉丝数排序 */
    @Select("<script>SELECT * FROM sys_user WHERE deleted = 0 AND status = 'NORMAL' "
            + "<if test='q != null and q != \"\"'>AND (nickname LIKE CONCAT('%', #{q}, '%') "
            + "OR CAST(id AS CHAR) LIKE CONCAT('%', #{q}, '%')) </if>"
            + "ORDER BY follower_count DESC, id ASC LIMIT #{limit}</script>")
    List<AppUser> selectByKeyword(@Param("q") String q, @Param("limit") int limit);

    /** 关注数原子增减（关注时 +1 / 取关时 -1，不低于 0） */
    @Update("UPDATE sys_user SET following_count = GREATEST(0, CAST(following_count AS SIGNED) + #{delta}), updated_at = NOW(3) WHERE id = #{userId}")
    int adjustFollowingCount(@Param("userId") long userId, @Param("delta") int delta);

    /** 粉丝数原子增减 */
    @Update("UPDATE sys_user SET follower_count = GREATEST(0, CAST(follower_count AS SIGNED) + #{delta}), updated_at = NOW(3) WHERE id = #{userId}")
    int adjustFollowerCount(@Param("userId") long userId, @Param("delta") int delta);
}
