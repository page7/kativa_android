# 功能规划（Features）

> 优先级：**P0** = MVP 必须有；**P1** = 首个正式版；**P2** = 后续增强。
> 状态列用 `⬜ 待办 / 🟨 进行中 / ✅ 完成`，实施时更新此表。
>
> **最后一次核对：v1.2.0（versionCode 30，2026-11）**。核对方式不是看文档自述，而是逐条回到代码确认
> 端点是否被调用、入口是否可达、状态是否真的流转，证据见 §7「状态核对记录」。本次修正：
> **⬜/🟨 → ✅ 6 项**（F-09、F-09b、F-12、F-13、F-16、F-17 页面书签，实为已完成但文档没更新）；
> **✅ → 🟨 9 项**（F-01、F-03、F-04、F-14、F-15、F-17 文本书签、F-18、F-20、F-35，做了一半却被记成完成）；
> 另有 F-23/F-28/F-29/F-32 补上了"到底做到哪一步"的说明。汇总见 §7，缺陷清单见 §7.3。
>
> 一句话结论：**代码比文档诚实**。文档里有 9 项"✅"实际只完成主干，其中 3 项（F-03 首页流、F-15 阅读清单、F-01 TOFU）
> 是用户能直接感知的功能缺口或点了没反应的死入口。

---

## 0. 能力矩阵（决定 UI 显隐）

| 能力 | 判定依据 | 影响 | 实现状态 |
| --- | --- | --- | --- |
| 写操作 | `UserDto.roles` 不含 `Read Only` | 隐藏标记已读/收藏/评分/配置保存 | 🟨 仅 `Session.isReadOnly` 有值并显示在 Me 页；标记已读/想读的按钮**尚未按角色隐藏** |
| 书签 | roles 含 `Bookmark` 或 `Admin` | 隐藏书签手势与菜单 | ✅ `Session.canBookmark` → `ReaderViewModel.canBookmark` → 阅读器顶栏按钮 |
| 推广合集/清单 | roles 含 `Promote` 或 `Admin` | 允许把私人清单设为公开 | ✅ 客户端只**展示** `Promoted` 徽标，无写操作（本就不做管理端） |
| 修改密码 | roles 含 `Change Password` 或 `Admin` | 设置页显示改密 | ⬜ 未实现（无改密入口） |
| 渲染扫描/元数据刷新 | `Admin` | 系列详情"刷新"菜单 | ⬜ 未实现（明确不做管理端） |
| Kavita+ 能力（评分/评论/推荐） | `UserDto.kavitaVersion` + 服务端返回的数据 | 数据为空即隐藏，标 `K+` | ⬜ 未实现（F-25/F-26 均未开始） |
| EPUB/PDF 支持 | `ChapterInfoDto.seriesFormat` | 按格式选阅读器 | 🟨 只做了**识别与明确拒绝**：`ReaderBlockReason.UnsupportedFormat` 显示"暂不支持该格式"，未做阅读器 |
| 隐私模式 | 本地开关（按服务器持久化） | 封面/缩略图不请求，显示 `privacy_placeholder` | ✅ `PrivacyModeState` + `PrivacyGuardInterceptor` 双层兜底 |

服务端版本兼容：以 `ServerInfoSlimDto.kavitaVersion` 为准，低于 `0.8.0` 时提示"建议升级服务端"，并关闭依赖新字段的功能（`doublePairs`、`reading-profile`、`want-to-read` 按接口探测降级）。

---

## 1. 功能总表

