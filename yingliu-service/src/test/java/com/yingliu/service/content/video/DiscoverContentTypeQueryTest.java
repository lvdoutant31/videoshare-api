package com.yingliu.service.content.video;

import com.yingliu.service.content.video.mapper.VideoMapper;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DiscoverContentTypeQueryTest {
    private String query(String contentType) {
        Configuration configuration = new Configuration();
        configuration.addMapper(VideoMapper.class);
        Map<String, Object> params = new HashMap<>();
        params.put("keyword", "安全"); params.put("keywordPattern", "安全");
        params.put("categoryId", 3L); params.put("tagId", 4L);
        params.put("sort", "LATEST"); params.put("contentType", contentType);
        params.put("limit", 21); params.put("offset", 20L);
        return configuration.getMappedStatement(VideoMapper.class.getName() + ".selectDiscoverVideos")
                .getBoundSql(params).getSql().replaceAll("\\s+", " ");
    }

    @Test
    void contentTypeFiltersAreAppliedBeforeSortingAndPagination() {
        assertThat(query("VIDEO")).contains("AND v.original_media_id IS NOT NULL ORDER BY");
        assertThat(query("ARTICLE")).contains("AND v.original_media_id IS NULL ORDER BY");
        assertThat(query("ALL")).doesNotContain("AND v.original_media_id IS NOT NULL ORDER BY", "AND v.original_media_id IS NULL ORDER BY");
        for (String type : new String[]{"ALL", "VIDEO", "ARTICLE"}) {
            assertThat(query(type)).contains("filter_vc.category_id = ?", "filter_vt.tag_id = ?",
                    "v.title LIKE", "LIMIT ? OFFSET ?", "v.audit_status = 'APPROVED'", "v.visibility = 'PUBLIC'");
        }
    }
}
