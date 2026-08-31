package com.yingliu.service.content.tag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yingliu.domain.content.tag.ContentTag;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ContentTagMapper extends BaseMapper<ContentTag> {
    @Select("SELECT COUNT(*) FROM video_tag_map WHERE tag_id = #{tagId}")
    long countVideoReferences(Long tagId);
}
