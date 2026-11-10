# AGENTS.md

> 本文件是 **Kavita for Android** 的工程宪法：给 AI 代理与人类贡献者的执行契约。
> 动手写代码前必须读完；与本文件冲突的实现一律视为缺陷。
> 产品/页面/流程规划见 `docs/PLAN.md`、`docs/PAGES.md`、`docs/FLOWS.md`、`docs/FEATURES.md`、`docs/API.md`、`docs/ASSETS.md`。

---

## 1. 项目定位

| 项目 | 说明 |
| --- | --- |
| 产品名 | **Kavita**（Android 客户端） |
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
> `minSdk 26`、`targetSdk 35`、`compileSdk 35`；`applicationId = "com.kavita.reader"`（改名只改 `app/build.gradle.kts` 一处 + 包名迁移脚本）。

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
com.kavita.reader
├─ KavitaApp.kt                  @HiltAndroidApp
├─ MainActivity.kt
├─ core/
│  ├─ network/                   OkHttp、Retrofit、AuthInterceptor、TokenAuthenticator、PaginationInterceptor
│  ├─ auth/                      ServerProfileStore（多服务器）、SessionManager（DataStore + Keystore 加密）
│  │                             AuthRepository、StartupRouter（决定启动进哪个服务器）
│  ├─ image/                     KavitaImageLoader、CoverUrlBuilder、PrivacyMask（隐私模式短路请求）
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
   ├─ navigation/                KavitaNavHost、Destinations、DeepLinks
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
| API Key 登录 | `POST /api/Account/login` `{apiKey}`，或 `POST /api/Plugin/authenticate?apiKey=&pluginName=Kavita`（返回 token，便于旧版/无密码场景） |
| 业务请求 | `Authorization: Bearer <token>` |
| 刷新 | `POST /api/Account/refresh-token` `{token,refreshToken}` → 新 `{token,refreshToken}`；用 OkHttp `Authenticator` 做**单飞（single-flight）**刷新，失败即登出 |
| 主动刷新 | 解析 JWT `exp`，提前 5 分钟刷新（避免阅读中 401） |
| 图片/媒体 URL | **必须**带 `?apiKey=<imageKey>`（服务端从 query 取 key），并同时带 Bearer 头 |
| 设备标识 | 每个请求带 `X-Device-Id: <本地生成的 UUID>`；`User-Agent: Kavita/<version> (Android <api>; Mobile)` |

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
- 网络异常 → 统一 `KavitaError`：`Network / Timeout / Unauthorized / Forbidden / NotFound / BadRequest / Server / Parse / Unknown`，UI 只消费这层。
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
- **编号展示铁律**：`VolumeDto`/`ChapterDto` 的 `minNumber` 带哨兵值 —— `-100000` = **无编号**，`100000` = **番外容器卷**，`0` = 真实第 0 卷。这些值**绝不允许直接渲染**；所有卷/章标题必须走 `ui/common/EntityTitles.kt`，判定用 `domain/model/KavitaNumbering.kt`。番外与散章必须从常规卷列表拆出单独成区块。（曾有线上 bug 把 `Vol. -100000` 显示给用户；详见 `docs/API.md` §2「哨兵编号」。）
- 进度保存：**页面变化 800ms 防抖** + `ON_PAUSE`/`ON_STOP` 立即提交 + 排队失败重试；退出阅读器必须已落盘。
- 预取：当前页 ±2 页；Webtoon 向下 5 页；滚动到 `pages-3` 时预热下一章第 1 页（`prefetchStartOfChapter`）。
- 翻页方向 `ReadingDirection`(0=LTR/1=RTL)、布局 `LayoutMode`(1 单页/2 双页/3 双页漫画)、分页模式 `ReaderMode`(0 左右/1 上下/2 Webtoon)、图片适配 `ScalingOption`(0 高/1 宽/2 原图/3 自动)、切分 `PageSplitOption`(0..3) 必须与服务端阅读配置模型一致。
- 阅读配置优先级（服务端 `GET /api/reading-profile/{libraryId}/{seriesId}` 已按此解析）：**本系列的 implicit > 系列绑定 > 库绑定 > 默认配置**；同一层中绑定当前设备的配置优先。客户端只需"临时覆盖（本次会话）> 服务端返回值"两层，不要自己再推算层级。`skipImplicit=true` 时忽略 implicit（用于"写回上级配置"场景）。
- 阅读器内改设置：调用 `POST /api/reading-profile/series?libraryId=&seriesId=&deviceId=`（写 implicit），或用户选择"保存到上级"时用 `POST /api/reading-profile/update-parent?...`。只读用户只做本地临时生效，**不调用任何写接口**。
- 沉浸式：隐藏系统栏 + 防息屏 + 可调亮度（仅本 App 会话，不落服务端）+ 音量键翻页（可关）。
- 章节切换：`GET /api/Reader/next-chapter|prev-chapter?seriesId=&volumeId=&currentChapterId=`；读到最后一页且 `nextChapterId>0` 时显示"下一章"过渡页，不自动跳（可在设置开启自动）。
- 阅读清单/合集进入时按清单顺序取下一章（`/api/ReadingList/next-chapter`），不按系列顺序。
- 无网络 → 明确"需联网"状态，不显示假图片；封面按隐私模式策略处理（见 §6）。
- 手势：单击左/右区翻页、单击中部=菜单；**双击=放大当前页（放大后可拖动/捏合，缩回原比例或再次双击退出）**；书签只在顶部栏按钮。手势冲突处理见 `docs/PAGES.md`。

---

## 6. 设计系统

- **主题色**：主色 `#4AC694`（薄荷绿）；深色主题色 `#2D7A56`。浅色主题用主色，深色主题用深主题色作为主色（对比度需过 WCAG AA）。M3 动态取色**关闭**（避免破坏封面视觉），封面主色可作为详情页渐变背景（`SeriesDto.primaryColor/secondaryColor`）。
- **顶栏**：全应用只有 `core/designsystem/KavitaTopAppBar`，屏幕禁止直接用 Material `TopAppBar`。亮色主题顶栏铺满品牌色 `#4AC694`，文字/图标用 `onPrimaryContainer`（近黑，`#07130E`）——白字配薄荷绿只有 ~2.2:1，不过 AA。品牌色顶上不能再用薄荷绿做强调色，改用 `LocalOnBrandSurface`（`kavitaTopBarIconTint()`）切到墨色。`TopBarContractTest` 守卫这两条。
- **填充件一律用主题色（不加深）**：主按钮、选中态分段控件、开关轨道、首页「搜索」大卡等**大面积填充**用 `#4AC694` + 墨色 `OnBrand` 内容，**不要**用深绿 `#2D7A56`（它只用于浅色主题的 `primary` 文案/图标墨色与暗色主题容器）。开关统一走 `core/designsystem/KavitaSwitch`。小指示器（RadioButton 圆点等）保留 `primary`，因为薄荷绿细环在白底上对比不足。
- **暗色优先**（阅读产品），支持跟随系统。
- **隐私模式**（全局开关，主界面右上角）：
  - 开启后**所有封面/缩略图不发起网络请求**，直接渲染纯色占位图——暗色主题 `#3A3A3A`、浅色主题 `#E9EBEF`（`#3A3A3A` 在白色卡片里像个黑洞）——来自素材表 `privacy_placeholder` 的淡色水印叠在该底色上，卡片布局与进度信息保持不变。底色由 `PrivacyCoverPlaceholder` 按 `LocalDarkTheme` 选取 `PrivacyFill`/`PrivacyFillLight`。
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

