package com.yingliu.domain.user.management;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/** 用户与企微身份的绑定关系及绑定时间记录。 */
@TableName("sys_user_wecom_identity")
public class UserWeComIdentity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long userId;
    private String corpId;
    private byte[] wecomUserIdCiphertext;
    private String wecomUserIdHmac;

    public String getCorpId() { return corpId; }
    public void setCorpId(String value) { corpId = value; }
    public byte[] getWecomUserIdCiphertext() { return wecomUserIdCiphertext; }
    public void setWecomUserIdCiphertext(byte[] value) { wecomUserIdCiphertext = value; }
    public String getWecomUserIdHmac() { return wecomUserIdHmac; }
    public void setWecomUserIdHmac(String value) { wecomUserIdHmac = value; }
    private String displayName;
    private String bindStatus;
    private LocalDateTime boundAt;
    private LocalDateTime unboundAt;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getBindStatus() { return bindStatus; }
    public void setBindStatus(String bindStatus) { this.bindStatus = bindStatus; }
    public LocalDateTime getBoundAt() { return boundAt; }
    public void setBoundAt(LocalDateTime boundAt) { this.boundAt = boundAt; }
    public LocalDateTime getUnboundAt() { return unboundAt; }
    public void setUnboundAt(LocalDateTime unboundAt) { this.unboundAt = unboundAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
