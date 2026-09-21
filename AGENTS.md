# 项目维护约定

## GitHub 同步

用户指定的唯一发布仓库为 `https://github.com/zjafb/BUAA-Hangzhou-Schedule`，使用 `origin` 推送；`upstream` 仅用于参考上游。

每次完成项目更新后，验证改动，同步相关文档，提交本次修改并推送到 `origin`，无需再次询问是否推送。提交前检查工作区，避免夹带无关修改、密码或签名文件。推送前检查远端变化，不强制覆盖远端历史；推送后验证远端已包含本次提交。

网络失败时检查系统代理及仓库局部 Git 代理配置并合理重试。不得声称永远在线或将本地提交当作已推送；仍失败时明确说明待推送状态。代理地址取当前环境，不在共享配置中硬编码机器端口。

## 文档同步

每次修改用户可见的功能、设置或操作流程，都必须同时检查并更新文档，不等用户另行提醒。

- 在 `docs/content/latest-release.txt` 维护当前版本的更新日志，仅保留最新一次更新，首行与 `gradle.properties` 的版本一致。
- 在 `docs/content/usage-guide.txt` 更新实际操作步骤；README 结构或构建方式变化时更新 `docs/content/README.template.md`。
- 运行 `./gradlew syncUserDocs`，将生成的 README、更新日志及使用说明与源码一并提交。不要直接修改生成的文档或应用内生成的 `UserDocumentation.kt`。
- 构建会自动同步文档并检查日志版本，但不会自动理解代码修改；维护者应根据真实完成、验证的功能编写说明，不得把未实现功能写入日志。