> **铁律：编译通过 ≠ 能启动。** 见 §8.1。

| 层 | 要求 |
| --- | --- |
| 单元测试 | 每个 Mapper、进度换算、自然排序、URL 构造、过滤器编码必须有测试 |
| **启动冒烟（强制）** | 任何影响启动的改动，必须真机或模拟器**冷启动**验证，见 §8.1 |
| 网络契约 | MockWebServer 回放真实响应（含 `Pagination` 头、纯文本错误、401→刷新） |
| UI | 关键屏幕 Compose 测试（服务器选择、登录、首页、系列详情、阅读器翻页/进度、隐私模式） |
| 截图 | Roborazzi 覆盖 暗/亮 + 手机/平板 + 隐私模式开关两态 |
| i18n | 四语言资源齐全性检查（中/英/法/日），缺失即 CI 失败 |
| 素材 | 新增图片/图标必须登记到 `docs/ASSETS.md`（占位→正式状态列） |
| CI | `./gradlew ktlintCheck detekt testDebugUnitTest assembleDebug` 必须绿 |
| 覆盖率 | 新代码行覆盖 ≥ 70%（domain/data 层优先） |

### 8.1 启动冒烟（M0/M1 血的教训）

**背景**：M0 交付的 APK 安装后**启动即闪退**。原因是 `MainActivity : AppCompatActivity`
（为了应用内语言切换），但 `themes.xml` 的父主题写成了系统主题
`android:Theme.Material.NoActionBar`。AppCompat 在 `setContentView` 时强制校验主题，
不匹配就抛：

```
java.lang.IllegalStateException: You need to use a Theme.AppCompat theme
  (or descendant) with this activity.
  at androidx.appcompat.app.AppCompatDelegateImpl.createSubDecor
```

当时只跑了 `assembleDebug` + 单测，都是绿的，**没有任何自动化环节会发现它**。教训是：
**运行时契约类问题（主题、Manifest、基类、Hilt 绑定、Room schema、资源 id）只有真启动才能暴露。**

#### 必须做启动冒烟的改动清单

只要碰到以下任意一项，**提交前必须冷启动一次**（真机或模拟器都行）：

| 类别 | 具体项 |
| --- | --- |
| 主题 / 样式 | `themes.xml`、`styles.xml`、`AndroidManifest` 的 `android:theme`、夜间/横屏变体 |
| Activity / Application | 基类变更（`Activity`↔`AppCompatActivity`↔`ComponentActivity`）、`android:name`、`configChanges` |
| Manifest | 新增 `<activity>`/`<service>`/`<provider>`、权限、`android:exported`、`networkSecurityConfig`、`allowBackup` |
| DI | Hilt 模块增删、`@Inject` 构造变化、`@HiltAndroidApp`/`@AndroidEntryPoint` 迁移 |
| 数据库 | Room `entities`/`version`/`Migration` 变化（首次安装与升级两条路径都要试） |
| 资源 | 新增 `values-*` 目录、`drawable` 引用改名、字符串格式化占位符（`%1$d` 与参数不匹配会在运行期抛） |
| 依赖 | 新增/移除 AAR、`appcompat`/`material`/`compose` 大版本变动、`minSdk`/`targetSdk` 变动 |
| 启动路径 | `SplashViewModel`/`StartupRouter`/导航起点、`MainActivity.onCreate` 里的任何逻辑 |

#### 判定标准

1. 冷启动后进程**存活**（`adb shell pidof <pkg>` 非空），且 logcat **无 `FATAL EXCEPTION`**。
2. 首屏 UI 实际渲染出来（用 `adb shell uiautomator dump` 校验关键文案存在，不能只看截图）。
3. **异常路径不崩**：例如地址非法时点“测试连接”，应显示错误文案而不是退出。
4. 深链/参数异常不崩：`adb shell am start -n <pkg>/<Activity>`（无参）与带参数各一次。

#### 一次性命令（本地模拟器）

```bash
# 1. 启动模拟器（本机 AVD：KativaTest，android-36，WHPX 加速；AVD 名未随包名改名）
"D:\Program\Android SDK\emulator\emulator.exe" -avd KativaTest -no-snapshot -gpu swiftshader_indirect -no-audio &

# 2. 等启动完成
adb wait-for-device
adb shell 'while [ "$(getprop sys.boot_completed)" != "1" ]; do sleep 1; done'

# 3. 装 + 冷启动 + 看是否存活
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb logcat -c
adb shell am force-stop com.kavita.reader
adb shell am start -n com.kavita.reader/com.kavita.reader.MainActivity
sleep 8
adb shell pidof com.kavita.reader        # 必须非空
adb logcat -d | grep "FATAL EXCEPTION"         # 必须为空

# 4. 校验首屏真的渲染了
adb shell uiautomator dump /sdcard/ui.xml && adb shell cat /sdcard/ui.xml | head -c 3000
```

#### 已知主题约束（别再踩）

- `MainActivity` 是 `AppCompatActivity`，**主题必须是 `Theme.AppCompat.*` 的后代**。
  当前为 `Theme.AppCompat.DayNight.NoActionBar`。
- Compose 负责全部视觉，XML 主题只提供窗口背景与系统栏透明——但那一点点也**足以导致启动崩溃**。
- 如果将来要改成 `ComponentActivity`（放弃 AppCompat），必须同时迁移掉
  `AppCompatDelegate.setApplicationLocales`，改用 `LocaleManager`（API 33+）或自建
  `attachBaseContext` 包装，并重跑本节全部检查。

#### 提交前自检

