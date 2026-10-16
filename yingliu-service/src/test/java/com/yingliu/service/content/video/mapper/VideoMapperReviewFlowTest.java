package com.yingliu.service.content.video.mapper;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertTrue;

class VideoMapperReviewFlowTest {

    @Test
    void decisionsRequireTheClaimedReviewer() throws Exception {
        assertClaimOwnership("approveReview");
        assertClaimOwnership("rejectReview");
        assertClaimOwnership("requestRevision");
    }

    @Test
    void releasingAClaimReturnsItToThePendingQueue() throws Exception {
        Method method = VideoMapper.class.getMethod("releaseReview", long.class, long.class, int.class);
        String sql = String.join(" ", method.getAnnotation(Update.class).value());

        assertTrue(sql.contains("audit_status = 'PENDING_REVIEW'"));
        assertTrue(sql.contains("review_claimed_by = NULL"));
        assertTrue(sql.contains("review_claimed_at = NULL"));
    }

    @Test
    void reviewQueueIncludesPendingAndCurrentReviewersClaimedVideos() throws Exception {
        Method method = VideoMapper.class.getMethod("selectReviewPage", Page.class, String.class,
                Long.class, String.class, long.class);
        String sql = String.join(" ", method.getAnnotation(Select.class).value());

        assertTrue(sql.contains("audit_status = 'PENDING_REVIEW'"));
        assertTrue(sql.contains("audit_status = 'CLAIMED' AND review_claimed_by = #{actorAdminId}"));
    }

    private void assertClaimOwnership(String methodName) throws Exception {
        Method method = VideoMapper.class.getMethod(methodName, long.class, long.class, int.class);
        String sql = String.join(" ", method.getAnnotation(Update.class).value());

        assertTrue(sql.contains("audit_status = 'CLAIMED'"), methodName + " must require a claimed task");
        assertTrue(sql.contains("review_claimed_by = #{actorAdminId}"), methodName + " must require the claimant");
    }
}
