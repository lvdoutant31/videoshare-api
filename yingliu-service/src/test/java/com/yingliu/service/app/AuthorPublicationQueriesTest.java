package com.yingliu.service.app;

import com.yingliu.service.app.mapper.AppUserMapper;
import com.yingliu.service.app.mapper.UserVideoLikeMapper;
import com.yingliu.service.app.mapper.UserVideoFavoriteMapper;
import com.yingliu.service.content.video.mapper.VideoMapper;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AuthorPublicationQueriesTest {
    private String sql(Class<?> mapper, String method, Map<String, Object> params) {
        Configuration configuration = new Configuration();
        configuration.addMapper(mapper);
        return configuration.getMappedStatement(mapper.getName() + "." + method).getBoundSql(params).getSql();
    }

    @Test
    void authorCountWorksLikesAndFavoritesUseTheSameVisibilityAndMediaRules() {
        var params = Map.<String, Object>of("authorId", 7L, "userId", 7L, "cursorId", 88L, "limit", 19, "authorIds", List.of(7L));
        for (String query : List.of(sql(VideoMapper.class, "selectPublicIdsByAuthor", params),
                sql(VideoMapper.class, "countPublicByAuthor", params), sql(VideoMapper.class, "countPublicByAuthors", params),
                sql(UserVideoLikeMapper.class, "selectPublicIds", params), sql(UserVideoFavoriteMapper.class, "selectPublicIds", params))) {
            assertTrue(query.contains("v.audit_status = 'APPROVED'"));
            assertTrue(query.contains("v.publish_status = 'PUBLISHED'"));
            assertTrue(query.contains("v.visibility = 'PUBLIC'"));
            assertTrue(query.contains("u.status = 'NORMAL'"));
            assertTrue(query.contains("ready_media.storage_state = 'READY'"));
            assertTrue(query.contains("v.original_media_id IS NULL"));
        }
    }

    @Test
    void authorSearchBindsNicknameAndCursorAndExcludesUnavailableAccounts() {
        String query = sql(AppUserMapper.class, "searchAuthors", Map.of("keyword", "' OR 1=1 --", "cursorId", 99L, "limit", 21));
        assertTrue(query.contains("status = 'NORMAL'"));
        assertTrue(query.contains("id < ?"));
        assertTrue(query.contains("ORDER BY id DESC LIMIT ?"));
        assertFalse(query.contains("OR 1=1 --"));
    }
}