| ID | 功能 | 优先级 | 里程碑 | 状态 |
| --- | --- | --- | --- | --- |
| F-01 | 服务器添加/编辑与连通性检测 | P0 | M0 | 🟨 主流程 ✅；**TOFU 证书指纹未实现** |
| F-02 | 服务器选择页与启动路由（默认不自动登录 + 可指定自动登录服务器） | P0 | M0 | ✅ |
| F-03 | 首页流（On Deck / 最近更新 / 新入库） | P0 | M0 | 🟨 三条流硬编码；**未读 `Stream/dashboard`** |
| F-04 | 账号/API Key 登录、按服务器的会话保持、JWT 过期判定 | P0 | M0 | 🟨 Token **刷新未接线**（见 §7.2） |
| F-05 | **隐私模式**（顶栏开关，封面屏蔽） | P0 | M0 | ✅ |
| F-06 | 继续阅读（续点直达） | P0 | M1 | ✅ 大卡按产品决定移除；续点走 On Deck → 详情「继续」 |
| F-07 | 书架：库列表 → 系列网格 | P0 | M1 | ✅ |
| F-08 | 系列详情（卷/章、元数据、进度） | P0 | M1 | ✅ |
| F-09 | 漫画阅读器（单页 + 分区点击 + 页码跳转 + 章节切换） | P0 | M1 | ✅ |
| F-09b | 双页 / Webtoon / 上下翻页模式 + 阅读设置抽屉 | P1 | M2 | ✅ |
| F-10 | 阅读进度保存与跨设备续读 | P0 | M1 | ✅ |
| F-11 | 章节/整卷/整系列标记已读 | P0 | M1 | ✅ |
| F-12 | 搜索 | P1 | M2 | ✅ |
| F-13 | 筛选与排序（Filter v2） | P1 | M2 | ✅ |
| F-14 | 合集浏览 | P1 | M2 | 🟨 列表 + 详情已通；「全部阅读」未做 |
| F-15 | 阅读清单（顺序阅读） | P1 | M2 | 🟨 仅列表；**详情与清单顺序续读未做** |
| F-16 | 想读 | P1 | M2 | ✅ |
| F-17 | 书签（页面书签；文本书签随 EPUB） | P1 | M2 | 🟨 页面书签 ✅／文本书签依赖 F-21 |
| F-18 | 「我的」与账户设置 | P1 | M2 | 🟨 账户信息 + 服务器切换 + 设置 + 退出；**改密/Auth Keys/清缓存未做** |
| F-19 | 四语言（中/英/法/日） | P1 | M0 | ✅ |
| F-20 | 素材管线（SVG→PNG 占位 + 清单 + 批量替换） | P1 | M0 | 🟨 管线 ✅；**素材仍是占位** |
| F-21 | EPUB 阅读器 | P1 | M3 | ⬜ |
| F-22 | PDF 阅读器 | P1 | M3 | ⬜ |
| F-23 | 阅读配置（Reading Profiles） | P1 | M3 | ⬜ 当前只做**本地**按系列持久化，未接服务端 |
| F-24 | 阅读统计 | P1 | M3 | ⬜ |
| F-25 | 评分与评论（K+） | P2 | M3 | ⬜ |
| F-26 | 批注/高亮查看（K+） | P2 | M3 | ⬜ |
| F-27 | 相关作品/关系 | P2 | M3 | ⬜ |
| F-28 | 平板双栏与响应式 | P1 | M3 | ⬜ 已有 `material3-adaptive` 依赖，无实际使用 |
| F-29 | 人物/类型/标签浏览 | P2 | M3 | 🟨 搜索分组里能命中 Genre/Tag/Person，无独立浏览页 |
| F-30 | 首页流自定义 | P2 | M4 | ⬜ |
| F-31 | 智能筛选（保存/编辑） | P2 | M4 | ⬜ |
| F-32 | 二维码配置导入 | P2 | M4 | ⬜ 深链 `kavita://setup` 已注册，**扫码入口未做** |
| F-33 | 阅读历史/活动 | P2 | M4 | ⬜ |
| F-34 | 深色/浅色主题（主题色 #4AC694 / #2D7A56） | P1 | M1 | ✅ |
| F-35 | 无障碍（TalkBack/大字号） | P1 | M4 | 🟨 全量 `contentDescription` 已覆盖；**无专用测试** |

---

## 2. P0 功能详述（MVP）

> M0 已完成并验收的条目在标题标 ✅，实现位置见 `AGENTS.md` §11「M0 落地记录」。

### F-01 服务器添加/编辑与连通性检测
- 输入规范化（`ServerUrl.normalize`）、`GET /api/Health` 探测、`server-info-slim` 显示版本；可命名（"家里的服务器"）；可单独设置明文开关与启动自动登录。
- **验收**：粘贴 `host`、`host/api`、含空格、含 apiKey 的链接、OPDS 链接 5 种输入都能正确得到 baseUrl；自签证书场景给出可理解的提示，且不静默忽略。
- 失败提示覆盖：DNS/超时/404/502/明文。
- **实现状态（v1.2.0）**：🟨 主体已交付（含 OPDS 后缀剥离 + 一次性 `PendingApiKeyStore` 预填、`http://` 视为明文同意）。
  **缺口**：`ServerProfile.tlsFingerprint` 字段存在但**没有任何 TLS 校验实现**（未注册 `TrustManager`/`CertificatePinner`），
  即 AGENTS §4.1 的"信任本次证书指纹（TOFU）"**尚未落地** —— 自签证书现在只能靠系统信任库，失败即失败。

