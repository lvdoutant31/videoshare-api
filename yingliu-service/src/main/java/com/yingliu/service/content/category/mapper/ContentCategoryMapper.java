package com.yingliu.service.content.category.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yingliu.domain.content.category.ContentCategory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ContentCategoryMapper extends BaseMapper<ContentCategory> {
    @Select("SELECT COUNT(*) FROM video_category_map WHERE category_id = #{categoryId}")
    long countVideoReferences(Long categoryId);

    /** Query soft-deleted records, bypassing @TableLogic auto-filter. */
    @Select("SELECT * FROM content_category WHERE deleted = 1 " +
            "AND (#{keyword} IS NULL OR #{keyword} = '' OR name LIKE CONCAT('%',#{keyword},'%')) " +
            "ORDER BY updated_at DESC LIMIT #{offset}, #{pageSize}")
    List<ContentCategory> selectDeleted(@Param("offset") long offset, @Param("pageSize") long pageSize, @Param("keyword") String keyword);

    /** Count soft-deleted records, bypassing @TableLogic auto-filter. */
    @Select("SELECT COUNT(*) FROM content_category WHERE deleted = 1 " +
            "AND (#{keyword} IS NULL OR #{keyword} = '' OR name LIKE CONCAT('%',#{keyword},'%'))")
    long countDeleted(@Param("keyword") String keyword);

    /** Restore a soft-deleted record back to active. */
    @Update("UPDATE content_category SET deleted = 0, updated_at = NOW() WHERE id = #{id} AND deleted = 1")
    int restoreById(@Param("id") Long id);

    /** Check name uniqueness among non-deleted records (for restore conflict detection). */
    @Select("SELECT COUNT(*) FROM content_category WHERE name = #{name} AND deleted = 0")
    long countActiveByName(@Param("name") String name);
}
