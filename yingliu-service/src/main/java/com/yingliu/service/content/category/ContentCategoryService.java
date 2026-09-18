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

/** 分类维护用例，统一分类查询、排序和可用状态处理。 */
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

    /** List soft-deleted categories (trash / recycle bin). */
    public ServicePage<ContentCategory> listDeleted(long page, long pageSize, String keyword) {
        long offset = (page - 1) * pageSize;
        return new ServicePage<>(mapper.selectDeleted(offset, pageSize, keyword), mapper.countDeleted(keyword));
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
        long refCount = mapper.countVideoReferences(id);
        if (refCount > 0) {
            // Logical delete still allowed — the category won't be visible to C-end users.
            // But warn the admin that there are video references.
            throw new BusinessException("CATEGORY_IN_USE", HttpStatus.CONFLICT,
                    "分类已被 " + refCount + " 个视频引用，删除后这些视频将失去该分类关联。如仍需删除请先移除视频的分类关联");
        }
        mapper.deleteById(category.getId());
    }

    /** Restore a soft-deleted category back to active. */
    public ContentCategory restore(long id) {
        int rows = mapper.restoreById(id);
        if (rows == 0) {
            throw new BusinessException("CATEGORY_NOT_FOUND", HttpStatus.NOT_FOUND, "分类不存在或未被删除");
        }
        // Check name conflict with existing active categories
        ContentCategory restored = mapper.selectById(id);
        if (restored != null && mapper.countActiveByName(restored.getName()) > 1) {
            throw new BusinessException("CATEGORY_NAME_CONFLICT", HttpStatus.CONFLICT,
                    "已存在同名分类「" + restored.getName() + "」，请先重命名后再恢复");
        }
        return restored;
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
