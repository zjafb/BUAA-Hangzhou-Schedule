# 北航杭州课表

![Kotlin](https://img.shields.io/badge/Kotlin-2.3.20-blue.svg?style=flat&logo=kotlin)
![Compose Multiplatform](https://img.shields.io/badge/Compose_Multiplatform-1.10.3-blueviolet.svg?style=flat&logo=jetpack-compose)
![Ktor](https://img.shields.io/badge/Ktor-3.4.1-orange.svg?style=flat&logo=ktor)
![Platform](https://img.shields.io/badge/Platform-Android%20%7C%20iOS%20%7C%20Desktop%20%7C%20Web-lightgrey.svg?style=flat)

**北航杭州课表** 是面向北京航空航天大学杭州国际校园学生的跨平台课表客户端，基于 **Kotlin Multiplatform**、**Compose Multiplatform** 和 **Ktor** 构建，覆盖 Android、iOS、Desktop 与 Web。

本项目是某个北航客户端项目（MIT 协议）的 fork，做了杭州国际校园的专门适配。

## 功能

- 课表查询：学期、周次、周课表与今日课程
- 考试查询
- 成绩查询
- 空教室查询（仅杭州校区）
- 希冀作业、SPOC 作业
- 阳光打卡、自动评教
- 空间预约：跳转钉钉工作台「空间预约管理平台」

已删除北京校区专属的博雅课程、图书馆座位、课程签到与研讨室预约。

## 技术栈

- Kotlin Multiplatform + Compose Multiplatform + Ktor
- Material Design 3，支持系统主题适配
- 本地直连与服务器中转两种连接模式（纯本地使用无需部署服务端）

## 构建

需要 JDK 17+ 与 Android SDK（compileSdk 36）。打开工程后运行：

```bash
./gradlew :androidApp:assembleDebug
```

## 许可证

本项目基于 MIT 协议开源。

Copyright (c) 2026 BUAASubnet