1. 没有硬编码字符串/颜色/尺寸（走 tokens 与 strings）。
2. 没有新增未使用依赖。
3. 没有把 token/密码写入日志或 `SharedPreferences` 明文。
4. 新增 API 调用已在 `docs/API.md` 登记（接口 + 用到的字段 + 陷阱）。
5. 三态（Loading/Empty/Error）齐全。
6. 横屏/平板/大字号手动验证过。
7. **碰过 §8.1 清单里的任何一项？→ 已做冷启动冒烟，并贴出 `pidof` + 无 FATAL 的证据。**
8. 新增/修改了带占位符的字符串？→ 参数个数与类型已核对（`%1$d` 不能传给 String）。

---

## 9. 构建与提交

```bash
# 需要 JDK 17；本机路径：D:\tools\jdk-17.0.21+9
./gradlew :app:assembleDebug          # 构建
./gradlew :app:packageDebugApk        # 构建并输出到 dist/Kavita-v<versionName>.apk
./gradlew :app:testDebugUnitTest      # 单测
./gradlew :app:generateAssets         # 从 assets/manifest.json 生成全部 PNG 素材
./gradlew :app:checkAssetsRegistered  # 校验 res/ 下没有未登记素材
./gradlew ktlintFormat detekt         # 格式化与静态检查（M1 接入）
./gradlew :app:connectedDebugAndroidTest
```

**版本号唯一事实来源 = `app/build.gradle.kts` 的 `versionName` / `versionCode`。**

交付 APK 一律用 `:app:packageDebugApk`，不要手工改名复制——文件名由构建脚本从 `versionName`
派生，保证「文件名 = APK 元数据 = 设置页显示」。曾发生过 `versionName` 仍是 `0.1.0` 而手工
交付物被命名为 `v0.3.10` 的事故。每次交付都要递增 `versionCode`。

- 分支：`main` 可发布；功能分支 `feat/<scope>`；修复 `fix/<scope>`。
- 提交：Conventional Commits（`feat(reader): ...` / `fix(api): ...` / `docs: ...`），一次提交一件事。
- PR 必须包含：改动动机、截图/录屏（UI）、测试说明、`docs/` 是否需同步。
- `local.properties`、签名文件、任何服务器地址/token **不得入库**。
- Release 构建：混淆 + 资源压缩；崩溃日志仅本地可选导出，不上传。
- **发布命名**：APK 统一为 `Kavita-v<versionName>.apk`（不带变体后缀），包名统一 `com.kavita.reader`（debug 不再加 `.debug`）；发布正文见 `docs/RELEASE_NOTES.md`。当前无签名配置，发布物即为 debug 签名构建。

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
| **M2 好用** | ✅ 已完成 | 双页/上下/Webtoon 三种模式 + 阅读设置抽屉（方向/布局/缩放/封面单独/亮度）；哨兵编号修复（`-100000`/`100000` 不再外泄，散章卷显示"全一章/单话"）；搜索（防抖/分组/历史）；筛选与排序面板（字段/比较符/条件 chips）；合集；阅读清单；想读（含系列详情加入/移出）；**我的书签**（我的页入口 + 书签页缩略图网格 + 直达书签页） | 构建通过、103 个单测通过、lint 0 error、四语言 261 条对齐 |
| **M3 完善** | ⬜ | EPUB/PDF 阅读器、阅读配置（Reading Profiles）、统计、批注/评分、平板双栏、素材正式替换；**另加 §11 记录的真缺陷**（阅读清单详情、首页流接 dashboard、Token 刷新、只读角色收敛写操作、TOFU 证书） | 功能对齐 Web 端读者主路径；占位素材替换完成 |
| **M4 打磨** | ⬜ | 性能、无障碍、截图测试、Play 上架物料 | 冷启动 < 2s，滚动 60fps |

> **功能完成度以 `docs/FEATURES.md` §7 为准**：那里是 v1.2.0 逐条回代码核对后的真实状态（含 6 项上修、9 项降级）。
> 上面的 M2 范围列写的是"做过什么"，不代表每项都做完了 —— 例如「阅读清单」只做了列表且行点击无响应。

### M2 落地记录

- **阅读器**：`ReaderSettings`（方向/翻页模式/布局/缩放/封面单独/亮度/页码），`PagePairing` 纯逻辑处理宽页独占与配对，
  `ReaderSettingsSheet` 底部抽屉。切换布局会重映射 reading position，不会跳页。
- **编号**：`KavitaNumbering`（哨兵常量 + `containsSentinel` 词边界扫描）、`Chapter.safeTitle`、`Volume.safeName`
  （因为服务端会把 `Title` 预格式化成 `"Chapter -100000"`，只查数值字段不够）、`EntityTitles` 唯一格式化出口。
- **搜索**：`SearchRepository` + `SearchScreen`，300ms 防抖、**1 字符门槛**（服务端单字即可命中）、会话内历史、分组渲染。
- **筛选**：`SeriesFilter`/`SeriesFilterField`/`Comparison`/`SeriesSort` 领域枚举严格对齐 `docs/API.md` §12；
  `FilterSheet` + 条件 chips；库范围永远作为第一条 statement 注入，用户条件无法越出库。
- **书架**：`ShelfRepository` + `ShelfScreen`（一个页面三种 kind：合集/阅读清单/想读），
  想读可在系列详情菜单加入/移出（失败回滚）。
- **文案**：四语言 261 条对齐；筛选/书架文案用脚本生成，避免手工编辑破坏 XML。

### M2 修复记录（测试反馈）