### F-02 服务器选择页与启动路由
- **默认不自动登录**：启动 → 服务器选择页；仅当设置里指定"启动自动登录的服务器"且会话有效时才直达首页。
- 选择页展示多台服务器的状态；支持切换、编辑、删除、设为启动自动登录（单选，可"不自动登录"）。
- **验收**
  1. 默认（未设置自动登录）每次启动都停在选择页。
  2. 设置某服务器为自动登录后，重启直达该服务器首页；该服务器会话失效时回到选择页而不是卡死。
  3. 两台服务器分别登录不同账号，切换后用户/权限/封面互不串（缓存与 token 按 `serverId` 隔离）。

### F-03 首页流
- 目标：`GET /api/Stream/dashboard?visibleOnly=true` 决定顺序，按 `streamType` 分别取数：`1 OnDeck`、`2 RecentlyUpdated`、`3 NewlyAdded`、`4 SmartFilter`。
- 每流最多 **10 条** + 「查看全部」（产品反馈：首页流只是"扫一眼"的入口，全量列表在「查看全部」里）。
  请求侧 `pageSize=10`（`HomeViewModel.STREAM_PAGE_SIZE`），映射侧再 `take(10)` 兜底，防止忽略分页参数的服务端渲染出无限长的一行。
- **验收**：无任何流数据时首页显示引导而非空白；单流失败不影响其他流。
- **实现状态（v1.2.0）**：🟨 **三条流是硬编码的**。`HomeViewModel` 直接并发调 `onDeck` / `recentlyUpdated` / `recentlyAdded`
  （各 `pageSize=STREAM_PAGE_SIZE`=10，由 `HomeStreamLimitTest` 守卫），**从不读 `Stream/dashboard`**；`SeriesRepository.dashboardStreams()` 已实现但**零调用点**。
  后果：服务端里隐藏/重排/自定义的流不会反映到首页，`4 SmartFilter` 流也不会出现（那是 F-30 的前提）。
  单流失败确实不影响其他流（三个 `async` 各自 `Outcome` 判定）✅。

### F-04 登录与按服务器会话
- 账号密码 + API Key 两种；按 JWT `exp` 判定会话是否有效（`JwtExpiry` + `Session.status()`），过期即视为需要重新登录。
- 登录后自动确保 `image-only` key 存在（图片必需）。
- 记住上次登录的**账号名**（不存密码），过期后重新登录只需输入密码，并自动聚焦密码框。
- **验收**：token 过期后服务器选择页显示「登录已过期」并可直接进登录页；登录页预填账号；杀进程重启仍保持登录（token 未过期）。
- **⚠ 已知缺口（v1.2.0 核对）**：`POST Account/refresh-token` 仅在 `Apis.kt` 声明，**没有任何调用点**，
  OkHttp 也没有注册 `Authenticator`。所以 token 一旦过期只能重新登录，不会自动刷新。原文档把
  "Token 刷新"标为 ✅ 属超前记功，已改为 🟨；补 `Authenticator`（单飞刷新）是 F-04 剩余工作。

### F-05 隐私模式
- 主界面右上角开关；开启后**所有封面/缩略图不发起请求**，渲染 `privacy_placeholder`（暗色 `#3A3A3A` / 亮色 `#E9EBEF`）；按服务器持久化。
- **验收**
  1. 开关后当前屏封面立即变占位（不等网络），再次进入列表不再产生封面请求（可用 OkHttp 日志/断言验证无 `image` 请求）。
  2. 重启保持状态；切服务器状态独立。
  3. 阅读器正文页不受影响。

### F-06 继续阅读（已按产品决定改造）
- ~~首页「继续阅读」大卡~~ **已移除**（产品反馈：首页只保留流 + 库文件夹）。续读路径改为：On Deck 流 → 系列详情 → `series_continue` 主按钮（`resumeChapterId` 来自 `Reader/continue-point`）。
- 页码来自 `GET /api/Reader/get-progress?chapterId=`；页码展示 = `pageNum`（1 基），完成态（`> pages`）显示"已读完，重读"（`series_reread`）。
- **验收**：从 On Deck 进入阅读器后再返回，详情页主按钮文案在 继续/重读/开始 三态间正确切换。

