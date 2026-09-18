package com.yingliu.service.app.support;

/** App 用户对外视图（脱敏：不含手机号明文与企微标识；ID 字符串化避免 JS 精度丢失） */
public record AppUserView(
        String id,
        String nickname,
        String avatarUrl,
        String bio,
        String status,
        long likeReceivedCount,
        long followingCount,
        long followerCount,
        boolean wecomBound) {
}
