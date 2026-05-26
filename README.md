# WuminPy

[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![Android](https://img.shields.io/badge/Android-8.0%2B-green.svg)](https://developer.android.com)

在 Android 设备上运行 Python 脚本的工具应用。

## 功能特性

- **Python 3.14 运行环境** — 完整的 Python 解释器，在手机上编写和执行 Python 脚本
- **终端模拟器** — 基于 merminal 的终端，支持 bash、vim 等常用命令
- **代码编辑器** — 语法高亮、LSP 代码补全、多文件管理
- **VS Code 远程连接** — 通过 USB 或 Wi-Fi 连接 VS Code，实现远程开发和屏幕投屏
- **Git 集成** — 内置 Git 版本控制
- **AI 智能助手** — AI 辅助编程和问答
- **悬浮窗** — 快速执行脚本和查看输出
- **无障碍自动化** — 通过无障碍服务实现 UI 自动化操作

## 开源说明

本仓库为 WuminPy 的**部分开源**版本，包含以下开源模块：

| 模块 | 说明 |
|------|------|
| `:app` | 主应用界面（免责声明、隐私政策、侧边栏、赞助页面） |
| `:auto` | 无障碍自动化服务 |

### 闭源模块

以下模块为闭源，以 AAR 形式提供：

`:python` `:core` `:merminal` `:codeeditor` `:git` `:debug` `:ai`

## 构建方法

### 1. 准备闭源 AAR

从私有仓库构建闭源模块的 AAR 文件：

```bash
build_aar.bat
```

生成的 AAR 文件将自动复制到 `libs/` 目录。

### 2. 编译应用

```bash
./gradlew :app:assembleDebug
```

## 隐私与数据

- **无后台服务器** — 所有数据均在设备本地处理
- **不收集个人信息** — 不收集、上传或存储任何用户数据
- **不包含广告或统计 SDK**

详细隐私政策请见应用内「隐私政策」页面。

## 免责声明

本应用仅供学习、研究和开发用途。使用者应遵守相关法律法规。详见应用内「免责声明」。

## 许可证

本项目开源部分遵循 [MIT License](LICENSE)。

## 联系方式

- QQ：1352183717
- 微信：liu1352183717
- 邮箱：1352183717@qq.com

## 赞助支持

如果您觉得这个应用对您有帮助，欢迎扫码支持开发者。

详情见应用内「赞助支持」页面。
