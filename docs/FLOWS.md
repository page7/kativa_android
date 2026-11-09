# 使用流程（User Flows）

> 每个流程给出：触发 → 步骤 → 状态/异常 → 落点。接口名以 `docs/API.md` 为准。
> 所有文案需四语言（中/英/法/日）；所有封面受隐私模式开关控制（见 F3b）。

---

## F1 启动路由（默认不自动登录）

```
冷启动
 └─ Splash：读取启动偏好
     ├─ 设置了「启动自动登录的服务器」且该服务器会话有效
     │    └─ 跳过选择页 → 预取首页数据 → 首页（可被「我的 → 切换服务器」打断）
     ├─ 已保存 ≥1 个服务器，但未设置自动登录（默认行为）
     │    └─ → ServerSelect 服务器选择页
     └─ 一个服务器都没有 → 欢迎页（只有 logo，无插画无文案）
          └─ 1.2s 后（或首次点击屏幕）→ ServerSetup 添加服务器
```

**ServerSelect 服务器选择页**

1. 列表展示已保存服务器：名称、地址、最近使用时间、登录状态徽标（已登录/需登录/无法连接）。
2. 点击某服务器：
   - 会话有效 → 切换上下文（Retrofit baseUrl、图片缓存、会话）→ 该服务器首页。
   - 会话失效/从未登录 → 进入该服务器的登录页（F2）。
   - 连不上 → 卡片标红 + 「重试 / 编辑地址 / 删除」。
3. 顶部/底部「+ 添加服务器」→ ServerSetup；添加成功后可勾选「启动时自动登录此服务器」。
4. 长按卡片：编辑地址、设为启动自动登录、删除（二次确认，提示"仅删除本机记录"）。

**ServerSetup（添加/编辑服务器）**

1. 输入框粘贴 `https://kavita.example.com`（自动补 `https://`、去尾斜杠、识别子路径）。
2. 「测试连接」→ `GET /api/Health`（匿名）。
   - 成功 → 显示 `GET /api/Server/server-info-slim` 的版本号（若 401 则跳过）→ 下一步。
   - 证书错误 → 弹「证书不受信任」面板：展示 CN/指纹/有效期 → 「信任此证书并继续（按主机保存）」。
   - `http://` → 提示「未加密连接」二次确认（仅该服务器允许明文）。
3. 保存为一条 `ServerProfile`（可命名，如"家里的服务器"），进入登录。

**异常矩阵**

| 现象 | 提示 | 操作 |
| --- | --- | --- |
| DNS 失败 | 「找不到服务器，请检查地址」 | 编辑地址 / 重试 |
| 超时(>10s) | 「连接超时」 | 重试 / 检查网络 |
| 404 / 非 Kavita | 「该地址不是 Kavita 服务器」 | 返回 |
| 502/503（反代） | 「服务器暂时不可用」 | 重试 |
| 明文被拒 | 「系统禁止明文流量」 | 引导开启 |
| 切换服务器时有未同步进度 | 「进度将在联网后同步」 | 继续（队列按 serverId 保留） |

---

## F2 登录

```
Login
 ├─ 账号密码 → POST /api/Account/login
 │    ├─ 200 → 保存 token/refreshToken/user → 拉偏好 → 首页
 │    ├─ 401「Your credentials are not correct」→ 表单错误
 │    ├─ 401「disabled-account」→ 「账号已被禁用，请联系管理员」
 │    └─ 401「password-authentication-disabled」→ 引导 OIDC/API Key 登录
 ├─ API Key → 同端点 {apiKey} → 校验 /api/Plugin/authkey-expires
 │    └─ 过期 → 「该密钥已过期」+ 生成指引（Web 用户设置 → Auth Keys）
 └─ 匿名浏览（若服务端允许）→ 只读首页，任何写操作提示登录
```

**登录后必做（并行）**：
1. `GET /api/Account` 取完整用户（roles/preferences/authKeys）。
2. 确保存在 `image-only` Auth Key（否则 `POST /api/Account/create-auth-key`）。
3. `GET /api/Users/get-preferences` 同步阅读偏好。
4. `GET /api/Library/libraries` 预热库列表。
5. `GET /api/Server/server-info-slim` 记录版本，用于能力开关。
6. 会话按 `serverId` 落库；可选：勾选"启动时自动登录此服务器"（写入启动偏好，写前确认"仅记住本机"）。

