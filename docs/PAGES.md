# 页面设计（Pages）

> 统一约定：顶部 `TopAppBar` 支持大标题滚动收起；页面骨架 = 状态栏 → 内容 → 底部导航（阅读器与启动流程除外）。
> **主界面（首页/书架/发现）右上角统一带搜索图标与隐私模式开关；「我的」页不设该开关（隐私模式可在设置中切换）。无通知点、无通知中心。**
> 每个页面列出：目标 / 结构 / 关键组件 / 状态 / 交互 / 接口。组件名对应 `core/designsystem`。
> **所有文案四语言（中/英/法/日）；所有图片/图标来自 `docs/ASSETS.md` 素材表（占位 → 正式）。**

---

## P00 Splash

- **目标**：最短时间决定去向。
- **结构**：品牌 logo（只有 logo，无插画）+ 呼吸进度动画。
- **逻辑**：读启动偏好 → 设置了自动登录服务器且会话有效 → 预取 `POST /api/Series/on-deck?pageNumber=1&pageSize=1` 暖场 → 该服务器首页；否则 → 服务器选择页（P01）。
- **状态**：正常（<1.2s）；无服务器 → P00A 欢迎页；无网络但有会话 → 服务器选择页（条目标"上次不可达"）。
- **接口**：`GET /api/Account`（可选，验证 token）。

---

## P00A 欢迎页（仅 logo）

- **目标**：首次安装的第一印象；极简。
- **结构**：深色底 + **居中 logo**（唯一元素，素材 `logo_full.png`）。
- **交互**：点击 logo 不触发任何行为；自动（1.2s 后）或首次点击屏幕 → 进 P00B 添加服务器。
  - 无插画、无价值主张/宣传文案、无轮播。
- **状态**：无。

---

## P00B 服务器设置（添加/编辑服务器）

- **目标**：把服务器地址收进来。
- **结构**
  - `OutlinedTextField`（URL）、命名输入框（可选，默认取 host）。
  - `Switch` 允许明文连接（该服务器独立）、`Switch` 启动时自动登录此服务器。
  - 主按钮「测试连接」→ 成功后变「保存并登录」。
- **组件**：`UrlField`、`InlineError`、`TrustCertificateSheet`、`KativaPrimaryButton`。
- **状态**：空闲 / 测试中（按钮 loading）/ 成功（显示版本徽标）/ 失败（错误条 + 重试）。
- **交互**：输入自动规范化（补协议、去尾斜杠、识别子路径）；粘贴含 apiKey 的 URL 自动拆解并填入。
  - **返回键**：首次进入（P00A 欢迎页 → 本页）**不显示**返回箭头，本页即启动根页；由 P01 添加服务器或编辑已有服务器进入时才显示，用于 pop 回上一页。
- **接口**：`GET /api/Health`、`GET /api/Server/server-info-slim`。

---

## P01 服务器选择页

- **目标**：启动后第一屏；默认**不自动登录**，让用户选服务器。
- **结构**
  - 大标题「选择服务器」。
  - 服务器卡片列表：名称、地址、登录状态徽标（已登录 / 需登录 / 无法连接）、最近使用时间。
  - 底部主按钮「+ 添加服务器」。
- **组件**：`ServerCard`、`StatusPill`、`KativaPrimaryButton`、`EmptyState`。
- **状态**：空（无服务器 → 直接进 P00A 欢迎页）/ 列表 / 连接中（卡片内 spinner）/ 单卡错误（红标 + 重试）。
- **交互**：
  - 点卡片 → 会话有效进首页；否则进 P02 登录；连不上 → 卡片展开「编辑 / 重试 / 删除」。
  - 卡片右上角菜单只有「编辑」「删除」（二次确认）。**不含**「启动时自动登录」——该选项只属于 P00B 的开关，避免两处入口、两套文案。
  - 「启动时自动登录」为单选，在添加/编辑服务器时设置；默认值 =「不自动登录」。
- **接口**：`GET /api/Health`（探测）、`GET /api/Server/server-info-slim`（版本徽标）。

---

## P02 登录

- **目标**：两种凭据都能进。
- **结构**
  - 服务器行（可点击返回修改）。
  - `TabRow`：账号密码 / API Key。
  - 账号密码：用户名、密码（可见切换）、「登录」、记住用户名。
  - API Key：单个输入框 + 说明（"在 Web 端 用户设置 → Auth Keys 生成"）+ 粘贴按钮。
  - 底部：版本号 + 服务端版本。
