package com.yingliu.framework.config.mybatis;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** 注册业务 Mapper；test profile 排除数据库装配，完整集成测试使用实际数据源。 */
@Configuration
@Profile("!test")
@MapperScan("com.yingliu.service.**.mapper")
public class MybatisMapperConfiguration {
}
