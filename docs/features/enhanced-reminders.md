# 课程与今日计划提醒

设置 → 课程与今日计划提醒：

- 息屏 / 锁屏全屏提醒默认关闭，课程与今日计划共用此开关。
- 开启且获得系统全屏通知权限：息屏或锁屏请求全屏意图，提醒页请求亮屏；解锁使用中只提交高优先级通知，由系统展示横幅。
- 关闭或权限不足：高优先级普通通知。应用整体和渠道的横幅开关、勿扰及厂商策略仍影响展示。
- 显示全屏权限、应用整体通知等级、渠道等级及勿扰的可读取状态；提供相应系统设置入口，返回时刷新。
- 分别提供课程、今日计划的 10 秒测试按钮，使用正式 AlarmManager 和 Receiver。

两个 Receiver 共用 ReminderNotifications.kt，冷启动时创建渠道。今日计划支持点击查看、确认、时间与备注，和课程共用锁屏 Activity。保留原渠道 ID 和设置，不删除渠道来绕过系统限制。课程开始超过 10 分钟、计划提醒超过 30 分钟不再弹出；全屏页至多显示两分钟。

按用户最新要求移除流体云和实验性 Android Live Update（UI、调用和 promoted notification 权限）。旧 class_reminder_live_update 存储值不再读取。OPPO 12658 是模板说明，13572 明确正式接入需要开发者认证和 OPPO 确认，不能将标准通知宣称为流体云。

## 2026-09-20 实机诊断

设备 PLZ110，Android 16 / ColorOS V16.1.0：

- 通知权限已授予，勿扰关闭。
- 两个渠道 importance=4、mShowBanner=true，但应用整体 importance=DEFAULT userSet=true，计划通知最终 importance=3。
- 系统通知页证实应用总“横幅”未勾选；已按用户要求打开。
- 应用内全屏开关已开启。app-op 有历史拒绝记录，但当前系统全屏授权页开关为开启；不能以历史记录认定当前仍拒绝。
- 旧计划通知 fullscreenIntent=null、contentIntent=null、pri=0：代码缺少全屏和点击处理，未设置高优先级，已修复。

系统“强行停止”后需重新打开应用；重启后需先解锁。验证覆盖两种提醒的息屏、解锁使用、关闭全屏、通知权限与渠道、点击确认及过期情况。

用户已实机确认息屏全屏提醒可用，并确认离开应用后提醒异常由系统后台应用限制导致。需在系统电池管理中允许本应用后台运行；普通应用不能保证绕过厂商后台冻结。

参考：

- https://developer.android.com/about/versions/14/behavior-changes-14
- https://developer.android.com/develop/ui/views/notifications/channels
- https://open.oppomobile.com/documentation/page/info?id=12658
- https://open.oppomobile.com/documentation/page/info?id=13572