### F-07/F-08 书架与系列详情
- 库 → 系列网格（`POST /api/Series/v2`，Libraries 语句）；分页 40（`PAGE_SIZE`）；**只有网格，没有网格/列表切换**（原文档写的切换未实现，已删）。
- 系列详情：`GET /api/Series/{id}` + `GET /api/Series/volumes`；卷折叠状态在 ViewModel 内记住（最新一卷默认展开），**跨进程/返回重建会重置**（`expandedVolumeIds` 未进 `SavedStateHandle`）。
- **验收**：滚动流畅、滚动到底能加载完所有页（分页用 `Pagination` 头的 `totalItems` 精确判定末页）。

### F-09 漫画阅读器
- 三种渲染路径（分页 `HorizontalPager` / `VerticalPager` / Webtoon `LazyColumn`）；手势/进度/章节切换；错误页可重试。
- EPUB/PDF 只做识别与明确拒绝（`reader_unsupported_format`），不假装渲染。
- **验收清单**
  1. LTR/RTL 均正确翻页与页码显示。
  2. 双页模式宽页独占且不错位（`PagePairing` 纯逻辑 + 单测）。
  3. Webtoon 模式滚动页码准确，退出重进定位一致（`ReaderChrome.webtoonStartIndex` 钳制）。
  4. 旋转屏幕保持页码与阅读模式（`configChanges=orientation|screenSize`）。
  5. 单页章节（1 页）能标记完成。
  6. 弱网下页面失败可重试，不阻塞其他页。

### F-09b 双页 / Webtoon / 上下翻页 + 阅读设置抽屉
- 已在 M2 交付（原表误标 ⬜）。设置项：翻页模式、阅读方向、页面布局、图片适配、封面单独、亮度、显示页码、横屏显示、音量键翻页、恢复本系列。
- 无效选项自动隐藏：阅读方向/页面布局只在「左右翻页」出现，条漫固定隐藏图片适配（渲染层会忽略的选项不提供）。
- 设置按 **系列 → 全局默认 → App 默认** 三级持久化（`ReaderSettingsStore`），亮度仅本会话生效。

### F-10 进度同步
- 800ms 防抖 + 写后落 Room 队列 + 重试（协程，无 WorkManager）；完成判定 `pages + 1`。
- **实现状态（v1.2.0）**：✅ 主体已交付（`ProgressRepository.save/flush` + `pending_progress` 表，
  同章节覆盖写，主键 `serverId+chapterId`）。
- **补传触发点只有两处**：`ReaderViewModel.onAppResumed()` 与 `onCleared()`/`onAppPaused()`。
  **冷启动时没有全局补传**（原文档写"启动时补传"，实际未接），所以断网期间留下的队列要等下次进阅读器才会上传。
- **验收**：飞行模式阅读 10 页 → 恢复网络后进阅读器 30s 内服务端进度更新；不产生页码跳变；队列项不丢（进程重启后仍在 Room）。

### F-11 标记已读
- 系列/卷/章三级；**想读**是乐观更新 + 失败回滚；**标记已读**是"提交成功后再 `load()` 刷新"，失败弹 message（不做乐观更新）。
- **实现状态（v1.2.0）**：✅ 三级均已接（`Reader/mark-read`、`mark-volume-read`、`mark-chapter-read`）。
  取消已读**没有独立端点**：`markChapterUnread` 走 `Reader/progress` 写 `pageNum=0` 把进度清零。注意与 AGENTS §3 的"所有写操作必须乐观更新"存在偏差：标记已读是**串行 + 成功后再刷新**，
  代价是多一次请求、好处是不会出现"先当真后回滚"的闪烁。
- **验收**：标记整卷已读后，系列进度与 On Deck 状态一致（刷新后仍正确）。

---

## 3. P1 功能要点

### F-12 搜索
- 300ms 防抖；结果分组（系列 / 合集 / 阅读清单 / 人物 / 类型 / 标签）；最近搜索 ≤10 条；不搜章节内容（`includeChapterAndFiles=false`）。
- 隐私模式下结果图不加载（文字结果照常）。
- **实现状态（v1.2.0）**：✅ 已交付。1 字符门槛（服务端单字即可命中）、竞态用请求序号防护。
- **两点注意**：① 历史是**会话内**的（`_history` 在 ViewModel 里，进程结束即清空），原文档"本地保存"尚未落 DataStore；
  ② 服务端只搜 `includeInSearch=true` 的资料库且不做简繁折叠，见 `docs/API.md` §6。

