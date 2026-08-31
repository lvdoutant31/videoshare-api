package com.yingliu.service.content.category.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yingliu.domain.content.category.ContentCategory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ContentCategoryMapper extends BaseMapper<ContentCategory> {
    @Select("SELECT COUNT(*) FROM video_category_map WHERE category_id = #{categoryId}")
    long countVideoReferences(Long categoryId);
}