**Token 过期流程（P0：无自动刷新，只保证可恢复）**

```
启动 / 打开选择页 / 请求收到 401（且请求带 Authorization）
 └─ 会话按 JWT exp 判定为 Expired
     ├─ 选择页显示「登录已过期」（不再显示"已登录"）
     ├─ 点该服务器 → 直接进登录页（不再进首页再报错）
     └─ 登录页已预填上次的账号，只需输入密码 → 成功后写回新 token
```

- 密码/API Key **从不**落盘；只记住 `username`（`kativa_accounts` DataStore），登出/过期都不清。
- 401 拦截在 `SessionExpiryInterceptor`：跳过 `Account/login`、`Account/refresh-token`、`Plugin/authenticate`，避免把"密码错"当成"会话过期"。
- 自动刷新（`Authenticator` + 单飞 + 提前 5 分钟）属于 **P1**，尚未实现；当前 token 过期即需重新登录（只需密码）。

**登出 / 删除服务器**

- 登出当前服务器：清该 `serverId` 的 token/imageKey/图片缓存，回服务器选择页。
- 删除服务器：额外清该服务器的 Room 投影与待同步队列；其它服务器不受影响。

---

## F3 首页 → 继续阅读（核心路径，目标 ≤ 2 次点击）

```
首页
 └─ 顶部「继续阅读」大卡（来自 POST /api/Series/on-deck 第一条 + Reader 进度）
     └─ 点击
         ├─ 有进度 → GET /api/Reader/chapter-info（续点章节）
         │    → GET /api/Reader/get-progress → 定位页码 → 阅读器
         └─ 无进度 → GET /api/Reader/continue-point?seriesId= → 第一章 → 阅读器
```

- 卡片信息：系列名 · 卷/章标题 · 「第 N 页 / 共 M 页」· 进度环。
- 长按卡片：`标记本卷已读` / `从进度中移除`（`POST /api/Series/remove-from-on-deck`）。
- 无网络 → 卡片置灰显示「需联网」，点击弹「重试」。
- 多本横向可滑（On Deck 前 10 条），"查看全部"进入 On Deck 列表页。
- 顶栏右侧：搜索图标、**隐私模式开关**（见 F3b）。

---

## F3b 隐私模式（封面屏蔽）

```
顶栏眼睛/遮罩图标
 ├─ 关 → 开
 │    ├─ PrivacyMask = true（DataStore 持久化，按 serverId）
 │    ├─ 已在屏的封面立即置灰（组合重组，不等待网络）
 │    ├─ Coil 请求短路：直接返回 privacy_placeholder，不发起 HTTP
 │    └─ Snackbar「已隐藏封面」（2s 自动消失）
 └─ 开 → 关：恢复加载当前屏 + 后续列表正常请求
```

- 覆盖范围：所有封面/缩略图（系列、卷、库、合集、阅读清单、人物、搜索结果图、书签缩略图）。
- **不影响**：阅读器正文页、PDF/EPUB 内容（隐私模式只管"封面类"图片）。
- 重启 App 保持上次状态；切换服务器状态各自独立。
- 与图片缓存的关系：隐私模式下不请求 = 不写入缓存；已缓存的封面在关闭隐私模式时直接命中缓存（**不联网也能显示已缓存封面**——这是隐私模式之外的正常缓存行为）。

### 实现约束（曾因此在 2-3 级页面失效，勿再拆开）

隐私状态的唯一事实来源是 `PrivacyModeState`（`core/image`），由它自己 `combine`
`ActiveServerStore.active` 与 `SettingsRepository.privacyModeByServer`，**不依赖任何 ViewModel**。
曾经写成在 ViewModel 里 `active.collect { isPrivacyMode(id).collect { … } }`：内层永不结束，
外层收不到第二次发射 → 启动时 serverId 为 null，值恒为 `false`。

渲染侧只有一个开关：`CoverImage` 的 `privacyMode` 默认取 `LocalPrivacyMode.current`
（由 `KativaTheme` 从上述状态注入）。**屏幕不得再把 `privacyMode` 当参数往下传**——首页当初
额外把 `coverUrl` 置 null，所以"看起来"生效，而系列详情/搜索/流列表走默认值就漏了。