### F-13 筛选与排序
- **只保留三项**（产品定稿）：排序（名称 `sortField=1` / 加入时间 `2` / 阅读时间 `7`）、
  名称搜索（`SeriesName(1)` + `Matches(7)`）、是否收藏（`WantToRead(26)`==`"true"`，即「想读」）。
- 排序带方向（升序/降序）。切换排序字段时方向自动取该字段的自然默认（名称→升序，日期类→降序），
  避免"按加入时间 + 升序"把最老的排在最前；用户可再手动点反向。
- 比较符必须与字段匹配：`SeriesName` 只能用 `Matches/BeginsWith/EndsWith/Equal/NotEqual`，
  **不能用 `Contains(5)`**（仅列表型字段可用，错配会抛异常而非返回空）。详见 `docs/API.md` §2。
- 组合方式固定 `And(1)`：库范围是独立注入的第一条 statement，用户条件不能把它并集掉。
- 已激活条件在列表顶部显示可点击移除的 chips；筛选按钮图标在条件生效时高亮。
- 后续若要放开更多字段（标签/类型/评分…），必须逐个按 `ComparisonProfile` 核对允许的比较符。
- **实现状态（v1.2.0）**：✅ 已交付（`FilterSheet` + `ActiveFilterChips` + `SeriesSort.defaultAscending`）。

### F-14 合集
- 列表（`GET Collection`）+ 详情（`Series/series-by-collection` 的系列网格，复用 `ShelfScreen`）；推广合集带 `Promoted` 徽标。
- **实现状态（v1.2.0）**：🟨 列表与详情已通；**「全部阅读」（按集合顺序取续点）未做** —— 详情页只能逐个点系列。
- 已知限制：合集详情只映射为系列网格，合集内「章节级」顺序不保留（那是阅读清单的职责）。

### F-15 阅读清单
- 目标：顺序项列表（可含跨系列章节）；阅读器内下一章走清单顺序；只读角色禁用排序。
- **实现状态（v1.2.0）**：🟨 **只做了列表**（`ReadingList/lists` + `ShelfScreen` 渲染），而且列表行**当前点击无反应**
  （`ShelfScreen` 只对 `ShelfKind.Collections` 调 `onOpenShelf`）。缺：清单详情（`ReadingList/items`）、
  按清单顺序的 `ReadingList/next-chapter` 续读、只读角色禁用排序。
- **验收**：清单阅读时"下一章"不跳到系列下一章 —— **该验收尚未被实现覆盖**，属 M3 待办。

### F-16 想读
- 加入/移出 + 列表页 + 详情页状态同步；`wantToReadSync` 偏好存在时由服务端处理同步。
- 入口是系列详情主按钮右侧的**爱心图标**（`ic_favorite` / `ic_favorite_filled`），空心=未收藏、实心品牌绿=已收藏；不放进右上 `⋯` 菜单。
- **实现状态（v1.2.0）**：✅ 已交付。`ShelfRepository.toggleWantToRead` 成功后清 `SeriesListCache`，
  否则「仅收藏」筛选会命中旧页（这是 M2 修复过的真实 bug）。
- 实现细节：`is-in-want-to-read` 返回的是纯文本 `true`/`false`，不是 JSON，故按 body 文本比较。

### F-17 书签
- 页面书签（漫画/PDF）+ 文本书签（EPUB，`XPath`）。**实现状态（v1.2.0）**：🟨 页面书签已完成；文本书签随 F-21 一并待做。
- **入口**：「我的 → 我的书签」（卡片网格 + 书签页缩略图）；阅读器顶部栏书签按钮添加或取消（双击手势已让位给放大，见 `docs/FLOWS.md`）。
- 书签页缩略图 `GET /api/Image/bookmark`；点击直达该书签页（`Routes.reader(chapterId, pageIndex)` 带 `page` 参数）。
- **页码是 0 基索引**：`BookmarkDto.page`、`Reader/image?page=`、`Image/bookmark?pageNum=` 三者同源，只有展示才 +1。
- 只读角色隐藏阅读器的加书签按钮（`Session.canBookmark` → `ReaderViewModel.canBookmark`，列表仍可浏览）；隐私模式下缩略图为占位图且**不请求**。
- 列表在 `onResumed` 重新拉取：书签是在阅读器里改的，返回时不能展示过期数据。