| 问题 | 根因 | 修复 |
| --- | --- | --- |
| **所有封面不显示** | `coil-network-okhttp` 只是普通 JVM 依赖，其 `ServiceLoader` target **在 Android 上不生效**；从未显式注册 `OkHttpNetworkFetcherFactory`，Coil 对每个 http(s) 请求静默失败 | `KavitaApp.newImageLoader` 显式 `add(OkHttpNetworkFetcherFactory(...))`，复用同一个 OkHttp（沿用明文门禁），并显式配置内存/磁盘缓存 |
| **"查看更多"列表滚动几下闪退** | 「最近更新」按**章节**分组，同一系列会在不同页重复出现；用 `series.id` 作 Compose key → 重复 key 崩溃 | 列表 key 改为分页位置派生的唯一值（`s{page}_{index}`），保留条目身份稳定性 |
| **系列详情显示 "One or more validation errors occurred"** | 该文案是 ASP.NET `ValidationProblemDetails`，被当成普通 message 直接展示；且 `hasProgress`/`continuePoint` 失败会拖垮整页 | `ErrorMapper` 解析 `errors` 字段映射为 `字段: 原因`；详情页把进度/想读降级为**可选增强**，失败不再阻断渲染 |
| 首页缺资料库入口 | 首页只有流，没有库 | 新增库文件夹卡片，改为 2 列网格 |
| 系列列表重复请求 | 无缓存 | 新增 `SeriesListCache`（按 serverId+参数签名、TTL 2 分钟、上限 40 条、不缓存失败） |
| **API Key 登录恒 400 "One or more validation errors occurred"** | `LoginRequest` 的 `username`/`password` 声明了 `= ""` 默认值，而 kotlinx.serialization **默认省略等于默认值的字段**，实际只发 `{"apiKey":"…"}`；Kavita 的 `LoginDto` 把二者标记为必填 → 400 | 新增 `WireJson`（`encodeDefaults = true`）作为唯一序列化入口，并用 `WireSerializationTest` 断言**精确线上负载**。MockWebServer 抓不到这类 bug：它验证的是"意图"，而字段是被编码器静默丢弃的 |
| **保存服务器后登录仍走 https**（`SSLException: Unable to parse TLS packet header`） | 裸 `host:port` 默认补 `https://`（正确的安全默认），但自建 Kavita 常只开明文；此前只有"测试连接"按钮会回退 http，保存与登录都不会 | 在 `ServerSetupViewModel.save()` 中对未验证的 https 档案先探测：https 失败且判定为 TLS 错误时再探 http，**只有真的通了才**填入 `http://` 并开启该服务器的明文开关，同时给出警告并要求用户**再点一次保存确认**（不静默降级，符合 §4.1）。请求层**不做**自动降级 |
| **搜索箱是 Material 的盒式 `OutlinedTextField`，方框+四角在顶栏里显得笨重** | 直接用了 Material 默认外形，没走设计系统 | 新增 `core/designsystem/KavitaTextField.kt: KavitaUnderlineField`（`BasicTextField` + `decorationBox`，只有底部一条横线：静止 1dp `outline`、聚焦 2dp `primary` 带动画；`heightIn(min=48.dp)` 保住点击区；清除按钮由调用方 `trailingIcon` 传入）。搜索页改用该组件 |
| **输入框在空态与输入态高度不一致（下划线上下跳）** | 组件高度由子节点撑开：空态没有尾随 Clear 按钮，行高只有一行文字；输入后 `IconButton` 把行撑到 48dp，整个字段长约 6dp，横线随之位移。同时下划线参与布局、`1dp↔2dp` 的聚焦动画本身也会挤动一像素 | 高度改为**与内容无关**：`fieldHeight = max(48dp, 行高 + 4dp + 2dp)`，行高从 `typography.bodyLarge.lineHeight × fontScale` 推导（大字号自动变高、不裁切）；下划线放进独立的 2dp 槽位，粗细变化不再影响布局。真机实测 font1.0 `y=88..220 h=132`、font2.0 `y=79..228 h=149`，空/输入/清空三态**像素级一致** |
| **中文内容搜不到，被怀疑是编码问题** | **不是编码问题**（日志里 `queryString=%E8%83%BD%E5%8A%9B`，服务端 200 正常）。两个真实原因：① Kavita 的 `/Search/search` 只搜 `includeInSearch=true` 的资料库（`GetLibraryIdsForUserIdAsync(QueryContext.Search)`），而用户中文漫画所在的「系列」库 `includeInSearch=false`；② 服务端只做 SQL `LIKE %q%` 子串匹配，**不做简繁/异体折叠**（`樂園`→命中、`乐园`→0；`女兒`→命中、`女儿`→0）。同为 CJK 的日文假名可正常命中 | 按产品决定**不改搜索行为**，只把机制写进 `docs/API.md` §6（含自查 `includeInSearch` 的方法与简繁限制），并修正 `docs/FLOWS.md` 中过期的"≥ 2 字符"门槛 |
| 首页搜索按钮无障碍标签写成 "View all" | `HomeScreen` 里搜索 `IconButton` 误用了 `R.string.home_view_all` | 改用 `R.string.search_title`（四语言已有该键）；TalkBack 与自动化都能正确定位 |
| **粘贴 OPDS 链接后被拼成 `/api/opds/<key>/api/`** | `ServerUrl.normalize` 只识别 `?apiKey=`，把 `/api/opds/<key>` 当成子路径保留；`extractedApiKey` 抽出来却从未被使用 | 新增 OPDS 后缀识别：剥掉 `/api/opds/<key>` 并把末段作为密钥返回；密钥经 `PendingApiKeyStore`（仅内存、一次性）交给登录页，自动切到「API 密钥」页签并预填 |
| **手动输入 `http://` 地址后登录报 "Network error"** | 保存时 `allowCleartext` 只取开关状态；用户显式写 `http://` 但没动开关，档案被存成 `http + allowCleartext=false`，明文门禁拦下**所有**请求 | 以地址栏里的 `http://` 作为明确同意：保存时同步把开关与档案置为允许明文（用户自己输入的协议本身就是选择） |
| **搜索单字无反应 / "不能搜多一个字"** | `MIN_QUERY_LENGTH = 2` 是照抄 Web 端 UI 的臆测门槛，且被旧单测写死。实测服务端 `/Search/search` 单字即可命中（`z`→2 部、`日`→6 部），只有空串返回 400。单字查询被本地**静默丢弃**，看起来就是搜索坏了 | 门槛降到 1，空白输入由客户端短路（不发给服务端）；同时修 `execute()` 的竞态：慢返回的旧请求不再覆盖新查询结果 |
| **搜索页显示 `Type at least %1$d characters` / `No results for "%1$s"`** | 带格式参数的字符串被 `stringResource(R.string.x)` **无参调用**，占位符原样渲染。可编译、无 lint 报错、不崩溃 | 两处都补上参数；新增 `ThemeContractTest` 守卫：扫描全部 Kotlin 源，凡调用含 `%` 的字符串却未传参即判失败（已反向验证该守卫确实能抓到本 bug） |
| **首页左右留白不一致（文件夹比流区更内缩）** | `LazyVerticalGrid` 外层 16dp + `SectionHeader`/`LazyRow` 各自内部 16dp → 叠加成 32dp | 统一为**单一** 16dp：只在网格 `contentPadding` 设边距，流区与标题不再自带横向 padding。实测标题/流卡片/文件夹卡片左缘均为 44px(=16dp) |
| **隐私模式在 2-3 级页面失效（详情/搜索/流列表封面照常加载）** | 两层错误叠加：① `ThemeViewModel` 用**嵌套 collect**（`active.collect { isPrivacyMode(id).collect { … } }`），内层永不结束 → 外层收不到「选择服务器」那次发射，`LocalPrivacyMode` 恒为 `false`；② 首页把 `coverUrl` 手动置 `null` 所以"看着对"，而二级页走 `CoverImage` 默认值就漏了。探针 `cache/image_cache` 生成封面文件即为铁证 | 隐私状态收敛到 `PrivacyModeState`（用 `combine`，进程级 scope，不依赖 ViewModel），`ThemeViewModel` 只做镜像；去掉各屏幕的 `privacyMode` 参数，统一由 `LocalPrivacyMode` 决定；再注册 `PrivacyGuardInterceptor` 在 Coil 层按路径兜底（`/Reader/image` 永不拦）。新增 `PrivacyModeStateTest` + `PrivacyGuardInterceptorTest`，真机验证 首页/详情/搜索/流列表 四级 `cache` 均无封面文件 |
| **阅读器进度条与亮度条"很丑"** | 两处都是**裸 `material3.Slider`**，零定制。M3 1.3 的滑块 token 是"16dp 厚轨道 + 4×44dp 竖条拇指 + 轨道末端 4dp stop dot"，且**未激活轨道取 `secondaryContainer`** —— 本主题没定义该 token，于是掉进 Material 默认紫灰 `#4A4458`；`LinearProgressIndicator` 同版本也自带 4dp 间隙 + 末端圆点，在封面卡 3dp 细条上就是一颗乱入的亮点 | 新增 `core/designsystem/KavitaSlider`（4dp 细轨 + 14dp 圆点，保留官方拖拽/48dp 触摸区/TalkBack 语义）与 `KavitaProgressBar`（Canvas 自绘，无间隙无圆点），替换 4 处调用；`KavitaTheme` 补齐 `secondaryContainer`/`inverseSurface`/`surfaceContainer*` 等 token 防再掉默认色 |
| **亮度滑块是死控件** | `ReaderSettings.brightness` 只被 `setBrightness` 写入、Sheet 读取，**没有任何消费方**；拖了毫无效果 | 改为内容层暗色遮罩（`ReaderDim`）：遮罩画在页面之上、控件之下，`background` 不消费指针事件所以手势照常；不从系统窗口亮度下手，避免与用户自动亮度打架、退出时残留。同时补上 `keepScreenOn`（AGENTS §5 防息屏） |
| **「显示页码」开关是死控件** | `showPageNumber` 同样只写不读 | 阅读器内容层渲染右下角极淡页码（`docs/PAGES.md` P08 早已要求），开关真正生效 |
| **拖动阅读进度条时页面抖动、进度反复重写** | `onValueChange` 每帧调 `goToPage()`，pager 被动滚动且每帧重置 800ms 保存防抖；`updateSettings` 又对**所有**设置（含 brightness/pageNumber）无条件 `buildSlots` + 重映射阅读位置 | 滑块改为拖动期间只更新本地状态、`onValueChangeFinished` 才跳页一次；`ReaderViewModel` 拆出 `updateVisualSetting`，视觉类设置不再重建槽位 |
| **阅读器顶部信息压住系统状态栏（时钟/电量与系列名重叠）** | `ReaderControls` 的上下条用 `Surface` 直接贴在 `TopCenter`/`BottomCenter`，阅读器本身是 edge-to-edge（`enableEdgeToEdge`），所以条带画在状态栏/导航栏**下面**，内容没有让出安全区 | 上下条改为 `TopChrome`/`BottomChrome`：**半透明 scrim（`KavitaColors.ReaderScrim`，75% 黑）+ 20dp 渐变过渡**从屏幕边缘一直铺到内容，内容再叠 `WindowInsets.systemBars.only(Top/Bottom + Horizontal)` 让出安全区（同时覆盖横屏刘海）。沿用 §6「暗色优先」：阅读器内强制系统图标为浅色，退出时还原 |
| **拖动进度条偶尔变成翻页** | 上下条只是 `Surface` + 文案/按钮，**本身没有 pointer-input 节点**；落在条带内边距、或滑块拇指与 48dp 触摸区之间的空隙的按压，命中测试直接穿透到下层 pager，横向位移就被判成翻页手势 | 给条带的实心区域挂一个**惰性命中节点**（`readerChromeTouchGuard`：只 `awaitFirstDown` 不消费），让条带成为该区域的命中目标、pager 彻底收不到这些事件；**不能**改成消费事件——那会让 `clickable` 在 Final pass 取消点击，返回/书签/设置按钮全废。`ChapterEndBar` 一并补上安全区内边距 |
| **切到上下连续滚动（Webtoon）后再也打不开设置** | Webtoon 分支里写了 `return@Box`，提前退出外层 `Box` lambda。而 `ReaderSettingsSheet` 就组合在**同一个 `Box` lambda 的下面**，于是被整体跳过：设置抽屉永远打不开，用户被困在滚动模式无法恢复 | 改为 if/else，两个分支都**落到**共享的 overlay/controls/settings；分页渲染抽成 `PagedReader`。同时把模式判定收敛到 `ReaderChrome`（`showsSettingsSheet`/`usesContinuousScroll`/`webtoonStartIndex`）并加 `ReaderModeSettingsTest` 守卫——已反向验证：把 `showsSettingsSheet` 改回依赖模式，测试立刻失败 |
| 切模式时进度被重置回第 1 页 | `WebtoonReader` 的 `LazyColumn` 无 `initialFirstVisibleItemIndex`，切换后从列表顶部开始，`LaunchedEffect(visiblePage)` 立刻把"第 1 页"写回，等于把章节进度倒退并落盘 | `startPage` 由 `ReaderChrome.webtoonStartIndex(state.pageIndex, totalPages)` 钳制后传入；实测 `seek=8 → webtoon=7 → horizontal=7`，进度不再倒退 |
| **「图片适配」是死控件（能选但不生效）** | `ReaderSettings.scalingOption` 只被 `setScaling` 写入、抽屉读取，渲染层永远硬编码 `ContentScale.Fit` —— 与之前的亮度/页码开关是同一类 bug | 新增 `ReaderScaling.contentScale()` 作为唯一映射出口（FitHeight→`Fit`、FitWidth→`FillWidth`、Original→`None`、Automatic→`Fit`），`PagerSlotContent` 单页与双页两处都改用它；条漫固定 `FillWidth` 并**在抽屉里隐藏该控件**，不提供渲染层会忽略的选项 |
| 阅读方向/页面布局出现在它们无效的模式下 | 方向是**水平**概念（镜像点击分区、pager 顺序、对开页左右顺序），纵向翻页与条漫恒为自上而下；页面布局同理，对开页需要左右并排 | `ReaderSettings.readingDirectionApplies` / `pageLayoutApplies`（均仅 `LeftRight`）控制抽屉可见性；渲染层把 `isRightToLeft` 与 `readingDirectionApplies` 取与，避免残留的 RTL 偏好在纵向模式下镜像点击分区 |
| 双页布局切到上下翻页后两页叠在一个槽位 | `isDoublePage = !isWebtoon && layoutMode != Single`，纵向模式下仍会 `buildSlots` 配对，而 `VerticalPager` 每槽一屏 | `isDoublePage` 收紧为 `readerMode == LeftRight && layoutMode != Single`；抽屉里布局选项也只在该模式下出现。`ReaderSettingsRulesTest` 覆盖（已反向验证会 FAIL） |
| **筛选完全不起作用**（现象：加条件后列表要么不变、要么空） | 三个独立缺陷叠加：① 名称搜索默认发 `Contains(5)`，而 Kavita 的 `ComparisonProfile` 只允许 `SeriesName` 用 `Equal/NotEqual/BeginsWith/EndsWith/**Matches**`，落库前直接抛异常 → 整个 `/Series/v2` 失败；② 界面暴露 16 字段 × 12 比较符 × And/Or 全矩阵，其中大量组合是服务端不接受的死组合，排序也只 `take(6)` 了个残缺子集；③ 面板「应用」后列表与顶部 chips 都有，但 `SeriesSort` 枚举名与 `sortField` 混用、`removeStatement` 按下标删会产生歧义 | 按产品决定**收敛为三项**：排序（名称 1 / 加入时间 2 / 阅读时间 7）、名称搜索（`SeriesName` + `Matches(7)`）、是否收藏（`WantToRead(26)`=="true"，即「想读」）。`SeriesFilter` 重写为只含这些字段，组合固定 `And(1)`，库范围由 `toLibraryScopedDto(libraryId)` 注入第一条并禁止外部覆盖；领域枚举不再保留服务端不接受的比较符，**非法组合无法被构造出来**。`WireSerializationTest` 断言精确线上负载（`{"field":1,"comparison":7,"value":"Love"}`），`SeriesFilterTest` 覆盖条件增删不重复。真机验证：搜索 `Love` → 200 + 4 条命中 |
| **加入想读后「仅收藏」筛选仍为空** | `SeriesListCache` 有 2 分钟 TTL，而「收藏」= 想读，属于服务端用户状态；`ShelfRepository` 的 add/remove 只调了接口，没有失效任何缓存，于是筛选命中旧页 | `ShelfRepository.add/removeToWantToRead` 成功后调用 `seriesCache.clearServer(activeId)`（只清当前服务器，符合 §4.1 的会话隔离）。真机验证：详情页「加入想读」→ 回列表 → 仅收藏筛选立即返回该系列 |
| 切筛选时旧请求回包覆盖新结果 | `LibraryGridViewModel.load()` 无请求序号，慢返回的旧条件结果会盖掉新条件 | 加单调 `requestId`，回包与当前序号不符即丢弃；末页判定改用 `Pagination.totalItems` 精确计算，仅 header 缺失时才退回"短页"启发式 |
| 排序没有方向，且方向会在字段间串味 | 面板只有排序字段，没有升/降序控件（`isAscending` 恒为 `true`） | 补回升序/降序两段式选择；`SeriesSort.defaultAscending` 定义各字段自然默认（名称升序、两个日期字段降序），`withSort()` 默认取该值，面板切换字段时同步重置方向——避免"按加入时间+升序"把最老的排最前 |
| **浅色主题下所有列表的封面遮罩是一层灰蒙蒙的黑渐变、标题白字发灰** | `SeriesPosterCard`（首页流/库网格/搜索/书架/书签/流列表共用的唯一列表卡片）把底部渐变写死为 `Color.Black.copy(alpha = 0.72f)` + `Color.White` 标题——这是深色主题的正确做法，浅色主题下黑纱罩在白卡上就成了灰色遮罩，白字也失去对比 | 新增 `KavitaColors.PosterScrimDark/PosterScrimLight`（黑 72% / 白 90%，均 ≥72% 不透明以压住封面），`SeriesPosterCard` 按 `LocalDarkTheme` 三处分叉：遮罩、标题（浅色改 `Color.Black`）、副标题与进度条轨道同步反色；深色主题取值不变。新增 `PosterScrimContractTest` 守卫（已反向验证：把遮罩改回写死黑色立即 FAIL）；阅读器 chrome 与详情页 hero 渐变属常暗/品牌色场景，不在本次范围 |