第三道闸：`PrivacyGuardInterceptor` 注册在 Coil 的 `ImageLoader` 上，按路径
（`/Image/*-cover`、`/Reader/thumbnail`）拦掉漏网请求；`/Reader/image` 永不拦截。

---

## F4 书架浏览

```
书架 Tab
 ├─ 库列表（GET /api/Library/libraries）
 │   └─ 库卡片（封面、名称、类型、最近扫描时间，例：Manga / Comic / Book）
 │       └─ 库详情
 │           ├─ 系列网格（POST /api/Series/v2，body: {statements:[{field:Libraries,comparison:Equal,value:"id"}], ...}）
 │           ├─ 排序（SortOptions）与筛选（FilterSheet）
 │           ├─ 视图切换：网格 / 列表（记住选择）
 │           ├─ 跳字条（A-Z，按 sortName 首字母客户端分桶）
 │           └─ 系列详情
 └─ 空态：「这个库还没有内容」+「去 Web 端扫描」(复制提示)
```

**系列详情**

```
系列详情 (GET /api/Series/{id} + GET /api/Series/volumes?seriesId=)
 ├─ 头图：封面模糊 + 标题 + 元数据（类型/年份/状态/页数/评分）
 ├─ 主行动按钮（按状态变化）
 │   ├─ 未读 → 「开始阅读」
 │   ├─ 有进度 → 「继续第 N 页」
 │   └─ 全读完 → 「重新阅读」（确认后进度归零，POST /api/Reader/progress pageNum=0）
 ├─ Tab：卷章 / 简介 / 相关 / 评论（M3）
 ├─ 卷列表（可折叠，显示每卷进度）
 │   └─ 章列表（每章：标题、页数、已读勾、书签角标）
 │       ├─ 点击 → 阅读器
 │       └─ 长按/更多 → 标记已读 / 标记未读 / 加入书签列表 / 加入阅读清单
 ├─ 更多菜单：标记全部已读（POST /api/Reader/mark-read）、加入想读、加入合集
 └─ 下拉刷新：重新拉取系列与卷章数据（管理员额外显示「扫描系列」→ POST /api/Series/scan）
```

---

## F5 阅读（漫画）

```
进入阅读器
 ├─ 参数：chapterId、seriesId、volumeId、libraryId、来源（series/readingList/collection）、incognito
 ├─ 步骤 1：GET /api/reading-profile/{libraryId}/{seriesId}?deviceId=… → 应用阅读配置（失败→本地默认）
 ├─ 步骤 2：GET /api/Reader/chapter-info?chapterId=&includeDimensions=true
 │    → pages、doublePairs、页面尺寸、格式
 ├─ 步骤 3：GET /api/Reader/get-progress?chapterId= → pageNum（0 基换算）
 │    └─ pageNum == pages → 钳制 pageNum = pages-1；pageNum == pages+1 → 表示已完成，从 1 开始
 ├─ 步骤 4：GET /api/Reader/chapter-bookmarks（可选，加载书签标记）
 ├─ 步骤 5：渲染首屏（±2 页预取）
 └─ 阅读中
     ├─ 翻页 → 800ms 防抖 POST /api/Reader/progress
     ├─ 到达最后一页 → POST progress(maxPages+1)
     │    ├─ nextChapterId > 0 → 底部「下一章」过渡条（不自动跳，除非设置开启）
     │    └─ 无下一章 → 顶部 Snackbar「已读完本系列」+ 返回详情
     └─ 退出（返回/手势/系统返回）
         └─ flush 进度 → 若失败入队 → 阅读器关闭 → 回到来源页
```

**手势与操作映射（单页模式）**

| 手势 | 行为 |
| --- | --- |
| 单击中部 40% 区域 | 显示/隐藏菜单（工具栏 + 进度条） |
| 单击左/右 30% 区域 | 上一页/下一页（遵循阅读方向） |
| 左右滑 | 翻页（可在设置关闭，改为仅点击） |
| 双击 | 添加/移除当前页书签 |
| 长按 | 唤出「跳转到页」输入框（M2） |
| 双指捏合 | 缩放（原图模式） |
| 音量键 | 翻页（默认开） |
| 下拉（Webtoon） | 无操作；下拉（分页） | 刷新当前页 |
| 旋转/折叠屏 | 保持页码，重算双页配对 |