### F-18 我的与设置
- 目标：账户信息、改密、Auth Keys 查看（掩码 + 复制）、服务器管理入口、清理缓存（按服务器）、退出。
- **实现状态（v1.2.0）**：🟨 已交付 = 账户信息（用户名 + 角色徽标 + 当前服务器）、切换服务器（`ServerPicker` 带返回键）、
  我的书签、想读、设置页（外观 / 阅读默认 / 关于 + 退出登录）。
  **未做**：改密、Auth Keys 查看与复制、按服务器清缓存入口（`KavitaDatabase.clearServer` 与 `ReaderSettingsStore.clearServer` 已存在，
  但没有 UI 调用点），因此"清理缓存"目前没有入口。
- **验收**：退出/删除某服务器后，本地只清该 `serverId` 的会话、阅读设置与缓存，不影响其它服务器（`ServerPickerViewModel` 已按此实现）。

### F-19 四语言（中/英/法/日）
- `values/`（中文基准）、`values-en/`、`values-fr/`、`values-ja/`；默认跟随系统，可在设置强制指定。
- **验收**：四语言切换后所有页面无缺串（含空态/错误态/Snackbar）；日期与数字按 Locale 格式化；缺失翻译 CI 失败。

### F-20 素材管线（SVG→PNG）
- 所有图片/图标走 `docs/ASSETS.md` 清单：占位素材（脚本自动生成 SVG→PNG）先用，正式素材到位后按清单批量替换。
- **实现状态（v1.2.0）**：🟨 管线已完成（`generateAssets` / `checkAssetsRegistered` / 43 项清单 → 183 个 PNG），
  但**素材本身仍是占位**（`logo_full`、`empty_*`、`error_generic`、`cover_fallback`、`privacy_placeholder` 与 launcher 皆为占位）。
- **验收**：清单中每一项状态为"正式"；工程内无游离于清单之外的图片资源（后者已由 `checkAssetsRegistered` 强制）。

### F-21 EPUB 阅读器
- WebView + CSS 注入；主题/字号/行距/边距；目录；进度锚点 `bookScrollId`。
- **验收**：换字号后当前阅读位置不跳；返回再进定位一致；代码块/图片不溢出。

### F-22 PDF 阅读器
- 拉取 PDF 到 app cache（渲染用途，非下载功能，LRU 50MB）→ `PdfRenderer`；页面位图 LRU；缩放；双页/单页。
- **验收**：200 页 PDF 打开 < 2s 显示首页；连续翻页不 OOM（内存 < 300MB）。

### F-23 阅读配置
- 列表/编辑/绑定系列或库/设备绑定；优先级链正确。
- **实现状态（v1.2.0）**：⬜ 服务端 `reading-profile/*` 未接入。当前只有**纯本地**的按系列持久化
  （`ReaderSettingsStore`，系列 → 全局默认 → App 默认），不写 implicit、不读服务端绑定、不做设备区分。
  这满足了"退出重进设置不丢"，但**不等于** F-23 完成。
- **验收**：给系列绑定"RTL 双页"，进入该系列生效；换绑后立即生效；只读账号不产生 implicit profile。

### F-24 阅读统计
- `GET /api/Stats/user-stats` 摘要 + 阅读热力图（`reading-activity`）+ 阅读速度（`reading-pace`）+ 类型分布（`genre-breakdown`）。
- 图表用 Compose Canvas 自绘（不引第三方图表库）。

### F-28 响应式
- 断点 600/840；平板 `NavigationRail` + 双栏；横屏阅读器沉浸。
- **实现状态（v1.2.0）**：⬜ 仅 `material3-adaptive` 的依赖已在，**代码里没有任何使用**（无 `NavigationRail`/双栏）。
  横屏阅读器沉浸已由 F-09b 的「横屏显示」覆盖。

### F-34 主题色
- 主色 `#4AC694`、深色主题色 `#2D7A56`；浅色用主色、深色用深主题色；动态取色关闭。
- **验收**：按钮/进度环/激活态在两套主题下对比度均过 WCAG AA。

---

## 4. P2 功能要点