### M2 阅读器设置作用域（产品反馈，2026-11）

| 变更 | 说明 |
| --- | --- |
| **阅读设置此前无任何持久化** | `ReaderUiState.settings` 恒为 `ReaderSettings.Default`，切章节/退出即归零；跨章节时旧 entry 被 `popUpTo` 弹出、新 ViewModel 重建，设置连"本次会话"都保不住。服务端 `/api/reading-profile/*` 虽已文档化但从未接入（属 M3 范围） |
| **改为按系列本地持久化** | 新增 `core/settings/ReaderSettingsStore`（复用 `kavita_settings` DataStore，**不能**再开同名 delegate）+ `StoredReaderSettings`/`ReaderSettingsCodec`（枚举存 Kavita `apiValue` 数字，未知值回落不抛）+ `ReaderSettingsLayers.resolve` 实现 **系列 → 全局默认 → App 默认** 三级回落；key = `reader_settings_<serverId>_<seriesId>`，删除服务器时一并清理 |
| 亮度不持久化 | 亮度随环境而非作品变化，保持会话级（文案已写「仅本次阅读」）；`ReaderSettingsPersistence` 是唯一换算出口并显式丢弃该字段 |
| 全局默认的写入口 | 设置页新增「阅读 → 默认阅读设置」，复用同一个 `ReaderSettingsSheet`（`showBrightness=false`、无恢复行），避免两套 UI 漂移；设置页的 `SettingsUiState` 用嵌套 `combine` 合并 `globalSettings` |
| 抽屉新增「恢复本系列的阅读设置」 | 清掉系列 key 后回落到**全局**（不是出厂默认），并即时重映射槽位 |
| 抽屉新增「横屏显示」（真生效） | `ReaderOrientationEffect`：`DisposableEffect(forceLandscape)` 设 `requestedOrientation = SENSOR_LANDSCAPE`（系统关自动旋转也生效），退出恢复原值；Manifest 已有 `configChanges=orientation\|screenSize`，旋转不重建 Activity。按 AGENTS §8.1 做了冷启动冒烟 |
| 读设置必须早于首帧 | `load()` 在发布 session 的**同一次** `_state.update` 里先读持久化设置再 `buildSlots`，否则双页模式下会先按默认单页渲染再跳变、导致阅读位置闪一下 |
| 验证 | 12 个新单测（round-trip / 编码默认值 / 损坏 JSON / 未知枚举 / 亮度不落库 / 三级回落 / key 隔离）；真机：改垂直+横屏 → 退到首页重进仍保留（`cur=2400x1080`）→ 退出恢复竖屏（`cur=1080x2400`）→ 恢复本系列回到出厂默认并解除横屏 → 设置页改全局双页 → 该系列继承双页 → 系列内改单页覆盖全局；`run-as cat kavita_settings.preferences_pb` 可见 `reader_settings_global` 与 `reader_settings_<uuid>_<seriesId>` 两条 JSON |