**异常**

| 现象 | 处理 |
| --- | --- |
| 图片 404/500 | 该页显示重试占位，3 次后提示「该页加载失败，跳过/重试」 |
| `chapter-info` 失败 | 全屏错误 + 重试 + 「用外部阅读器打开」(M3) |
| 进度保存失败 | 静默入队，Snackbar「进度稍后同步」 |
| 服务端重新扫描导致 chapterId 失效 | 404 → 提示「该章节已变更，请返回刷新」 |
| 阅读中收到服务端 401 | 刷新 token 后重放；失败则保存进度并跳登录 |

---

## F6 EPUB / PDF 阅读

```
EPUB
 ├─ GET /api/Book/{chapterId}/book-info → 元信息
 ├─ GET /api/Book/{chapterId}/chapters → 目录树
 ├─ GET /api/Book/{chapterId}/book-page?page=n → HTML 片段
 ├─ GET /api/Book/{chapterId}/book-resources?file=… → css/图片/字体
 └─ 渲染：WebView + 注入主题/字号/行距/边距/margin
     ├─ 进度：progress.pageNum + bookScrollId（滚动锚点）
     ├─ 点击左右 → 翻页；目录抽屉；书签（XPath）
     └─ 批注/高亮（M3，需 BookReaderHighlightSlots）

PDF
 ├─ GET /api/Reader/pdf?chapterId= → 拉取 PDF 到 app 缓存（带 apiKey；仅为渲染需要，**不属于下载功能**，LRU 50MB）
 ├─ Android PdfRenderer 逐页渲染 Bitmap（复用页面缓存池）
 └─ 缩放/滚动/双页；进度与漫画一致（pageNum 1 基）
```

> 格式判断以 `ChapterInfoDto.seriesFormat` 为准，不信任文件扩展名。

---

## F7 搜索

```
发现 → 搜索框（自动聚焦）
 ├─ 输入 ≥ 1 字符，300ms 防抖 → GET /api/Search/search?queryString=&includeChapterAndFiles=false
 ├─ 结果分组：系列 / 合集 / 阅读清单 / 人物 / 类型 / 标签 / 章节(M2 打开)
 ├─ 最近搜索（本地，最多 10 条），可单条删除/清空
 └─ 空结果：「没有找到「xxx」」+ 「在库内筛选」
```

- 搜索不区分大小写；中文按原样传（服务端处理）。
- 点击系列 → 系列详情；人物 → 人物详情（M3）；类型/标签 → 筛选结果页。

---

## F8 筛选与排序

```
任意系列列表 → 筛选按钮
 ├─ FilterSheet（底部抽屉，只有三项）
 │  ├─ 排序：名称(1) / 加入时间(2) / 阅读时间(7) + 升序/降序
 │  │   切换字段时方向自动取该字段自然默认（名称→升序，日期类→降序），可再手动反转
 │  ├─ 名称搜索：SeriesName(1) + Matches(7)（空串则不发该条件）
 │  └─ 是否收藏：全部 / 仅收藏 → WantToRead(26)= "true"
 ├─ 「重置」→ 回到默认（名称升序、无条件）
 └─ 「应用」→ POST /api/Series/v2（body 固定 combination=1）
```

- 条件与排序同步写进 `SeriesFilter`（ViewModel 内），进程重建后可按需恢复；**不持久化到服务端**。
- 已激活筛选时图标高亮；列表顶部显示可移除的 chips（名称 / 仅收藏）。
- 比较符不能乱选：`SeriesName` 只认 `Matches/BeginsWith/EndsWith/Equal/NotEqual`。用 `Contains(5)`
  会被服务端 `ComparisonProfile` 直接拒绝，表现为"筛选没反应"。详见 `docs/API.md` §2。
- 想读是"收藏"的唯一后端能力；在系列详情加入/移出想读后会**清空系列列表缓存**，否则筛选结果会继续用旧页。

---

## F9 合集 / 阅读清单 / 想读