- **状态**：空闲 / 提交中 / 错误（表单内联）/ 锁定（429 或多次失败，显示倒计时）。
- **交互**：键盘 IME 动作 = 登录；错误聚焦到对应字段；密码错误抖动一次。
- **接口**：`POST /api/Account/login`、`GET /api/Plugin/authkey-expires`。

---

## P03 首页（Home）

- **目标**：3 秒内续读。
- **结构（自上而下）**
  1. `HomeTopBar`：问候 + **隐私模式开关** + 搜索图标（**无通知点、无通知中心**）。
  2. `ContinueReadingCard`：封面 + 进度环 + 系列/章节 + 「继续第 N 页」。
  3. `StreamSection("On Deck")`：横向列表（20 条），标题右侧「查看全部」。
  4. `StreamSection("最近更新")`：按章节更新时间，卡片显示"第 X 章 新增"。
  5. `StreamSection("最近添加")`：新入库系列。
  6. 智能筛选流（用户配置，默认不显示）。
  7. 底部留白（避免被导航遮挡）。
- **组件**：`SeriesPosterCard`、`ProgressRing`、`SectionHeader`、`StreamRow`、`SkeletonRow`、`PrivacyToggle`。
- **状态**：骨架屏（3 行占位）/ 内容 / 空（"书库还是空的" + 去书架）/ 错误（整页重试）。
- **交互**：下拉刷新；点击卡片 → 系列详情；长按 → 快捷操作（标记已读、想读）；点击流标题 → 全量列表页；隐私模式开关切换 → 全屏封面立即变占位图。
- **接口**：`GET /api/Stream/dashboard?visibleOnly=true`、`POST /api/Series/on-deck`、`POST /api/Series/recently-updated-series`、`POST /api/Series/recently-added-v2`、`GET /api/Reader/get-progress`。

---

## P04 书架（Library 列表）

- **目标**：按库进入。
- **结构**：列表（每行：库封面 16:9 缩略、名称、类型标签、系列数、最近扫描时间、右侧箭头）。空态引导。
- **组件**：`LibraryRow`、`TypePill`、`EmptyState`。
- **状态**：加载 / 内容 / 空 / 错误。
- **交互**：点击 → P05；长按复制库 ID（调试隐藏手势）。
- **接口**：`GET /api/Library/libraries`。

---

## P05 库详情（系列网格）

- **目标**：浏览并筛选出想读的系列。
- **结构**
  - `TopAppBar`：库名 + 视图切换（网格/列表）+ 筛选图标（带激活角标）+ 排序图标。
  - 可选 `JumpBar`（A-Z 侧边索引，按 `sortName`）。
  - 内容：`LazyVerticalGrid` 2 列（手机）/ 4-6 列（平板）；卡片 = 封面 + 标题 2 行 + 进度条 + 未读数。
  - 底部：加载更多（滚动到 80% 自动请求）。
- **组件**：`SeriesPosterCard`、`FilterChipRow`、`JumpBar`、`SortSheet`、`FilterSheet`。
- **状态**：骨架网格 / 内容 / 空（"没有匹配的系列" + 清除筛选）/ 错误 / 加载更多失败（行内重试）。
- **交互**：点击 → P06；长按 → 多选模式（批量标记已读/加入清单/想读）；下拉刷新。
- **接口**：`POST /api/Series/v2`（含 `statements:[{field:19(Libraries),comparison:0,value:"<id>"}]`）、`GET /api/Library?libraryId=`。

---

## P06 系列详情

- **目标**：决定"读哪一章"并管理进度。
- **结构**
  1. `SeriesHero`：背景 = 封面模糊 + primaryColor 渐变；前景封面 2:3（宽 120dp）+ 标题 + 元数据行（年份 · 状态 · 卷/章数 · 评分）+ 主按钮。
  2. 进度条（整体）+ 「已读 X / Y 章」。
  3. `TabRow`：卷章 / 简介 / 相关 / 评论(M3)。
  4. 卷章列表：卷头（可折叠，显示卷号/名称/进度）→ 章行（章号 + 标题 + 页数 + 状态图标）。
  5. 底部悬浮「继续阅读」FAB（滚动时出现）。
- **组件**：`SeriesHero`、`PrimaryActionButton`、`ProgressBar`、`VolumeHeader`、`ChapterRow`、`MetaChip`、`RatingStars`。
- **状态**：首屏骨架 / 内容 / 404（已删除）/ 错误。
- **交互**
  - 主按钮逻辑：无进度→「开始阅读」；有进度→「继续第 N 页」；全读完→「重新阅读」。
  - 章行点击 → 阅读器；右侧 `⋯` → 底部菜单（标记已读/未读、书签、加入清单）。
  - 卷头长按 → 整卷标记已读。
  - 主按钮右侧的**爱心图标** = 想读（收藏）开关：空心添加、实心（品牌绿）已在想读，点击立即乐观切换。想读**不在**右上 `⋯` 菜单里。
  - 右上 `⋯`：标记全部已读、加入合集、刷新元数据（仅管理员，见 API.md §2）。
