# Spring Boot Website Starter

这是一个最小可运行的 Spring Boot 小网站项目，适合从空目录快速起步。

## 已包含

- `pom.xml`：Maven 项目配置
- `src/main/java/com/zijianxin/website/WebsiteApplication.java`：Spring Boot 启动入口
- `src/main/resources/application.properties`：应用配置
- `src/main/resources/static/index.html`：首页
- `src/main/resources/static/styles.css`：基础样式
- `src/main/resources/static/main.js`：基础交互脚本

## 运行方式

```powershell
mvn spring-boot:run
```

启动后打开：

```text
http://127.0.0.1:8080
```

## 打包方式

```powershell
mvn clean package
java -jar .\target\website-0.0.1-SNAPSHOT.jar
```