```
合集（GET /api/Collection）
 └─ 合集详情（GET /api/Series/series-by-collection?collectionId=）→ 系列网格
     └─ 「全部阅读」→ 第一条未读系列的续点

阅读清单（POST /api/ReadingList/lists）
 └─ 清单详情（GET /api/ReadingList/items?readingListId=）
     ├─ 顺序列表（含来自不同系列的章节），显示每项进度
     ├─ 「继续」→ 第一条未读完项 → 阅读器（readingListMode=true）
     │    └─ 阅读器内"下一章"走 GET /api/ReadingList/next-chapter?currentChapterId=&readingListId=
     └─ 只读角色：隐藏拖拽排序/删除/新增

想读（POST /api/want-to-read/v2）
 ├─ 系列详情菜单「加入想读」→ POST /api/want-to-read/add-series {seriesIds:[id]}
 ├─ 想读页 → 移出（remove-series）/ 开始阅读
 └─ 状态：GET /api/want-to-read?seriesId= 决定按钮文案
```

---

## F10 服务器管理（多服务器）

```
我的 → 服务器
 ├─ 列表：名称、地址、登录用户、版本、最近使用时间
 ├─ 点击条目 → 切换到该服务器（会话有效直接进，否则进登录页）
 ├─ 「+ 添加」→ ServerSetup（F1）
 ├─ 编辑：名称、地址、明文开关、证书信任
 ├─ 「设为启动自动登录」（单选，可"不自动登录"= 默认）
 └─ 删除 → 二次确认「仅删除本机保存的服务器与会话，不影响服务器上的数据」
     └─ 删除当前激活服务器 → 回服务器选择页
```

- 每个条目显示连接状态（上次探测结果），支持单独「测试连接」。
- 启动自动登录的设置项同时出现在「设置 → 启动」，与本页单选状态保持一致。
- 切换服务器不清理其它服务器的缓存与队列（按 `serverId` 隔离，见 AGENTS §4.1/§7）。

---

## F11 书签

```
阅读器双击 / 菜单「书签本页」
 └─ POST /api/Reader/bookmark {page, volumeId, seriesId, chapterId}
     └─ 成功：页面顶部书签图标填充 + 角标

书签入口
 ├─ 我的 → 书签（POST /api/Reader/all-bookmarks，仅支持系列名筛选，返回全量；客户端本地分页/分组）
 │    └─ 网格：页面缩略图（GET /api/Image/bookmark?chapterId=&pageNum=）
 │        └─ 点击 → 直接进入该章该页（路由 reader/{chapterId}?page={0 基索引}）
 └─ 系列详情 → 书签 Tab（GET /api/Reader/series-bookmarks）
```

- **页码语义**：`BookmarkDto.page` 与 `Image/bookmark?pageNum=` 都是 **0 基**图片索引，与
  `Reader/image?page=` 一致；只有展示（"第 N 页"）才 `+1`。加书签时提交阅读器的 `pageIndex` 原值，
  **不要**再 `+1`（详见 `docs/API.md` §3「书签」）。
- 书签列表在 `ON_RESUME` 重新拉取：书签的增删都发生在阅读器里，返回时缓存必然过期。
- 隐私模式下缩略图不发请求（`Image/bookmark` 在 `PrivacyGuardInterceptor.COVER_PATHS` 内）。
- 只读角色：列表可看，阅读器的"加书签"按钮与双击手势隐藏（服务端 `Bookmark` 策略会 403）。

---

## F12 阅读配置（Reading Profile）

```
设置 → 阅读 → 阅读配置
 ├─ 列表（GET /api/reading-profile/all）
 ├─ 新建/编辑（POST /api/reading-profile/create | POST /api/reading-profile）
 │    ├─ 漫画：方向/布局/分页模式/缩放/切分/自动 Webtoon/背景色/宽度覆盖/屏幕提示
 │    ├─ EPUB：字号/行距/边距/字体/主题/书写方向/布局
 │    └─ PDF：主题/滚动/双页
 ├─ 绑定：给系列（POST /api/reading-profile/series/{seriesId}）或库（.../library/{libraryId}）→ body 都是 `[profileIds]`
 ├─ 设备绑定：POST /api/reading-profile/set-devices?profileId=，body 为 `[deviceIds]`
 └─ 阅读器内修改 → 生成 implicit profile → 提示「保存到上级配置 / 提升为新配置 / 仅本次」
```