- **接口**：`GET /api/Series/{id}`、`GET /api/Series/volumes`、`GET /api/Reader/has-progress`、`GET /api/Reader/continue-point`、`POST /api/Reader/mark-read`、`POST /api/Reader/mark-volume-read`、`GET /api/Series/metadata`。

---

## P07 卷详情 / 章节详情（可选，M2）

- **目标**：单卷/单章的元数据与操作聚合。
- **结构**：头图 + 标题 + 页数/字数/阅读时长估算（`GET /api/Reader/time-left-for-chapter`）+ 章列表（卷页）/ 上一章下一章（章页）+ 操作区。
- **接口**：`GET /api/Series/volume?volumeId=`、`GET /api/Series/chapter?chapterId=`、`GET /api/Reader/next-chapter`。

---

## P08 阅读器 · 漫画（Manga Reader）

- **目标**：零干扰、零丢进度。
- **结构（三层）**
  1. **内容层**：`HorizontalPager`（单页/双页）或 `LazyColumn`（Webtoon/上下）。
  2. **覆盖层**（点击中部显现）：顶部栏（返回、系列名、页码 `12 / 180`、书签、设置）、底部栏（上一章、进度滑块、下一章）。
  3. **手势层**：`pointerInput` 处理点击分区/滑动/双击/长按；与 pager 的滑动仲裁。
- **组件**：`ReaderPage`、`ReaderTopBar`、`ReaderBottomBar`、`PageSlider`（`KativaSlider` + 拖动气泡）、`ReaderSettingsSheet`、`ChapterTransitionBar`、`PageErrorPlaceholder`。
- **状态**：`Preparing`（黑屏 + 章节名 + 进度）/ `Ready` / `PageError` / `ChapterEnd` / `NoNetwork`。
- **交互**
  - 单页 LTR/RTL、双页（按 `PageSplitOption`/`doublePairs` 配对，宽页独占）。
  - Webtoon：连续滚动，图片宽度撑满，滚动位置映射页码。
  - 双击书签；长按跳页；音量键翻页；屏幕常亮。
  - 设置抽屉：翻页模式 / 阅读方向 / 页面布局 / 图片适配 / 封面单独显示 / 亮度 / 页码 / 音量键。
    - **每个控件都必须真正生效**，且只在其有意义的模式下出现：阅读方向与页面布局仅左右翻页可见（纵向/条漫没有水平方向与对开页概念），图片适配在条漫下隐藏（条漫固定按宽度撑满），封面单独显示仅在双页布局下出现。
  - **正文页不受隐私模式影响**（隐私模式只屏蔽封面/缩略图）。
- **细节**
  - 上下覆盖条为**半透明 scrim（75% 黑）+ 20dp 渐变**，从屏幕边缘一直铺到条内内容；条内内容让出系统栏安全区（状态栏/导航栏/横屏刘海），因此系列名不会与系统时钟重叠。
  - 条带的实心区域挂惰性命中节点：落在内边距上的触摸不再穿透到 pager 触发翻页；按钮与滑块行为不变。
  - 三种翻页模式（左右/上下/Webtoon）**共用同一套覆盖层与设置抽屉**，任何模式下都能随时打开设置；切换模式保留当前阅读位置。
  - 页角显示极淡页码（可关）。
  - 图片适配：适应高度=`ContentScale.Fit`、适应宽度=`FillWidth`、原始尺寸=`None`（`ReaderScaling` 唯一映射出口）。
  - 首图未加载时显示占位图（隐私模式下用 `privacy_placeholder`，否则用该章封面模糊图）。
  - 双页配对异常（服务端 `doublePairs` 缺失）时降级单页并提示一次。
- **接口**：`GET /api/Reader/chapter-info`、`/api/Reader/image`、`/api/Reader/get-progress`、`POST /api/Reader/progress`、`/api/Reader/next-chapter|prev-chapter`、`/api/reading-profile/{libraryId}/{seriesId}`、`POST /api/Reader/bookmark`。

---

## P09 阅读器 · EPUB（Book Reader）

