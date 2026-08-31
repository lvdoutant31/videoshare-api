package com.yingliu.service.persistence;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!test")
@MapperScan("com.yingliu.service.**.mapper")
public class MybatisMapperConfiguration {
}