### M2 阅读器 UI 修复（测试反馈，2026-11）

- 验证方式：`adb shell input swipe` 在条带内边距/渐变区（y=2100/2150/2300/2340/2380）横划均**不再翻页**，
  页面区横划仍正常翻页；`Page N of M` 位于 y=2034、`Back/Bookmark/Reading settings` 位于 y=116，均已在
  48px 系统栏之下；返回/书签/设置按钮点击正常，冷启动无 `FATAL EXCEPTION`。
- 模式切换验证（真机）：进入阅读器 → 拖到第 8 页 → 切 Webtoon（显示第 7 页）→ **在滚动模式下连续打开设置 3 次全部成功**
  → 切回 Horizontal 仍停在第 7 页。修复前同一路径下设置抽屉完全打不开。

### M2 阅读器交互调整（双击放大，2026-11）

| 变更 | 说明 |
| --- | --- |
| **阅读设置抽屉去掉全部图标** | `SettingBlock`/`SwitchRow` 不再接收 `@DrawableRes icon`，只保留标题文字（品牌色）。图标与行内文字挤在一起时视觉噪音大，且部分图标（`ic_brightness`/`ic_volume`/`ic_double_page`/`ic_webtoon`）语义与控件并不完全对应。`ic_settings_reader` 仍用于阅读器顶栏入口，素材未删除 |
| **双击 = 放大当前页** | 新增 `ui/reader/ReaderZoom.kt`（`ReaderZoom` 纯逻辑 + `ReaderZoomState` 状态）：双击以**双击点为中心**动画放大到 2.5×，再次双击回到原始比例；放大期间单指拖动、双指捏合（1×～5×），**捏回原始比例（`EXIT_SCALE`=1.05×）自动退出缩放**。缩放通过 `graphicsLayer` 在**绘制阶段**读取，拖动/动画不触发重组；外层 `clipToBounds()` 保证放大后的页面不溢出到相邻页与覆盖条 |
| **手势仲裁** | 放大时 `HorizontalPager`/`VerticalPager`/Webtoon `LazyColumn` 的 `userScrollEnabled = false`，由页面自己接管拖动，避免两套手势系统抢同一次 drag；未放大时 pager 独占，翻页与三分区点击完全不变。放大状态下单击只切菜单，不翻页（否则会把用户刚放大的位置翻走） |
| **缩放随页面复位** | `rememberReaderZoomState(state.currentSlot, readerMode, layoutMode)` 以 key 重建，翻页/切模式/切布局**同步**归零（不用 `LaunchedEffect`，否则会先渲染一帧旧缩放） |
| **"只能放大一次" bug（已修）** | 初版用**动画中的 `scale`** 推断"当前是否已放大"，且 `Job.cancel()` 是异步的：动画中途再双击时读到的是过期状态，被取消的旧协程还会补写最后一帧把 `scale` 拉回 1.0，于是 `isZoomed` 与实际渲染分叉 —— 之后每次双击都被判定为"退出"，**再也不能放大**。修法：放大与否改由**同步的意图标志 `targetScale`** 决定（`isZoomed = isZoomed(targetScale)`），动画只负责把 `scale` 追上去；并加单调 `animationId`，被取消的动画帧发现自己不再是当前动画就直接丢弃，不会覆盖新意图。`panBy`/`scaleBy` 同样同步写意图 |
| **"翻页后再也放不大" bug（已修）** | `rememberReaderZoomState(currentSlot, …)` 会**随翻页重建状态对象**（这是"翻页同步复位缩放"的实现方式），但 `pointerInput` 只在 key 变化时重建手势处理器；key 里没有 `zoomState`，所以翻页后闭包仍持有**上一页被丢弃的实例**，双击写到僵尸对象上，新页面永远不动。真机日志铁证：`compose slot=56 state=24854220` 而 `onDoubleTap state=39548202 slot=55`。修法：把 `zoomState` 加进 `pointerInput` 的 key（paged 与 Webtoon 两处），并新增 `ReaderZoomGestureTest` 直接解析 `pointerInput(...)` **参数列表**断言含 `zoomState`（只查整个代码块是无效的，因为回调体里本来就有这个名字） |
| 书签入口收敛 | 双击不再用于书签（`docs/FEATURES.md` F-17 已同步），书签只在阅读器顶栏按钮；只读角色的隐藏逻辑不变 |
| 文档 | `AGENTS.md` §5、`docs/PAGES.md` P08、`docs/FLOWS.md` 手势表同步更新 |
| 验证 | `ReaderZoomTest`（20 个纯逻辑）+ `ReaderZoomGestureTest`（4 个源码契约），`testDebugUnitTest` **279 个全绿**。**反证已做两次**：① 把 `nextDoubleTapScale(targetScale)` 改回 `(scale)` → 2 条回归测试 FAIL；② 把 `pointerInput` 的 `zoomState` key 删掉 → `pager gesture modifier keys on the zoom state it captures` FAIL。真机（模拟器 1080×2400）：连续 **4 页**「放大→退出→翻页→再放大」全部成功（进入 `124.17/113.21/115.14/144.53`，退出 `0.02/0.04/0/0`）；Webtoon 模式同样「放大→退出→再放大」成功（`56.81`）；连续 4 轮同页进出成功（`130.77/83.33/81.23/81.26`，拖动 `~56-70` 且页码不变）。阅读设置抽屉 `uiautomator dump`：**ImageView 节点 = 0** |

