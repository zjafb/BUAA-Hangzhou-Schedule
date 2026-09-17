# 北航杭州课表

![Kotlin](https://img.shields.io/badge/Kotlin-blue.svg?style=flat&logo=kotlin)
![Compose](https://img.shields.io/badge/Jetpack_Compose-blueviolet.svg?style=flat&logo=jetpack-compose)
![Ktor](https://img.shields.io/badge/Ktor-orange.svg?style=flat&logo=ktor)
![Platform](https://img.shields.io/badge/Platform-Android-green.svg?style=flat&logo=android)
![License](https://img.shields.io/badge/License-MIT-green.svg?style=flat)

面向 **北京航空航天大学杭州国际校园** 学生的 **Android** 校园助手，把课表、考试、成绩、空教室、作业、邮件等常用服务集中到一个客户端中。基于 **Kotlin**、**Jetpack Compose** 与 **Ktor** 构建，**仅支持 Android**。

## 基于什么

本项目 fork 自 [BUAASubnet/UBAA](https://github.com/BUAASubnet/UBAA)（MIT 协议开源），在其基础上做了杭州国际校园的专门适配，并且**只保留 Android 客户端**（移除了原项目的 iOS / 桌面 / Web 目标）：

- 保留并改造：课表、考试、成绩、空教室、希冀作业等通用能力；
- 移除北京校区专属功能：博雅选课、图书馆座位、课程签到、研讨室预约、SPOC 作业、阳光打卡；
- 新增杭州特色能力：钉钉「空间预约」入口、北航邮箱查询、今日计划与课程/计划提醒。

## 功能

**普通功能**

| 功能 | 说明 |
| --- | --- |
| 课表查询 | 学期 / 周次 / 周课表 / 今日课程，固定 14 节、按杭州校区标准作息（08:00–22:15）显示；本地化后秒开 |
| 考试查询 | 查看考试安排，支持学期切换 |
| 成绩查询 | 查看课程成绩、学分与绩点 |
| 空教室查询 | 查询杭州校区空闲教室 |
| 希冀作业 | 聚合 `judge.buaa.edu.cn` 编程作业、提交状态、得分与题目明细 |
| 空间预约 | 一键跳转钉钉工作台「空间预约管理平台」 |
| 邮件查询 | 仅支持北航内部邮箱（IMAP/SMTP），未读红点、一键已读、长按/详情删除 |

**首页能力**

| 功能 | 说明 |
| --- | --- |
| 今日课程 | 展示当日课程、时间、地点与授课老师，右侧标注必修/选修，点击进入课程详情 |
| 今日计划 | 自定义待办任务，按时间排序，可叠加到课表，支持提前提醒通知，长按可删除 |
| 待办区 | 聚合希冀未完成作业提醒 |
| 消息入口 | 右上角一键查看未读邮件 |

**高级功能**

| 功能 | 说明 |
| --- | --- |
| 自动评教 | 一键完成学期末评教任务 |

**提醒通知**：课程课前提醒（提前分钟数可调）与计划提醒，均通过系统通知推送（Android 13+ 自动请求通知权限，设置页可检查/跳转开启）。

## 技术栈

- **Kotlin + Jetpack Compose**：Android 原生界面
- **Ktor**：网络请求与本地直连 / 服务器中转两种连接模式（纯本地使用无需部署服务端）
- **Material Design 3**：支持浅色 / 深色主题
- **russhwolf Settings + kotlinx.serialization**：轻量本地持久化
- **Android Keystore**：登录凭据加密存储
- **Jakarta Mail（Angus Mail）**：北航邮箱 IMAP/SMTP 收发
- **AlarmManager + BroadcastReceiver**：课程 / 计划提醒

## 构建

环境要求：

- JDK 21
- Android SDK（compileSdk 36）

Debug 构建：

```bash
./gradlew :androidApp:assembleDebug
```

产物位于 `androidApp/build/outputs/apk/debug/androidApp-debug.apk`。

Release 构建需要先配置签名（`local.properties`）：

```properties
SIGNING_KEY=<keystore 相对项目根目录的路径>
SIGNING_STORE_PASSWORD=<store 口令>
SIGNING_KEY_ALIAS=<alias>
SIGNING_KEY_PASSWORD=<key 口令>
```

```bash
./gradlew :androidApp:assembleRelease
```

## 下载安装

Release 版 APK 见 [Releases](../../releases)（或直接安装本仓库构建产物）。安装时需允许「安装未知来源应用」。

## 许可证

本项目基于 MIT 协议开源。

Copyright (c) 2026 BUAASubnet
