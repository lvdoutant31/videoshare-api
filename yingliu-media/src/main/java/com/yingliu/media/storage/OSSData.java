package com.yingliu.media.storage;

import java.util.Map;
import java.util.Objects;

/**
 * OSS V4 PostObject 表单签名数据。
 */
public final class OSSData {

    private final String key;
    private final String policy;
    private final String signature;
    private final String signatureVersion;
    private final String credential;
    private final String date;
    private final String securityToken;
    private final String successActionStatus;
    private final String host;

    public OSSData(
            String key,
            String policy,
            String signature,
            String signatureVersion,
            String credential,
            String date,
            String securityToken) {
        this(key, policy, signature, signatureVersion, credential, date, securityToken, "200", null);
    }

    public OSSData(
            String key,
            String policy,
            String signature,
            String signatureVersion,
            String credential,
            String date,
            String securityToken,
            String successActionStatus,
            String host) {
        this.key = requireValue(key, "key");
        this.policy = requireValue(policy, "policy");
        this.signature = requireValue(signature, "signature");
        this.signatureVersion = requireValue(signatureVersion, "signatureVersion");
        this.credential = requireValue(credential, "credential");
        this.date = requireValue(date, "date");
        this.securityToken = requireValue(securityToken, "securityToken");
        this.successActionStatus = requireValue(successActionStatus, "successActionStatus");
        this.host = host;
    }

    /**
     * 将 {@link OssUploadTool#createPostSignature(String)} 的返回值转换为上传参数。
     */
    public static OSSData from(Map<String, String> form) {
        Objects.requireNonNull(form, "form 不能为空");
        return new OSSData(
                form.get("key"),
                form.get("policy"),
                form.get("signature"),
                form.get("signature_version"),
                form.get("credential"),
                form.get("date"),
                form.get("security_token"),
                form.getOrDefault("success_action_status", "200"),
                form.get("host"));
    }

    public String getKey() {
        return key;
    }

    public String getPolicy() {
        return policy;
    }

    public String getSignature() {
        return signature;
    }

    public String getSignatureVersion() {
        return signatureVersion;
    }

    public String getCredential() {
        return credential;
    }

    public String getDate() {
        return date;
    }

    public String getSecurityToken() {
        return securityToken;
    }

    public String getSuccessActionStatus() {
        return successActionStatus;
    }

    public String getHost() {
        return host;
    }

    private static String requireValue(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " 不能为空");
        }
        return value;
    }
}