- **F-25 评分/评论**：`POST /api/Rating/series`（0-10 分，步进 0.5）、`POST /api/Review/series`；展示来源 `ScrobbleProvider` 徽标；只读隐藏。
- **F-26 批注**：只读查看 `GET /api/Annotation/all?chapterId=`、`GET /api/Annotation/all-for-series`；点赞 `POST /api/Annotation/like`；创建高亮仅在 EPUB 且服务端支持时开放。
- **F-27 相关作品**：`GET /api/Series/all-related`，`RelationKind` 分组（Prequel/Sequel/SpinOff/Adaptation…）。
- **F-30 首页自定义**：`GET /api/Stream/dashboard?visibleOnly=false` + `POST /api/Stream/update-dashboard-stream` / `update-dashboard-position`（拖拽排序）。仅对 `isProvided` 流允许显隐。
- **F-31 智能筛选**：`POST /api/Filter/update/series`、`GET /api/Filter`、`POST /api/Filter/encode/series`、`POST /api/Filter/decode`；创建后可加到首页/侧边。
- **F-32 二维码配置导入**：扫码解析 `kavita://setup?url=…&apiKey=…` → 直接进 F-01 的 ServerSetup（预填）。
- **F-29 浏览**：`GET /api/Metadata/genres|tags`、`POST /api/Person/all`（分页）、`GET /api/Person?name=` 人物详情 + `GET /api/Person/series-known-for?personId=` 作品列表。
- **F-33 阅读历史**：`GET /api/Stats/reading-history`（分页）；按系列查看 `reading-history/series/{id}`。

---

## 5. 明确不做（Out of Scope）

| 功能 | 原因 |
| --- | --- |
| **章节/系列下载与离线阅读** | 明确不做（`/api/Download/*` 不接）；阅读依赖网络，进度队列仅补传进度 |
| 建库/扫描/元数据编辑 | 管理端职责，风险高，接口面向 Admin |
| 用户管理/邀请 | 同上 |
| Kavita+ 订阅/激活 | 支付与许可证属服务端/官网 |
| OPDS 服务端 | 我们只做客户端 |
| 上传书籍/封面 | 移动端体验差，且需 Admin |
| 服务端主题编辑 | 与阅读无关 |
| 邮件/设备推送配置 | 管理端 |
| 自动更新安装包 | 应用商店分发，且避免供应链风险 |
| 通知中心/通知点 | 移动端不做推送；实时性靠下拉刷新 |

---

## 6. 验收与质量指标（汇总）

| 项 | 门禁 |
| --- | --- |
| 每个 P0 功能 | 至少 1 个 Compose UI 测试 + 1 个 ViewModel 单测 |
| 网络层 | MockWebServer 覆盖：分页头、401 刷新、纯文本错误、超时、多服务器 baseUrl 切换 |
| 隐私模式 | 断言开启后无封面 HTTP 请求；两态截图回归 |
| i18n | 四语言（中/英/法/日）齐全性检查 + 主要页面四语言截图抽查 |
| 素材 | `docs/ASSETS.md` 无"占位"残留项（M3 后为门禁） |
| 阅读器 | 真实样本（Kavita-TestData）截图回归：LTR/RTL/双页/Webtoon/EPUB/PDF |
| 性能 | 冷启动 < 2s（默认走服务器选择页 < 800ms）；首页滚动掉帧 ≤ 1%；阅读器内存 < 400MB |
| 稳定性 | 关键路径无 `!!`、无未捕获异常；Crash-free ≥ 99.8% |
| 安全 | 无明文 token；日志脱敏；不请求 Admin 端点与 Download 端点（CI 用静态规则扫描 `api/Library/create`、`api/Download/` 等字符串） |

> **门禁现状（v1.2.0 诚实版）**：以上是*目标*门禁，不是当前事实。已落地的是「i18n 齐全性」（四语言各 253 条，
> 靠 `ThemeContractTest` 等守卫）、「隐私模式」（`PrivacyModeStateTest` + `PrivacyGuardInterceptorTest`）、
> 「安全」（无 Admin/Download 端点，review 可验）。**未落地**：Compose UI 测试（`connectedAndroidTest` 还没有用例）、
> Roborazzi 截图回归、性能与 Crash-free 指标采集。这些属 M4。

---

## 7. v1.2.0 状态核对记录

核对原则：不看文档自述，只看代码里**端点是否被调用、入口是否可达、状态是否真的流转**。

### 7.1 由 🟨/⬜ 上修为 ✅（文档过期，实际已完成）