### M2 首页布局调整（产品反馈）

| 变更 | 说明 |
| --- | --- |
| 搜索框提示文案 | 由「搜索系列、合集、阅读清单、人物…」改为单词 **Search / 搜索 / Rechercher / 検索**（四语言同步） |
| 首页左上角 | 移除 `Hi, <用户名>` 问候语，改为**用户中心图标**（复用 `KavitaIcons.Me`，无需新增素材），点击进入「Me」页；用户名只在 Me 页出现 |
| 顶部「最新观看历史」 | 移除 ContinueReading 大卡（`HomeStream.ContinueReading` 及其卡片一并删除，不留死代码）。On Deck 流本身仍承载继续阅读条目 |
| 个人首页返回键 | 「Me」原本是底部页签、无返回概念；改为从首页用户中心图标进入后成了**入栈页面**，却没有退出路径（且全应用实际并未渲染底部栏，用户会被困住）。新增 `TopAppBar` 返回箭头：优先 `popBackStack()` 保留来路，仅当 Me 是栈底（深链/进程恢复）时才导航到 Home，避免 pop 直接退出应用 |
| Me 右上角重复设置入口 | Me 页既顶栏有 Settings 图标、正文又有 Settings 行，两个入口同一页面 | 移除顶栏图标，只保留正文菜单行 |
| 切换服务器页无返回键 | `ServerPickerScreen` 同时是**启动目的页**与「切换服务器」入口，原实现无返回逻辑（启动页不该有） | 新增可选 `onBack`，由路由参数 `showBack` 控制：从 Me/Settings 进入时显示返回箭头；启动进入时不显示，避免 pop 直接退出应用 |
| 设置页重复 Startup / Servers | 与 Me 页的「开机自动登录」「切换服务器」重复，且描述文案不一致 | 设置页只保留 Appearance + About；同时清理死参数 `onManageServers` 与死属性 `autoLoginName` |
| 资料库文件夹间距 | 与上方流区对齐到同一 16dp 栅格 |
| **文件夹内封面缩进与背景** | 封面拼图原本铺满整个顶部区域、四格紧贴，卡片背景是实色 `surface`。改为：拼图四周内缩 4dp、封面之间同 4dp 间隙（**同一个 token `INSET`，两者永不漂移**）、封面格加 3dp 圆角、卡片背景改为半透明灰（深色主题白色蒙版 `#1FFFFFFF`，浅色主题黑色蒙版 `#14000000`，均可透出页面底色）。隐私占位图继承同一缩进 |
| **隐私模式下文件夹内的遮罩图顶掉上边距、没有圆角** | `PrivacyCoverPlaceholder` 无条件带 `aspectRatio(2f/3f)`。poster 槽位（`SeriesPosterCard`/详情封面）的调用方本来就传了同一比例，所以看不出问题；但首页文件夹的槽位是**正方形**（`aspectRatio(1f)` + `padding(INSET)`），2:3 的占位图比槽位高，Compose 把它撑到槽位外，顶部 4dp 内边距被吃掉、圆角也被外层卡片的 16dp 方形裁剪顶替（像素实测：遮罩 `y=966..1445` 高 480px，而拼图槽位应为 `y=978..1434` 高 456px，宽度还只有 458px） | 组件改为**只填满调用方给的盒子**（去掉内部 `aspectRatio`），并新增 `shape` 参数（默认仍是 `PosterRadius`）；文件夹内传入与四宫格共用的 `MosaicCellShape`（3dp，**后被下方「圆角太小」一行改为 `PrivacyPanelShape` = 12dp**）。真机像素校验：遮罩变为 `458×458`、顶部起点 977（与 4dp 内边距一致）；`CoverImage` 的 poster 调用方各自保留比例，无回归。新增 `PrivacyPlaceholderSizingTest` 守卫（已反向验证：把 `aspectRatio` 加回去立即 FAIL） |
| **隐私模式下文件夹遮罩图圆角太小** | `LibraryFolderCard` 在隐私模式下把四宫格的 `MosaicCellShape`（3dp）直接套给了一整块占位面板。四宫格的 3dp 是「小格子」的圆角，而隐私面板是**一整块**封面大小的面，两者尺度不同；3dp 在 166dp 宽的面板上几乎看不出来，视觉上就是一块方角灰板贴在 16dp 圆角卡片里 | 新增 `PrivacyPanelShape = RoundedCornerShape(KavitaDimens.PosterRadius.dp)`（12dp，与全应用封面同源 token），隐私面板改用它；`MosaicCellShape` 只留给四宫格本身。真机像素校验（440dpi）：角落内缩从 3dp 的 ~6px 变为 12dp 的 ~28px，与 `SeriesPosterCard` 封面圆角一致；`PrivacyPlaceholderSizingTest` 改为断言 `shape = PrivacyPanelShape`、显式断言**不得**再出现 `shape = MosaicCellShape`，并新增一条断言 `PrivacyPanelShape` 必须由 `KavitaDimens.PosterRadius` 派生（已反向验证：改回 `MosaicCellShape` 立即 FAIL） |
| **文件夹统计数字全部显示 "4 series"** | 两层错误叠加：① `HomeViewModel` 用 `series.size`（预览图数量，上限 4）当总数；② 即便改用 `Pagination` 头也取不到——`PaginationInterceptor` 用 `ThreadLocal`，OkHttp 在自己的 dispatcher 线程写入、协程在另一线程读取，`consume()` **永远是 null**。`LibraryDto` 本身没有系列数字段，真实总数只存在于 `Pagination` 响应头 | API 改为返回 `retrofit2.Response<List<SeriesDto>>`，直接从 `response.headers()["Pagination"]` 解析 `totalItems`（天然按请求隔离，无线程问题）；新增 `SeriesPage{items,totalItems}` 并从 `totalOrPageSize` 兜底；`SeriesListCache` 一并缓存 `totalItems`，缓存命中不会退化。实测 4/40/85/71 与服务端一致；`PaginationInterceptor` 保留但已标注 **禁止用于读元数据** |

