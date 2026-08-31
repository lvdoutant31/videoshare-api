package com.yingliu.service.content.category;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yingliu.common.exception.BusinessException;
import com.yingliu.domain.content.category.ContentCategory;
import com.yingliu.service.content.category.mapper.ContentCategoryMapper;
import com.yingliu.service.content.shared.ServicePage;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@Profile("!test")
public class ContentCategoryService {
    private final ContentCategoryMapper mapper;

    public ContentCategoryService(ContentCategoryMapper mapper) { this.mapper = mapper; }

    public ServicePage<ContentCategory> list(long page, long pageSize, String keyword, String status) {
        Page<ContentCategory> result = mapper.selectPage(new Page<>(page, pageSize), new LambdaQueryWrapper<ContentCategory>()
                .like(keyword != null && !keyword.isBlank(), ContentCategory::getName, keyword)
                .eq(status != null && !status.isBlank(), ContentCategory::getStatus, status)
                .orderByAsc(ContentCategory::getSort)
                .orderByDesc(ContentCategory::getId));
        return new ServicePage<>(result.getRecords(), result.getTotal());
    }

    public ContentCategory create(String name, String icon, Integer sort, String status) {
        ContentCategory category = new ContentCategory();
        category.setName(normalizeName(name));
        category.setIcon(icon);
        category.setSort(sort == null ? 0 : sort);
        category.setStatus(normalizeStatus(status));
        mapper.insert(category);
        return category;
    }

    public ContentCategory update(long id, String name, String icon, Integer sort, String status) {
        ContentCategory category = require(id);
        if (name != null) category.setName(normalizeName(name));
        if (icon != null) category.setIcon(icon);
        if (sort != null) category.setSort(sort);
        if (status != null) category.setStatus(normalizeStatus(status));
        mapper.updateById(category);
        return category;
    }

    public void delete(long id) {
        ContentCategory category = require(id);
        if (mapper.countVideoReferences(id) > 0) {
            throw new BusinessException("CATEGORY_IN_USE", HttpStatus.CONFLICT, "分类已被视频使用，无法删除");
        }
        mapper.deleteById(category.getId());
    }

    private ContentCategory require(long id) {
        ContentCategory category = mapper.selectById(id);
        if (category == null) throw new BusinessException("CATEGORY_NOT_FOUND", HttpStatus.NOT_FOUND, "分类不存在");
        return category;
    }

    private String normalizeName(String name) {
        String normalized = name == null ? "" : name.trim();
        if (normalized.isEmpty()) throw new BusinessException("VALIDATION_FAILED", HttpStatus.UNPROCESSABLE_ENTITY, "分类名称不能为空");
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
