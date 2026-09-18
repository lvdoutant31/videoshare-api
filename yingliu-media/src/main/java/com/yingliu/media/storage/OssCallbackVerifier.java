package com.yingliu.media.storage;

import jakarta.servlet.http.HttpServletRequest;

@FunctionalInterface
public interface OssCallbackVerifier {
    boolean verify(HttpServletRequest request, byte[] body);
}
