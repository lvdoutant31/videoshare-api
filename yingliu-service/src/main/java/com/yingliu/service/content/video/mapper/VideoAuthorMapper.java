package com.yingliu.service.content.video.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yingliu.domain.content.video.VideoAuthor;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface VideoAuthorMapper extends BaseMapper<VideoAuthor> {
    @org.apache.ibatis.annotations.Insert("INSERT INTO sys_user (id, nickname, status, deleted, created_at, updated_at) " +
            "VALUES (#{id}, #{nickname}, 'NORMAL', 0, NOW(3), NOW(3))")
    int insertAdminAuthor(@org.apache.ibatis.annotations.Param("id") long id,
                          @org.apache.ibatis.annotations.Param("nickname") String nickname);
}