- **目标**：接近原生电子书体验。
- **结构**：WebView（全屏，注入 CSS）+ 顶部（返回/目录/书签/设置）+ 底部（进度百分比 + 滑块）+ 目录抽屉 + 设置抽屉。
- **组件**：`EpubWebView`、`TocDrawer`、`ReaderSettingsSheet(epub 变体)`、`HighlightToolbar`(M3)。
- **交互**：左右点击翻页（`bookScrollId` 记录锚点）；双指缩放（可选）；主题（白/棕/黑）；字号/行距/边距实时预览。
- **接口**：`GET /api/Book/{chapterId}/book-info|chapters|book-page|book-resources`、`POST /api/Reader/progress`（带 `bookScrollId`）。

---

## P10 阅读器 · PDF

- **目标**：稳定渲染、可缩放。
- **结构**：自绘 `Canvas`/`LazyColumn` 页面位图；顶部/底部同漫画；缩放手势。
- **组件**：`PdfPageView`、`PageBitmapCache`。
- **接口**：`GET /api/Reader/pdf?chapterId=&apiKey=`（拉取到 app cache 后 `PdfRenderer` 渲染；非下载功能）。

---

## P11 搜索

- **目标**：快速定位。
- **结构**：搜索框（自动聚焦）+ 历史 chips + 结果分组（系列网格 / 其他为列表行）。
- **状态**：初始（历史）/ 输入中 / 加载 / 结果 / 空 / 错误。
- **交互**：点击历史回填；长按删除单条；"清空历史"。
- **接口**：`GET /api/Search/search?queryString=&includeChapterAndFiles=false`。

---

## P12 发现

- **目标**：非"按库"的探索入口聚合。
- **结构**：4 个入口卡（合集 / 阅读清单 / 浏览 / 想读）+ 横向精选（最近更新、最高评分（K+））。
- **子页面**
  - 合集列表（`GET /api/Collection`）→ 合集详情（`GET /api/Series/series-by-collection`）。
  - 阅读清单（`POST /api/ReadingList/lists`）→ 详情（`GET /api/ReadingList/items`）。
  - 浏览：类型/标签/作者（`GET /api/Metadata/genres`、`tags`、`/api/Person/all`）。
  - 想读（`POST /api/want-to-read/v2`）。

---

## P13 服务器管理（设置内）

- **目标**：多服务器的增删改与切换（与 P01 同数据源）。
- **结构**：
  - 顶部：当前激活服务器卡片（名称、地址、用户、版本、状态）。
  - 列表：每条 = 名称 + 地址 + 状态徽标 + 最近使用；右侧 `⋯`（编辑 / 设为启动自动登录 / 删除）。
  - 底部：「+ 添加服务器」。
- **状态**：列表 / 单条连接失败（红标 + 重试）/ 空（跳 P00B）。
- **交互**：点条目 → 切换服务器（会话有效直接进首页）；删除需二次确认并说明"仅删除本机记录"。
- **接口**：`GET /api/Health`、`GET /api/Server/server-info-slim`、`POST /api/Account/login`（切到需登录的服务器）。

---

## P14 我的

- **结构**：用户头（头像/用户名/角色 + 当前服务器名）、统计摘要（本周阅读时长、读完章节数、连续天数）、快捷入口（切换服务器、书签、想读、设置）、退出登录。
- **顶栏**：仅返回箭头与标题，**不放隐私模式开关**（与首页/书架/发现区分；隐私模式在设置里切换）。
- **快捷入口（已落地）**：
  - **我的书签** → P14a，入口对所有角色可见（读取）；阅读器内的"加书签"按钮仅对 `Bookmark`/`Admin` 角色显示。
  - **想读** → P12 的想读列表（`POST /api/want-to-read/v2`）。
- **接口**：`GET /api/Stats/user-stats`、`GET /api/Users/profile-info`。

### P14a 我的书签

- **入口**：我的 → 我的书签。
- **结构**：2:3 卡片网格；封面 = 书签页缩略图（`GET /api/Image/bookmark?chapterId=&pageNum=`），标题 = 系列名，副标题 = `章节名 · 第 N 页`。
- **交互**：点击 → 直接进入该章该页（路由 `reader/{chapterId}?page={0-based index}`）；返回后列表**重新加载**（书签可在阅读器里增删）。
- **状态**：加载 / 空（"在阅读器里双击页面即可添加书签"）/ 错误（可重试）。
- **隐私**：缩略图属于封面类，隐私模式下直接渲染占位图（暗色深灰 / 亮色浅灰），**不发请求**。
- **接口**：`POST /api/Reader/all-bookmarks`（返回全部，客户端不分页）。

---

## P15 设置（分组）

