package com.yingliu;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** 后端统一启动入口，装配手机端、管理端、媒体与后台任务；根包保持 com.yingliu。 */
@SpringBootApplication
public class YingliuApplication {
    public static void main(String[] args) {
        SpringApplication.run(YingliuApplication.class, args);
    }
}
