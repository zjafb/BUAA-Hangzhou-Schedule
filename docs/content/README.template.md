# 北航杭州课表

面向北京航空航天大学杭州国际校园的 Android 校园助手，基于 [BUAASubnet/UBAA](https://github.com/BUAASubnet/UBAA)（MIT）改造。

支持课表及日历导出、课程与成绩查询、考试安排、空教室、希冀作业、北航邮件、今日计划、校园指南和自动评教等功能。

## 最新更新

{{LATEST_RELEASE}}

[下载最新版](https://github.com/zjafb/BUAA-Hangzhou-Schedule/releases/latest) · [更新日志](docs/changelog/index.md)

## 使用说明

{{USAGE_GUIDE}}

## 开发与构建

使用 JDK 21、Android SDK 36。项目核心采用 Kotlin、Compose、Ktor；shared 提供数据与接口，composeApp 提供界面，androidApp 为 Android 入口。

```shell
./gradlew :androidApp:assembleDebug
./gradlew :androidApp:assembleRelease
```

正式版签名通过未提交的 local.properties 或环境变量配置 SIGNING_KEY、SIGNING_STORE_PASSWORD、SIGNING_KEY_ALIAS、SIGNING_KEY_PASSWORD。不要提交密码或签名文件。

版本号由 gradle.properties 管理。每次更新先维护 docs/content/latest-release.txt 和 docs/content/usage-guide.txt，再运行 ./gradlew syncUserDocs。README 的结构在 docs/content/README.template.md 维护。

所有客户端编译都会先同步文档：自动生成应用内说明、README、docs/changelog/index.md 和 docs/features/usage-guide.md。日志首行必须与版本号一致，且只能包含一个版本；版本不匹配会阻止构建。更新内容仍需依据实际改动编写，构建不会凭空生成新功能说明。

## 许可证

MIT。Copyright (c) 2026 BUAASubnet。保留上游许可证与署名。