### M1 落地记录

- 新增数据层：`VolumeDto/ChapterDto/ChapterInfoDto/SaveProgressRequest/BookmarkDto`、`ReaderApi`、`SeriesDetailApi`、
  `ContentMapper`（**唯一的 pageNum↔pageIndex 换算点**）、`LibraryRepository`、`ProgressRepository`、Room 队列
  `pending_progress`（主键 `serverId+chapterId`，新值覆盖旧值）。
- 新增页面：`LibraryGridScreen`（自适应网格 + 触底加载 + 排序）、`SeriesDetailScreen`（Hero/整体进度/卷折叠/
  章节行/标记已读）、`MangaReaderScreen`（`HorizontalPager` + 三分区点击 + 双击书签 + 底部滑块 +
  章末"下一章"条）。
- 导航：`library/{id}`、`series/{id}`、`reader/{chapterId}`；章间跳转用 `popUpTo` 替换栈顶，避免无限堆叠。
- **图标合规**：移除 `material-icons-extended` 依赖，全部图标改由 `KavitaIcon` + `KavitaIcons` 从
  素材清单生成（新增 `ic_expand_less/more`、`ic_sort`、`ic_visibility/off`、`ic_read/unread`）。

### M0 落地记录（供后续参考）

- JDK：本机无 JDK 17，已安装到 `D:\tools\jdk-17.0.21+9`；`local.properties` 指向 `D:\Program\Android SDK`。
- `applicationId = com.kavita.reader`（**debug 变体不再加 `.debug` 后缀**，见 §9 与 1.1.0 发布说明）；`minSdk 26 / target 35 / compile 35`。
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
