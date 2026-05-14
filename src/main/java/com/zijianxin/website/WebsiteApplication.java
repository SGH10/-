package com.zijianxin.website;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

@SpringBootApplication
public class WebsiteApplication {

    public static void main(String[] args) {
        SpringApplication.run(WebsiteApplication.class, args);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void printStartupGuide() {
        System.out.println();
        System.out.println("============================================================");
        System.out.println("网址入口：");
        System.out.println("首页总览： http://127.0.0.1:8080");
        System.out.println("客户搜索： http://127.0.0.1:8080/customer-search");
        System.out.println("AI开发信： http://127.0.0.1:8080/ai-outreach");
        System.out.println("爬虫配置： http://127.0.0.1:8080/crawler-settings");
        System.out.println("AI 配置： http://127.0.0.1:8080/ai-settings");
        System.out.println("说明：首页看流程总览，客户搜索页执行抓取，AI开发信页生成并发送开发信，两个配置页分别管理爬虫与 AI 参数。");
        System.out.println("============================================================");
        System.out.println();
    }
}
