# kativa_android

Kavita 自建阅读服务器的 **Android 原生客户端**（第三方，非官方）。

> Kavita 是一个快速、自托管的数字图书馆服务端，支持漫画 / 电子书 / PDF。
> 本项目只通过 Kavita REST API 与服务器交互。

## 文档

| 文档 | 内容 |
| --- | --- |
| [`AGENTS.md`](AGENTS.md) | 工程契约：技术栈、分层、网络层、阅读器规范、质量门禁 |
| [`docs/PLAN.md`](docs/PLAN.md) | 整体规划：定位、信息架构、技术布局、里程碑、风险 |
| [`docs/FLOWS.md`](docs/FLOWS.md) | 使用流程：启动与服务器选择、登录、续读、阅读、隐私模式、异常恢复 |
| [`docs/PAGES.md`](docs/PAGES.md) | 页面设计：每个页面的结构、组件、状态、交互、接口 |
| [`docs/FEATURES.md`](docs/FEATURES.md) | 功能规划：优先级、里程碑、验收标准、不做清单 |
| [`docs/API.md`](docs/API.md) | 接口清单：端点、字段、枚举对照表、对接陷阱 |
| [`docs/ASSETS.md`](docs/ASSETS.md) | 素材清单：SVG→PNG 占位与正式素材替换追踪 |

## 技术栈

Kotlin · Jetpack Compose · Material 3 · Hilt · Retrofit/OkHttp · kotlinx.serialization · Room · Coil

## 产品要点

- **多服务器**：可保存多台 Kavita 服务器；启动默认显示服务器选择页（不自动登录），可在设置中指定"启动时自动登录的服务器"。
- **隐私模式**：主界面右上角一键开启，封面全部不请求，显示深灰占位图。
- **主题色**：主色 `#4AC694`，深色主题色 `#2D7A56`。
- **多语言**：中文 / English / Français / 日本語。
- **不做**下载与离线阅读（阅读依赖网络，进度离线入队、联网补传）。

## 快速开始

```bash
# 需要 JDK 17（本机已装：D:\tools\jdk-17.0.21+9）
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

首次运行：选择/添加服务器（如 `https://kavita.example.com`），使用账号密码或
「用户设置 → Auth Keys」生成的 API Key 登录。

## 交付前必做：启动冒烟

编译通过不等于能启动。改过主题、Manifest、Activity 基类、DI、Room、资源目录中的任意一项后，
必须先冷启动验证（详细清单与命令见 [`AGENTS.md` §8.1](AGENTS.md)）：

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb logcat -c
adb shell am force-stop com.kativa.reader.debug
adb shell am start -n com.kativa.reader.debug/com.kativa.reader.MainActivity
sleep 8
adb shell pidof com.kativa.reader.debug          # 必须非空
adb logcat -d | grep "FATAL EXCEPTION"           # 必须为空
```

## 参考

- Kavita API 文档：<https://www.kavitareader.com/docs/api/#/>
- Kavita Wiki：<https://wiki.kavitareader.com/>
- 上游仓库：<https://github.com/Kareadita/Kavita>

## 许可

Apache-2.0。Kavita 本身为 GPL-3.0，本项目仅通过 HTTP API 使用其服务。
