# AGENTS.md

> 本文件是 **Kativa for Android** 的工程宪法：给 AI 代理与人类贡献者的执行契约。
> 动手写代码前必须读完；与本文件冲突的实现一律视为缺陷。
> 产品/页面/流程规划见 `docs/PLAN.md`、`docs/PAGES.md`、`docs/FLOWS.md`、`docs/FEATURES.md`、`docs/API.md`、`docs/ASSETS.md`。

---

## 1. 项目定位

| 项目 | 说明 |
| --- | --- |
| 产品名 | **Kativa**（Android 客户端） |
| 目标 | Kavita 自建阅读服务器的第三方原生客户端，一间"随身漫画/电子书图书馆" |
| 上游服务端 | [Kavita](https://github.com/Kareadita/Kavita)，API 兼容基线 **v0.9.1.x** |
| 关系 | **非官方**第三方客户端，与 Kareadita 团队无隶属关系，不得使用其图标/商标暗示官方身份 |
| 许可 | 本仓库 Apache-2.0；Kavita 服务端为 GPL-3.0，只通过 HTTP API 交互，**不复制其源码** |
| 语言 | 代码/注释/提交信息用英文；UI 多语言 **中/英/法/日**（`values/` `values-en/` `values-fr/` `values-ja/`），默认跟随系统 |
| 素材 | 图片与按钮图标一律 **SVG → PNG** 管线，清单见 `docs/ASSETS.md`（开发期用自动生成的占位 SVG/PNG，正式素材到位后批量替换） |

产品北极星：**打开 App 3 秒内能接着上次的进度继续读**。

核心约束（产品级，优先级高于任何技术偏好）：

1. **多服务器**：可保存多个 Kavita 服务器；**启动默认不自动登录**，先显示服务器选择页；用户可在设置里指定"启动时自动登录的服务器"。
2. **隐私模式**：主界面右上角一键开启，开启后所有封面不请求、显示深灰占位图（状态持久化）。
3. **无下载模块**：不做章节/系列下载与离线阅读（见 §10）。
4. **欢迎页只有 logo**：无插画、无价值主张文案。

---

## 2. 技术栈（唯一答案，勿自行替换）

| 层 | 选型 |
| --- | --- |
| 语言 | Kotlin（`2.x`，`jvmTarget = 17`） |
| UI | Jetpack Compose + Material 3（`material3-adaptive` 做平板适配） |
| 架构 | 单 Activity + Compose Navigation，MVVM + UDF（`UiState` 不可变 data class） |
| DI | Hilt |
| 网络 | Retrofit 2 + OkHttp 4 + **kotlinx.serialization**（不用 Gson/Moshi） |
| 分页 | Paging 3（`RemoteMediator` 用于 Room 缓存 + 网络的统一列表源；无离线要求，也可用简单 `PagingSource` + 网络取数） |
| 本地库 | Room（结构化数据与同步队列）+ DataStore(Preferences)（设置与会话） |
| 图片 | Coil 3（自定义 `Fetcher`；隐私模式下不发起请求） |
| 时间 | kotlinx-datetime（服务端时间为 UTC ISO-8601） |
| 测试 | JUnit5 + Turbine + MockWebServer + Roborazzi（截图）+ Compose UI Test |
| 静态检查 | ktlint + detekt（CI 门禁） |
| 构建 | Gradle Kotlin DSL + `gradle/libs.versions.toml` 版本目录 |

> 版本号以 `gradle/libs.versions.toml` 为唯一事实来源，本文件不锁定小版本。
> `minSdk 26`、`targetSdk 35`、`compileSdk 35`；`applicationId = "com.kativa.reader"`（改名只改 `app/build.gradle.kts` 一处 + 包名迁移脚本）。

**禁止**引入：Firebase/GA 等第三方埋点、任何把书库元数据上传到外部的 SDK、自动更新 SDK。

---

## 3. 架构分层与依赖规则

```
ui/          Compose 屏幕、组件、ViewModel（只依赖 domain）
domain/      用例、领域模型、仓库接口（纯 Kotlin，无 Android 依赖）
data/        Retrofit API、DTO、Mapper、Room、DataStore（实现 domain 接口）
core/        网络客户端、鉴权、图片、设计系统、素材、通用工具
```

硬性规则：

1. **UI 不出现 DTO**；DTO → domain model 的映射只允许写在 `data/mapper/`。
2. **domain 不 import `android.*`**（`javax.inject` 除外）。
3. ViewModel 不持有 `Context`、不直接调 Retrofit；通过 UseCase/Repository。
4. 一个屏幕一个 `UiState`；状态用 `StateFlow`，事件用 `Channel`/`SharedFlow`（一次性）。
5. 所有写操作必须**乐观更新 + 失败回滚 + 可重试**（进度、收藏、评分、已读）。
6. 不在 Composable 里发网络请求；用 `LaunchedEffect` 触发 ViewModel 事件。

包结构：

```
com.kativa.reader
├─ KativaApp.kt                  @HiltAndroidApp
├─ MainActivity.kt
├─ core/
│  ├─ network/                   OkHttp、Retrofit、AuthInterceptor、TokenAuthenticator、PaginationInterceptor
│  ├─ auth/                      ServerProfileStore（多服务器）、SessionManager（DataStore + Keystore 加密）
│  │                             AuthRepository、StartupRouter（决定启动进哪个服务器）
│  ├─ image/                     KativaImageLoader、CoverUrlBuilder、PrivacyMask（隐私模式短路请求）
│  ├─ designsystem/              Theme、Color、Type、Shape、通用组件（SeriesCard/ProgressBadge/EmptyState…）
│  ├─ assets/                    素材注册表（见 docs/ASSETS.md；占位素材 → 正式素材批量替换）
│  ├─ util/                      文件、格式化、Result 封装
│  └─ di/
├─ data/
│  ├─ remote/
│  │  ├─ api/                    AccountApi、SeriesApi、ReaderApi、LibraryApi、CollectionApi、ReadingListApi、
│  │  │                          SearchApi、StreamApi、FilterApi、WantToReadApi、UsersApi、ImageApi
│  │  └─ dto/                    与服务端字段严格对齐（@SerialName 显式 camelCase）
│  ├─ local/                     Room（实体/DAO/迁移）、PreferencesDataStore（服务器档案、设置、隐私模式开关）
│  ├─ mapper/
│  └─ repository/                各 Repository 实现
├─ domain/
│  ├─ model/                     Server、Series、Volume、Chapter、Library、Progress、ReadingProfile、Filter…
│  ├─ repository/                接口
│  └─ usecase/                   GetHomeStreams、ContinueReading、SyncProgress、ResolveStartupServer…
└─ ui/
   ├─ navigation/                KativaNavHost、Destinations、DeepLinks
   ├─ servers/  auth/  onboarding/     服务器选择、登录、欢迎页
   ├─ home/  library/  series/  reader/  discover/  me/  search/  settings/
   └─ common/
```

---

## 4. 网络层契约（照抄，别重新发明）

### 4.1 基础地址与多服务器

- **每个服务器一份配置**：`ServerProfile{id, name, baseUrl, tlsFingerprint?, allowCleartext, autoLogin}` 存 Room/DataStore；当前激活服务器由 `ActiveServerStore` 提供，**切换服务器后 Retrofit baseUrl / 图片 loader / 缓存 key 全部随之切换**。
- 用户输入 `https://host[:port]`；**统一规范化**为以 `/` 结尾后拼 `api/`，例如 `https://kavita.example.com/api/`。
- 兼容用户粘贴 `.../api`、`.../api/`、带尾部空格的输入；也允许粘贴含 apiKey 的 OPDS/登录链接并自动提取。
- 只允许 `http(s)`；`http` 需**该服务器档案单独**开启"允许明文连接"并给出安全提示；自签证书走"信任本次证书指纹"（TOFU，按 host 存储），不做全局 `trustAll`。
- 所有请求路径**不以 `/` 开头**（Retrofit baseUrl 已含 `api/`），避免 `//api`。
- **启动行为**（见 §1）：默认进"服务器选择页"；仅当用户在设置里选定了 `autoLogin` 服务器且该服务器会话有效时，才直接进入对应首页。

### 4.2 鉴权

| 场景 | 做法 |
| --- | --- |
| 首选登录 | `POST /api/Account/login` `{username,password}` → `UserDto{token,refreshToken,apiKey,authKeys[]}` |
| API Key 登录 | `POST /api/Account/login` `{apiKey}`，或 `POST /api/Plugin/authenticate?apiKey=&pluginName=Kativa`（返回 token，便于旧版/无密码场景） |
| 业务请求 | `Authorization: Bearer <token>` |
| 刷新 | `POST /api/Account/refresh-token` `{token,refreshToken}` → 新 `{token,refreshToken}`；用 OkHttp `Authenticator` 做**单飞（single-flight）**刷新，失败即登出 |
| 主动刷新 | 解析 JWT `exp`，提前 5 分钟刷新（避免阅读中 401） |
| 图片/媒体 URL | **必须**带 `?apiKey=<imageKey>`（服务端从 query 取 key），并同时带 Bearer 头 |
| 设备标识 | 每个请求带 `X-Device-Id: <本地生成的 UUID>`；`User-Agent: Kativa/<version> (Android <api>; Mobile)` |

**imageKey 选取顺序**：`authKeys.firstOrNull { it.name == "image-only" }?.key` → `user.apiKey`（OPDS key）→ 若无，调用 `POST /api/Account/create-auth-key` `{name:"image-only",keyLength:32}` 后落库。
**会话按服务器隔离**：`SessionStore` 的 key 是 `serverId`，token/refreshToken/imageKey 分服务器保存；切换服务器 = 切换会话上下文，**不得**把 A 服务器的 token 发给 B。
**禁止**把用户密码持久化；token 用 Android Keystore 加密后存 DataStore；删除/登出某服务器只清该服务器的会话与图片缓存。

### 4.3 分页（最容易踩坑的地方）

- 请求：`?pageNumber=1&pageSize=40`（**1 起始**）。**必须显式传参**：服务端 `UserParams` 默认 `pageSize=int.MaxValue`，不传会一次返回全部数据。
- 响应体是**裸数组**（`PagedList<T> : List<T>`），分页信息只在响应头 `Pagination`：
  ```json
  {"currentPage":1,"itemsPerPage":40,"totalItems":1234,"totalPages":31}
  ```
- 用 OkHttp `Interceptor` 抓取该头，写入 `PagedResponse<T>{items,page,totalPages,totalItems}`；缺失时退化为"本页不满即末页"。
- 列表页统一 `pageSize=40`（网格）/`30`（列表）。像 `recently-updated-series` 这种"按系列聚合"的端点也用 `PageSize` 控制**返回系列数**（不是章节数），同样必须显式传。

### 4.4 请求体约定

- `POST` 的 filter 类接口即使无筛选也要发 `{}`（`SeriesFilterV2Dto` 有默认值），不要发 `null`。
- 服务端 DTO 字段**全部必填**（除非标注 optional），未知字段忽略；`@SerialName` 显式声明，禁止依赖命名策略猜测。
- 枚举是**数字**（见 `docs/API.md` 对照表）；未知数字映射为 `Unknown` 而不是崩溃。
- 时间统一 ISO-8601 UTC（`2026-03-01T12:00:00Z`），本地展示转时区。

### 4.5 错误处理

- 401 → 走刷新；刷新失败 → 全局 `SessionExpired` 事件 → 回登录页（保留服务器地址）。
- 403/404/400 → 映射为"无权限/内容已被删除/请求无效"，不重试。
- Kavita 错误体**多为纯文本**（本地化字符串，偶尔 JSON）。先按 `application/json` 尝试解析 `{message}`/`{errors}`，否则取 `body.string()` 原文展示。
- 网络异常 → 统一 `KativaError`：`Network / Timeout / Unauthorized / Forbidden / NotFound / BadRequest / Server / Parse / Unknown`，UI 只消费这层。
- 重试策略：GET 指数退避（0.5s/1s/2s，最多 3 次）；写操作**不自动重试**，由用户点按或进度同步队列处理。
- 日志：debug 打印 URL/状态码/耗时；**永不**打印 token、apiKey、密码（自定义 `redactHeader("Authorization","x-api-key","Cookie")`）。

### 4.6 已知服务端行为（务必遵守）

1. `page`（阅读器图片）**0 基**；`pageNum`（进度）**1 基**。换算只允许在 `ReaderProgressMapper` 一处做。
2. 读完一章保存为 `maxPages + 1`；进度等于 `maxPages` 表示"最后一页已展示未完成"，UI 恢复时钳制到 `maxPages - 1`。
3. 打开章节先 `GET /api/Reader/chapter-info?chapterId=&includeDimensions=true`（同时触发服务端解包缓存），再拉图片。
4. 图片接口带 `ETag`/缓存头，Coil 磁盘缓存直接复用；不要自建图片磁盘缓存。
5. `/api/Series/{id}` 与 `/api/Series/volumes?seriesId=` 是稳定端点；`/api/Series/series-detail` 官方声明"可能随时变化"，仅作增强，失败需降级。
6. 服务端可能返回 `Read Only` 角色（Demo 站），此时隐藏所有写操作入口（Auth Key 创建/偏好写回/评分/进度都会 403）。
7. `GET /api/Reader/get-progress` 无进度时返回 `pageNum=0` 且 `volumeId/seriesId/libraryId` **全为 0**；这些 id 必须取自 `chapter-info` 或章节 DTO。
8. 权限/归属错误不一定返回标准码：`GET /api/ReadingList?readingListId=` 无权限时返回 **400 + 纯文本**（`reading-list-restricted`），`GET /api/Collection/single?collectionId=` 无权限时返回 **404**。错误处理必须同时覆盖 400/403/404。
9. 分页端点**必须显式传 `pageSize`**：`UserParams.PageSize` 默认是 `int.MaxValue`。
10. `/api/Plugin/authenticate` 返回的 `UserDto` **没有 roles/preferences**，API Key 登录后必须再 `GET /api/Account` 补全。
11. SignalR 实时推送（`/hubs/messages`）在 v1 **不使用**，改为进入前台时刷新 + 下拉刷新；后续可选接入，禁止短轮询（< 30s）。

---

## 5. 阅读器规范（产品命脉）

三种内容，三条渲染路径，统一 `ReaderContract`：

| `MangaFormat` | 渲染方式 |
| --- | --- |
| Image(0) / Archive(1) / Unknown(2) | Compose 分页/双页/Webtoon + Coil |
| Epub(3) | 服务端解包章节 → WebView（`Book/{id}/book-page` + CSS 注入） |
| Pdf(4) | `GET /api/Reader/pdf` → 系统 `PdfRenderer`（本地缓存文件）分页渲染 |

- 格式判断以 `ChapterInfoDto.seriesFormat` 为准，**不信文件扩展名**。
- 进度保存：**页面变化 800ms 防抖** + `ON_PAUSE`/`ON_STOP` 立即提交 + 排队失败重试；退出阅读器必须已落盘。
- 预取：当前页 ±2 页；Webtoon 向下 5 页；滚动到 `pages-3` 时预热下一章第 1 页（`prefetchStartOfChapter`）。
- 翻页方向 `ReadingDirection`(0=LTR/1=RTL)、布局 `LayoutMode`(1 单页/2 双页/3 双页漫画)、分页模式 `ReaderMode`(0 左右/1 上下/2 Webtoon)、图片适配 `ScalingOption`(0 高/1 宽/2 原图/3 自动)、切分 `PageSplitOption`(0..3) 必须与服务端阅读配置模型一致。
- 阅读配置优先级（服务端 `GET /api/reading-profile/{libraryId}/{seriesId}` 已按此解析）：**本系列的 implicit > 系列绑定 > 库绑定 > 默认配置**；同一层中绑定当前设备的配置优先。客户端只需"临时覆盖（本次会话）> 服务端返回值"两层，不要自己再推算层级。`skipImplicit=true` 时忽略 implicit（用于"写回上级配置"场景）。
- 阅读器内改设置：调用 `POST /api/reading-profile/series?libraryId=&seriesId=&deviceId=`（写 implicit），或用户选择"保存到上级"时用 `POST /api/reading-profile/update-parent?...`。只读用户只做本地临时生效，**不调用任何写接口**。
- 沉浸式：隐藏系统栏 + 防息屏 + 可调亮度（仅本 App 会话，不落服务端）+ 音量键翻页（可关）。
- 章节切换：`GET /api/Reader/next-chapter|prev-chapter?seriesId=&volumeId=&currentChapterId=`；读到最后一页且 `nextChapterId>0` 时显示"下一章"过渡页，不自动跳（可在设置开启自动）。
- 阅读清单/合集进入时按清单顺序取下一章（`/api/ReadingList/next-chapter`），不按系列顺序。
- 无网络 → 明确"需联网"状态，不显示假图片；封面按隐私模式策略处理（见 §6）。
- 长按翻页区/双击：可配置（默认单击中部=菜单，双击=书签）。手势冲突处理见 `docs/PAGES.md`。

---

## 6. 设计系统

- **主题色**：主色 `#4AC694`（薄荷绿）；深色主题色 `#2D7A56`。浅色主题用主色，深色主题用深主题色作为主色（对比度需过 WCAG AA）。M3 动态取色**关闭**（避免破坏封面视觉），封面主色可作为详情页渐变背景（`SeriesDto.primaryColor/secondaryColor`）。
- **暗色优先**（阅读产品），支持跟随系统。
- **隐私模式**（全局开关，主界面右上角）：
  - 开启后**所有封面/缩略图不发起网络请求**，直接渲染深灰占位图（`#3A3A3A`，来自素材表 `privacy_placeholder`），卡片布局与进度信息保持不变。
  - 用 `PrivacyMask: StateFlow<Boolean>`（DataStore 持久化，按服务器独立），`CoverUrlBuilder`/Coil `Interceptor` 短路请求——**不是加载后打码，而是根本不请求**。
  - 阅读器内的正文页不受影响（隐私模式只管封面/缩略图/搜索结果图）。
  - 开关状态立即生效于所有已组合的列表。
- Tokens：4pt 栅格；圆角 `8/12/16/24`；封面比例 **2:3**（服务端封面约 1000×1500）。
- 字体：正文系统默认字体，标题 500-600 字重；漫画标题最多 2 行 ellipsis。
- 组件必须来自 `core/designsystem`，禁止屏幕内自造卡片/按钮样式；所有交互元素 ≥ 48dp 点击区。
- 空状态/加载/错误三态为**强制**：骨架屏（非转圈）用于列表，错误必须给"重试"，空态必须给"下一步动作"。
- 付费/上游服务（Kavita+）能力标注 `K+` 徽标；不可用时隐藏而非报错。
- 无障碍：`contentDescription` 全覆盖，动态字号到 200% 不截断，双页阅读器支持 TalkBack 朗读页码。
- **多语言**：`values/`（中文，基准）、`values-en/`、`values-fr/`、`values-ja/` 四套**必须同步交付**；默认跟随系统，可在设置中强制指定。新增文案四语缺失视为 CI 失败（`lintMissingDefaultResource`/等价检查）。
- **素材规范**：所有图片与按钮图标走 **SVG → PNG** 管线，清单与占位替换流程见 `docs/ASSETS.md`；开发期一律使用自动生成的占位 SVG/PNG，**禁止**手绘或临时用 Material 图标顶替最终素材。

---

## 7. 数据与同步（无下载模块）

> **明确范围**：本 App 不做章节/系列下载与离线阅读（`/api/Download/*`、`/api/Reader/image` 批量落地一律不实现）。以下只描述缓存与进度同步。

- Room 只存**索引与状态**（服务器档案、系列/章节轻量投影、进度、书签、待同步队列），不存整本书内容。
- 图片只靠 **Coil 磁盘缓存**（上限 512MB，按 `serverId` 分目录隔离）；隐私模式下不写入、不请求。
- 进度同步队列：`pending_progress(serverId, chapterId, pageNum, bookScrollId, updatedAt, attempt, state)`；同章节只保留最新一条（覆盖写）；联网成功后删除；冲突策略 = 本地较新且未同步成功则本地优先，否则服务端优先。
- 进度队列**不依赖 WorkManager**：用协程 + `SyncCoordinator`（前台触发：翻页防抖、`ON_STOP`、恢复联网、启动时补传）。
- 数据库升级必须写 `Migration` + `room.schemaLocation` 导出，禁止 `fallbackToDestructiveMigration`。
- 图片缓存/设置清理入口在"设置 → 存储"，按服务器维度清理。

---

## 8. 测试与质量门禁

| 层 | 要求 |
| --- | --- |
| 单元测试 | 每个 Mapper、进度换算、自然排序、URL 构造、过滤器编码必须有测试 |
| 网络契约 | MockWebServer 回放真实响应（含 `Pagination` 头、纯文本错误、401→刷新） |
| UI | 关键屏幕 Compose 测试（服务器选择、登录、首页、系列详情、阅读器翻页/进度、隐私模式） |
| 截图 | Roborazzi 覆盖 暗/亮 + 手机/平板 + 隐私模式开关两态 |
| i18n | 四语言资源齐全性检查（中/英/法/日），缺失即 CI 失败 |
| 素材 | 新增图片/图标必须登记到 `docs/ASSETS.md`（占位→正式状态列） |
| CI | `./gradlew ktlintCheck detekt testDebugUnitTest assembleDebug` 必须绿 |
| 覆盖率 | 新代码行覆盖 ≥ 70%（domain/data 层优先） |

提交前自检：

1. 没有硬编码字符串/颜色/尺寸（走 tokens 与 strings）。
2. 没有新增未使用依赖。
3. 没有把 token/密码写入日志或 `SharedPreferences` 明文。
4. 新增 API 调用已在 `docs/API.md` 登记（接口 + 用到的字段 + 陷阱）。
5. 三态（Loading/Empty/Error）齐全。
6. 横屏/平板/大字号手动验证过。

---

## 9. 构建与提交

```bash
# 需要 JDK 17；本机路径：D:\tools\jdk-17.0.21+9
./gradlew :app:assembleDebug          # 构建
./gradlew :app:testDebugUnitTest      # 单测
./gradlew :app:generateAssets         # 从 assets/manifest.json 生成全部 PNG 素材
./gradlew :app:checkAssetsRegistered  # 校验 res/ 下没有未登记素材
./gradlew ktlintFormat detekt         # 格式化与静态检查（M1 接入）
./gradlew :app:connectedDebugAndroidTest
```

- 分支：`main` 可发布；功能分支 `feat/<scope>`；修复 `fix/<scope>`。
- 提交：Conventional Commits（`feat(reader): ...` / `fix(api): ...` / `docs: ...`），一次提交一件事。
- PR 必须包含：改动动机、截图/录屏（UI）、测试说明、`docs/` 是否需同步。
- `local.properties`、签名文件、任何服务器地址/token **不得入库**。
- Release 构建：混淆 + 资源压缩；崩溃日志仅本地可选导出，不上传。

---

## 10. 绝对禁止（会直接被打回）

- ❌ 调用 Admin 专属端点（`/api/Library/create|update|delete`、`/api/Users/delete-user`、`/api/Server/*` 管理类、`/api/Settings/*`、`/api/License/*` 写操作）。本 App 是**读者客户端**，不是管理端。
- ❌ 抓取/解析 Kavita 网页 DOM；只用 REST API。
- ❌ 在图片 URL 里使用 JWT（会过期且泄漏到日志）。
- ❌ 用 OPDS key 调业务 API（它只是读者阅读 key，行为受限）。
- ❌ 破坏性数据库迁移。
- ❌ **实现下载/离线阅读模块**（`/api/Download/*`、批量落地页面、离线阅读页）——明确的范围外需求。
- ❌ 欢迎页加插画、价值主张文案（只允许 logo）。
- ❌ 隐私模式下发起封面请求，或用"加载后打码"代替"不请求"。
- ❌ 新增图片/图标未登记 `docs/ASSETS.md`，或用 Material 内置图标替代正式素材。
- ❌ 新增文案只写一种语言（中/英/法/日四套同步）。
- ❌ 未经用户操作自动标记已读（"读完一章"必须是进度自然到达末尾）。
- ❌ 将用户书库信息、封面、阅读记录发往任何第三方。
- ❌ 在 `main` 直接 push 未通过 CI 的代码。

---

## 11. 里程碑（对应 `docs/FEATURES.md`）

| 阶段 | 状态 | 范围 | 出口标准 |
| --- | --- | --- | --- |
| **M0 骨架** | ✅ 已完成 | 工程搭建、设计系统（#4AC694/#2D7A56）、多服务器添加/选择、登录（密码 + API Key）、网络层（分页/错误/明文门禁）、首页三流 + 继续阅读大卡、隐私模式开关、四语言、素材管线 | 构建通过、32 个单测通过、多服务器可添加/选择/登录、首页展示 On Deck、隐私模式可开关 |
| **M1 可读** | ✅ 已完成 | 库内系列网格（排序 + 无限滚动）、系列详情（卷折叠/章节/标记已读）、漫画阅读器（单页翻页/分区点击/页码跳转/书签/章节切换）、进度同步（防抖 + Room 写后队列 + 断网补传）、图片 URL 按会话生成 | 构建通过、54 个单测通过、可完整读完一卷并跨设备续读 |
| **M2 好用** | ⬜ 进行中 | 搜索、筛选排序、合集/阅读清单、想读、双页/Webtoon 阅读模式、阅读设置 | 主要路径四语言可用；封面受隐私模式控制 |
| **M3 完善** | ⬜ | EPUB/PDF 阅读器、阅读配置（Reading Profiles）、统计、批注/评分、平板双栏、素材正式替换 | 功能对齐 Web 端读者主路径；占位素材替换完成 |
| **M4 打磨** | ⬜ | 性能、无障碍、截图测试、Play 上架物料 | 冷启动 < 2s，滚动 60fps |

### M1 落地记录

- 新增数据层：`VolumeDto/ChapterDto/ChapterInfoDto/SaveProgressRequest/BookmarkDto`、`ReaderApi`、`SeriesDetailApi`、
  `ContentMapper`（**唯一的 pageNum↔pageIndex 换算点**）、`LibraryRepository`、`ProgressRepository`、Room 队列
  `pending_progress`（主键 `serverId+chapterId`，新值覆盖旧值）。
- 新增页面：`LibraryGridScreen`（自适应网格 + 触底加载 + 排序）、`SeriesDetailScreen`（Hero/整体进度/卷折叠/
  章节行/标记已读）、`MangaReaderScreen`（`HorizontalPager` + 三分区点击 + 双击书签 + 底部滑块 +
  章末"下一章"条）。
- 导航：`library/{id}`、`series/{id}`、`reader/{chapterId}`；章间跳转用 `popUpTo` 替换栈顶，避免无限堆叠。
- **图标合规**：移除 `material-icons-extended` 依赖，全部图标改由 `KativaIcon` + `KativaIcons` 从
  素材清单（新增 `ic_expand_less/more`、`ic_sort`、`ic_visibility/off`、`ic_read/unread`）生成，
  清单现为 **50 项 / 218 个 PNG**。
- 已知待办（M2）：双页/Webtoon 模式、阅读设置抽屉、卷详情页、`SessionStore` 的 Keystore 加密。

### M0 落地记录（供后续参考）

- JDK：本机无 JDK 17，已安装到 `D:\tools\jdk-17.0.21+9`；`local.properties` 指向 `D:\Program\Android SDK`。
- `applicationId = com.kativa.reader`，debug 后缀 `.debug`；`minSdk 26 / target 35 / compile 35`。
- 已实现：`ServerRepository`（多服务器 + 自动登录单选）、`SessionStore`（按 serverId 隔离）、`ActiveServerStore`、
  `StartupRouter`、`ApiFactory`（按 baseUrl 缓存 Retrofit）、`AuthInterceptor`、`CleartextGateInterceptor`、
  `PaginationInterceptor`、`ErrorMapper`、`CoverUrlBuilder`、`PrivacyModeState`、四语言资源、`Splash/Welcome/
  ServerPicker/ServerSetup/Login/Home/Library/Me/Settings` 页面。
- 素材：`tools/assets` 子工程（jsvg 2.2.0）+ `assets/manifest.json`（43 项）→ 183 个 PNG；应用图标由
  `assigns/kavita.svg` 渲染（108dp 前景安全区 / 48dp 传统图标）。
- 已知待办：`SessionStore` 目前为明文 DataStore，Keystore 加密列为 M1 前置；`Bookmark/Progress` 队列与阅读器随 M1 落地。

---

## 12. 参考

- Swagger UI：<https://www.kavitareader.com/docs/api/#/>
- OpenAPI 规范（文档页实际加载源）：<https://raw.githubusercontent.com/Kareadita/Kavita/develop/openapi.json>
- API 指南：<https://wiki.kavitareader.com/guides/api/>
- 阅读配置：<https://wiki.kavitareader.com/guides/user-settings/reading-profiles/>
- 漫画阅读器：<https://wiki.kavitareader.com/guides/readers/comic-manga/>
- 本仓库规划：`docs/PLAN.md`（总览）、`docs/PAGES.md`（页面）、`docs/FLOWS.md`（流程）、`docs/FEATURES.md`（功能）、`docs/API.md`（接口）、`docs/ASSETS.md`（素材）