| 组 | 项 | 来源 |
| --- | --- | --- |
| 启动 | 自动登录的服务器（单选 / 不自动登录=默认） | 本地（按 serverId） |
| 服务器 | 服务器列表、添加、切换、删除 → P13 | 本地 + `Health` 探测 |
| 账户 | 用户名、邮箱、改密码、年龄限制、Auth Keys | `POST /api/Account/update/username`、`/update/email`、`/reset-password`、`/update/age-restriction`、`/auth-keys` |
| 阅读 | 阅读配置列表、默认方向、音量键、自动下一章、预取 | `GET /api/reading-profile/all` |
| 外观 | 主题(暗/亮/系统)、主题色（#4AC694/#2D7A56，固定）、隐私模式、封面尺寸(紧凑/标准) | 本地 |
| 语言 | 中文 / English / Français / 日本語（默认跟随系统） | 本地（AppCompatLocale） |
| 网络 | 超时、清除图片缓存（按服务器）、明文开关（每服务器） | 本地 |
| 关于 | 版本、构建号、致谢、开源许可、反馈 | 本地 |

---

## P16 阅读配置编辑

- **结构**：表单分区（漫画 / EPUB / PDF），每项一个 `SettingRow`（开关/滑块/分段控件/颜色选择）。
- **底部**：保存 / 重置 / 删除（默认配置不可删）。
- **交互**：实时预览小窗（漫画显示示例页；EPUB 显示示例段落）。
- **接口**：`POST /api/reading-profile/create|update|delete`、`POST /api/reading-profile/set-devices`。

---

## 通用组件清单（`core/designsystem`）

| 组件 | 用途 |
| --- | --- |
| `SeriesPosterCard` | 2:3 封面卡（标题、进度、未读点；隐私模式下封面为占位图） |
| `SeriesListRow` | 列表视图的行 |
| `ProgressRing` / `KativaProgressBar` | 进度（`KativaProgressBar` 用 Canvas 自绘，避免 M3 1.3 的轨道间隙与末端圆点） |
| `KativaSlider` | 统一滑块（4dp 细轨 + 14dp 圆点；可选拖动气泡） |
| `SectionHeader` | 区块标题 + 「查看全部」 |
| `KativaPrimaryButton` / `SecondaryButton` | 主/次行动 |
| `TypePill` / `MetaChip` | 类型/元数据标签 |
| `EmptyState` | 空态（图标 + 文案 + 行动） |
| `ErrorState` | 错误（原因 + 重试） |
| `SkeletonGrid` / `SkeletonRow` | 加载占位 |
| `FilterSheet` / `SortSheet` | 筛选排序 |
| `ReaderSettingsSheet` | 阅读设置 |
| `BottomActionSheet` | 长按菜单 |
| `PrivacyToggle` | 顶栏隐私模式开关（眼睛/遮罩图标，激活高亮） |
| `KativaTopAppBar` | **全应用唯一顶栏**（屏幕禁止直接用 Material `TopAppBar`）；亮色主题用品牌色 `#4AC694` 铺满，暗色主题用 `surface`；文字/图标用 `onPrimaryContainer`（白字配薄荷绿对比度仅 ~2.2:1，不过 AA）；在品牌色顶上发布 `LocalOnBrandSurface`，让内部强调色（隐私开关、筛选激活图标、搜索下划线）改用墨色而非薄荷绿 |
| `PrivacyCoverPlaceholder` | 隐私模式封面占位（暗色 `#3A3A3A` / 亮色 `#E9EBEF` + 淡 logo 水印，2:3；底色由 `PrivacyFill`/`PrivacyFillLight` 决定） |
| `ServerCard` / `StatusPill` | 服务器选择页卡片与状态徽标 |
| `NoNetworkBanner` | 全局"无网络"提示条 |
| `KavitaSnackbar` | 统一提示（含重试动作） |

---

## 响应式断点

| 宽度 | 布局 |
| --- | --- |
| < 600dp | 底部导航；网格 2 列；详情单栏 |
| 600-839dp | 网格 3-4 列；详情单栏（更宽留白） |
| ≥ 840dp | `NavigationRail`；列表-详情双栏；网格 5-6 列；阅读器可双页默认 |

横屏：阅读器强制沉浸；列表页保持顶部栏；详情页头图高度压缩至 160dp。

---

## 文案与素材

- 每个页面的所有用户可见文案在 `values/`（中文基准）+ `values-en/` + `values-fr/` + `values-ja/` 各一份；日期/数字按当前 `Locale` 格式化。
- 每个页面引用的图片/图标在 `docs/ASSETS.md` 登记：用途、尺寸、密度（`drawable-xxhdpi` 优先 PNG）、当前状态（占位/正式）。
- 页面新增元素时**先登记素材再写 UI**；占位素材由脚本自动生成，正式素材到位后按清单批量替换。
