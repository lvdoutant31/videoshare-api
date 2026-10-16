package com.yingliu.service.app.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yingliu.domain.governance.SysSensitiveWord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysSensitiveWordMapper extends BaseMapper<SysSensitiveWord> {

    /** 启用的敏感词（action=BLOCK 直接拦截，REVIEW 进入待审） */
    @Select("SELECT * FROM sys_sensitive_word WHERE status = 'ENABLED'")
    List<SysSensitiveWord> selectEnabled();
}
