package com.yingliu.service.content.tag;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yingliu.common.exception.BusinessException;
import com.yingliu.domain.content.tag.ContentTag;
import com.yingliu.service.content.shared.ServicePage;
import com.yingliu.service.content.tag.mapper.ContentTagMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@Profile("!test")
public class ContentTagService {
    private final ContentTagMapper mapper;

    public ContentTagService(ContentTagMapper mapper) { this.mapper = mapper; }

    public ServicePage<ContentTag> list(long page, long pageSize, String keyword, String status) {
        Page<ContentTag> result = mapper.selectPage(new Page<>(page, pageSize), new LambdaQueryWrapper<ContentTag>()
                .like(keyword != null && !keyword.isBlank(), ContentTag::getName, keyword)
                .eq(status != null && !status.isBlank(), ContentTag::getStatus, status)
                .orderByDesc(ContentTag::getHeatScore)
                .orderByDesc(ContentTag::getId));
        return new ServicePage<>(result.getRecords(), result.getTotal());
    }

    public ContentTag create(String name, String status, BigDecimal heatScore) {
        ContentTag tag = new ContentTag();
        tag.setName(normalizeName(name));
        tag.setStatus(normalizeStatus(status));
        tag.setHeatScore(heatScore == null ? BigDecimal.ZERO : heatScore);
        mapper.insert(tag);
        return tag;
    }

    public ContentTag update(long id, String name, String status, BigDecimal heatScore) {
        ContentTag tag = require(id);
        if (name != null) tag.setName(normalizeName(name));
        if (status != null) tag.setStatus(normalizeStatus(status));
        if (heatScore != null) tag.setHeatScore(heatScore);
        mapper.updateById(tag);
        return tag;
    }

    public void delete(long id) {
        ContentTag tag = require(id);
        if (mapper.countVideoReferences(id) > 0) {
            throw new BusinessException("TAG_IN_USE", HttpStatus.CONFLICT, "标签已被视频使用，无法删除");
        }
        mapper.deleteById(tag.getId());
    }

    private ContentTag require(long id) {
        ContentTag tag = mapper.selectById(id);
        if (tag == null) throw new BusinessException("TAG_NOT_FOUND", HttpStatus.NOT_FOUND, "标签不存在");
        return tag;
    }

    private String normalizeName(String name) {
        String normalized = name == null ? "" : name.trim();
        if (normalized.isEmpty()) throw new BusinessException("VALIDATION_FAILED", HttpStatus.UNPROCESSABLE_ENTITY, "标签名称不能为空");
        return normalized;
    }

    private String normalizeStatus(String status) {
        String normalized = status == null || status.isBlank() ? "ENABLED" : status.trim();
        if (!normalized.equals("ENABLED") && !normalized.equals("DISABLED")) {
            throw new BusinessException("VALIDATION_FAILED", HttpStatus.UNPROCESSABLE_ENTITY, "状态必须为 ENABLED 或 DISABLED");
        }
        return normalized;
    }
}