| 功能 | 证据 |
| --- | --- |
| F-09 单页阅读器 | 单页/分区点击/页码跳转/章节切换/双击放大均已交付；`ReaderZoomTest` 等 24 个守卫 |
| F-09b 双页/Webtoon/上下翻页 + 设置抽屉 | `ReaderSettingsSheet` 全量设置项；`ReaderMode`/`PageLayout`/`ReadingDirection`/`ScalingOption`；`ReaderModeSettingsTest`、`ReaderSettingsRulesTest`、`PagePairingTest` 守卫 |
| F-12 搜索 | `SearchRepository` + `SearchScreen`；`Search/search`、`Metadata/genres|tags`、`Person/search`；`SearchRepositoryTest` |
| F-13 筛选与排序 | `FilterSheet` + `ActiveFilterChips` + `SeriesSort.defaultAscending`；`SeriesFilterTest` + `WireSerializationTest` 断言线上负载 |
| F-14 合集（列表+详情部分） | `ShelfRepository.collections/seriesInCollection`；`ShelfScreen` 三 kind；`Promoted` 徽标 |
| F-16 想读 | 详情页爱心按钮；`ShelfRepository.add/removeToWantToRead` + 缓存失效 |
| F-17 页面书签 | `Reader/bookmark|unbookmark|all-bookmarks`；`BookmarksScreen`；`canBookmark` 权限门 |

### 7.2 由 ✅ 降级为 🟨（做了一半，原文档超前记功）

| 功能 | 缺口 |
| --- | --- |
| F-01 服务器 | 主流程可用；**TOFU 证书指纹无实现**（只有 `tlsFingerprint` 字段） |
| F-03 首页流 | 三条流硬编码，未读 `Stream/dashboard`；SmartFilter 流不会出现 |
| F-04 Token 刷新 | `Account/refresh-token` **零调用点**，无 OkHttp `Authenticator`；过期只能重新登录 |
| F-14「全部阅读」 | 合集详情无"按顺序续读"按钮 |
| F-15 阅读清单 | 只有列表；**行点击无反应**（`onOpenShelf` 仅对 Collections 生效）；无 `ReadingList/items`、无清单顺序 next-chapter |
| F-17 文本书签 | EPUB `XPath` 书签未做（依赖 F-21） |
| F-18 我的与设置 | 无改密、无 Auth Keys 查看/复制、无按服务器清缓存入口 |
| F-20 素材 | 管线完成，素材仍为占位 |
| F-35 无障碍 | `contentDescription` 已覆盖，但无 TalkBack 大字号/双页朗读测试 |

### 7.3 发现的真实缺陷（建议列入 M3 首批修复）

1. **阅读清单点不动**：`ShelfScreen` 里 `if (viewModel.shelfKind == ShelfKind.Collections) onOpenShelf(shelf.id)`，
   导致「阅读清单」列表项点击后什么都不发生 —— 用户视角就是"功能坏了"。
2. **首页流绕过了 `/Stream/dashboard`**：三条流写死在 `HomeViewModel`，服务端的流顺序/显隐/自定义（含 SmartFilter 流）全部无效。
   这同时是 F-30（首页自定义）的前置缺陷。
3. **`SERVER_MANAGE` 是死路由**：`Routes.SERVER_MANAGE` 已定义但 NavHost 无对应 `composable`，
   设置页也没有入口（服务器管理只能从 Me 页进）。
4. **只读角色未真正收敛写操作**：`Read Only` 只隐藏了阅读器书签按钮；标记已读/想读入口仍对只读账号可见，
   点下去会拿到 403（`docs/API.md` §4.6 item 6 要求"隐藏所有写操作入口"）。
5. **自签证书 TOFU 未实现**：`tlsFingerprint` 只有字段，没有校验代码路径（AGENTS §4.1）。
6. **`Reader/time-left*` 已声明但未使用**：系列详情没有"剩余阅读时间"展示。
7. **冷启动不补传进度**：`ProgressRepository.flush()` 只在阅读器 `onAppResumed`/`onCleared` 触发；
   断网留下的队列要等下次进阅读器才上传，与 AGENTS §7「启动时补传」不符。
8. **无全局 `SyncCoordinator`**：AGENTS §7 指定的协调器不存在，触发点散落在 `ReaderViewModel` 里。

### 7.4 真·未开始（M3/M4）

F-21 EPUB、F-22 PDF、F-23 服务端阅读配置、F-24 统计、F-25 评分评论、F-26 批注、F-27 相关作品、
F-28 平板双栏、F-30 首页自定义、F-31 智能筛选、F-32 扫码导入、F-33 阅读历史。
