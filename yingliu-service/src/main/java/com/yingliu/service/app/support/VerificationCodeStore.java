package com.yingliu.service.app.support;

import com.yingliu.service.support.TtlStore;

/**
 * 验证码与企微授权 state 的存储抽象（生产 Redis，测试内存实现）。
 *
 * <p>操作契约由 {@link TtlStore} 定义；本接口保留原名称，用于区分"验证码语义"的使用方与
 * 其它 TTL 键值使用方（如管理端授权缓存）。
 */
public interface VerificationCodeStore extends TtlStore {
}