- 优先级（服务端已解析）：implicit(本系列) > 系列绑定 > 库绑定 > 默认；同级中**绑定当前设备**者优先。客户端只维护"本次会话覆盖"。
- 只读账号：不创建 implicit，不写回，仅本地临时生效。

---

## F13 我的 / 设置

```
我的
 ├─ 头像/用户名/角色徽标（Admin/Pleb/Read Only）+ 当前服务器名
 ├─ 快捷：切换服务器（→ F10）（隐私模式开关只在首页/书架/发现顶栏与设置里）
 ├─ 统计卡（GET /api/Stats/user-stats；M3 详情页有图表）
 ├─ 我的书签 / 我的清单 / 我的合集 / 我的评分
 └─ 设置
     ├─ 启动：自动登录的服务器（单选 / 不自动登录=默认）
     ├─ 账户：修改密码（POST /api/Account/reset-password）、邮箱、年龄限制、Auth Keys（查看/轮换）
     ├─ 服务器：服务器列表、添加、切换、删除（F10）
     ├─ 阅读：阅读配置、默认方向、音量键、自动下一章、预取页数
     ├─ 外观：主题（暗/亮/跟随）、动态取色(关)、隐私模式（与顶栏开关同一状态）
     ├─ 语言：中文 / English / Français / 日本語（默认跟随系统）
     ├─ 网络：超时、代理(可选)、清除图片缓存（按服务器）
     ├─ 关于：版本、致谢 Kavita、开源许可、问题反馈
     └─ 退出登录（二次确认，「保留服务器地址」勾选）
```

---

## F14 异常与恢复总表

| 场景 | 用户看到 | 系统行为 |
| --- | --- | --- |
| 无网络 | 顶部条「无网络连接」+ 已缓存内容 | 只读缓存，写操作入队；封面按隐私模式状态显示占位或缓存 |
| 服务器 5xx | 「服务器出错（500）」+ 重试 | 记录日志，不重试写操作 |
| Token 失效 | 「登录已过期」 | 回服务器选择页，该服务器标"需登录"，其它服务器不受影响 |
| 切换服务器失败 | 「无法连接 <名称>」 | 停在选择页，可编辑/删除该条目 |
| 会话被服务端撤销（权限变更） | 「你的访问权限已变更」 | 刷新 `/api/Account`，重算能力 |
| 内容被删除（404） | 「内容已不存在」 | 从缓存移除，返回上一级 |
| 年龄限制拦截 | 「此内容受年龄限制」 | 隐藏详情，不缓存封面 |
| 只读账号 | 写操作按钮隐藏 + 提示「演示账号只读」 | 禁用写 API |
| App 版本过旧 | 「请更新以继续使用」 | 阻断非读取操作 |

---

## F15 启动失败（开发/验收视角）

> 这类问题**用户只能看到闪退**，所以必须由我们在交付前拦住。判定与流程见 `AGENTS.md §8.1`。

```
安装后点图标 → 立即退回桌面（无任何界面）
 └─ 抓日志：adb logcat -d | findstr "FATAL EXCEPTION"
     ├─ IllegalStateException: You need to use a Theme.AppCompat theme
     │    → 主题父类不是 Theme.AppCompat.*（MainActivity 是 AppCompatActivity）
     │      修 themes.xml；改完必须重跑冷启动
     ├─ ClassNotFoundException / NoClassDefFoundError
     │    → Manifest 的 android:name 与实际类不符，或依赖被裁掉（R8/ProGuard）
     ├─ IllegalArgumentException: … has already been added / Hilt 相关
     │    → DI 模块重复绑定或 @AndroidEntryPoint 缺失
     ├─ Resources$NotFoundException
     │    → 资源 id 引用错误或 values-* 缺串
     └─ SQLiteException / IllegalStateException: Room
          → schema/version 变更未写 Migration（禁止破坏性迁移）
 └─ 无论哪一类：修完 → 冷启动 → pidof 非空 → 无 FATAL → 首屏 UI dump 有内容 → 才算完
```

**交付前固定动作**（不能省）：`assembleDebug` 绿 + 单测绿 + **冷启动冒烟绿**。前两项在 M0 都通过了，
但闪退依然发生——所以第三项是必要的。
