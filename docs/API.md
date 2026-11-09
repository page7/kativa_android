# API 对接清单（Kavita v0.9.1.x）

> 来源：Swagger 文档页实际加载的 `https://raw.githubusercontent.com/Kareadita/Kavita/develop/openapi.json`
> （共 **503** 个端点 / **392** 个 schema），并对照服务端源码校验过认证、分页、阅读器等关键实现。
> 本文只列出本 App 会用到的端点；新增调用必须在此登记。

---

## 0. 全局约定

| 项 | 值 |
| --- | --- |
| Base URL | 每个服务器独立：`{scheme}://{host}[:port]{optionalBasePath}/api/`（多服务器时按激活服务器切换） |
| 认证 | `Authorization: Bearer <jwt>`；图片/媒体用 `?apiKey=<authKey>` |
| 内容类型 | `application/json`（错误体可能是 `text/plain`） |
| 分页 | query `pageNumber`(1 起) + `pageSize`；**不传 pageSize 时服务端返回全部**（`int.MaxValue`） |
| 设备 | `X-Device-Id: <uuid>`；`User-Agent: Kativa/<ver> (Android <api>; Mobile)` |
| 时间 | ISO-8601 UTC 字符串 |
| 图片 | **隐私模式开启时不请求任何图片端点**（客户端短路，不发请求） |

**响应头 `Pagination` 示例**

```json
{"currentPage":1,"itemsPerPage":40,"totalItems":1234,"totalPages":31}
```

**错误体处理**

- 多数业务错误是**本地化纯文本**（如 `Your credentials are not correct`、`locked-out`）。
- 少数是 JSON（ModelState / `{message}`）。
- 实现：`if (contentType contains json) parse else body.string()`，超长截断 300 字符展示。

**角色（roles 字符串）**：`Admin`、`Pleb`、`Download`、`Change Password`、`Bookmark`、`Change Restriction`、`Login`、`Read Only`、`Promote`。

---

## 1. 认证与账户

### `GET /api/Health` ✅ 匿名
- 响应：**纯文本 `Ok`**（`Content-Type: text/plain`），用于服务器连通性检测。
- ⚠️ 不是 JSON（连 `"Ok"` 这种 JSON 字符串都不是）。用 JSON 反序列化会抛异常，必须读原始 body 再 `trim()` 比较。
- 若是 `http://` 服务器而请求了 `https://`，会得到 `SSLException: Unable to parse TLS packet header`（见 §13.21）。

### `GET /api/Server/server-info-slim` 🔒
- 响应 `ServerInfoSlimDto`：`installId, isDocker, kavitaVersion, firstInstallDate, firstInstallVersion`。
- 用途：版本兼容与"关于"页。

### `POST /api/Account/login` ✅ 匿名
```jsonc
// 账号密码
{ "username": "reader", "password": "***" }
// 或 API Key（忽略账号密码）
{ "apiKey": "a1b2c3..." }
```
- 200 → `UserDto`：`id, username, email, roles[], token, refreshToken, apiKey, authKeys[], preferences, ageRestriction, kavitaVersion, primaryColor, secondaryColor`
- 401 可能文案：`Your credentials are not correct` / `disabled-account` / `locked-out` / `password-authentication-disabled`
- **注意**：`UserDto.apiKey` 是 **OPDS key**；`authKeys[]` 里名为 `image-only` 的才是图片 key。
- 服务端会对失败登录做**锁定与限流**（多次失败 `locked-out`），UI 必须避免自动重试。

### `GET /api/Account` 🔒
- 返回当前用户（**不含 token**）。用于启动校验与权限刷新。
- 若返回 401 且含 `disabled-account` → 强制登出。

### `POST /api/Account/refresh-token` ✅ 匿名
```json
{ "token": "<jwt>", "refreshToken": "<refresh>" }
```
- 200 → 同结构新 token；401 `{message:"invalid-token"}` 或纯文本。
- ⚠️ **P0 未接入自动刷新**：token 过期由客户端按 JWT `exp` 判定（`core/auth/JwtExpiry`），
  选择页显示"登录已过期"并要求重新登录（只需密码）。本端点留给 P1 的 `Authenticator` 使用。

### 客户端会话判定（P0）
- JWT 的 `exp` 从 payload 里直接解析（不验签、不引依赖）；解析失败视为**未过期**，
  避免因服务端格式变化把用户锁在登录页。
- 业务请求返回 401 且请求带有 `Authorization` 时，`SessionExpiryInterceptor` 广播
  "会话过期" 并清除该服务器的 token；`Account/login` / `refresh-token` / `Plugin/authenticate`
  的 401 **不**走这条路径（密码错同样是 401）。
- 只持久化 `username`（`kativa_accounts`），**永不**持久化密码或 API Key。

### `POST /api/Plugin/authenticate?apiKey=&pluginName=Kativa` ✅ 匿名
- 备选登录：返回 `UserDto{username, token, refreshToken, apiKey, kavitaVersion}`（无 roles/preferences）。
- 适合"只拿到 key"的旧版服务端；登录后仍需 `GET /api/Account` 补全。

### `GET /api/Plugin/authkey-expires` 🔑（用 Auth Key 认证：`x-api-key` 头或 `?apiKey=`）
- 响应 `{ "expiresAt": "2026-12-31T00:00:00Z" }`，无过期时间则 `expiresAt = null`。
- API Key 登录前用它提示"已过期"；若 key 不属于当前账号，服务端返回 `null`（不代表有效）。

### `GET /api/Account/auth-keys` 🔒 / `POST /api/Account/create-auth-key` / `POST /api/Account/rotate-auth-key`
```json
// create/rotate body
{ "name": "image-only", "keyLength": 32, "expiresUtc": null }
```
- 响应 `AuthKeyDto`：`id, key, name, createdAtUtc, expiresAtUtc, lastAccessedAtUtc, provider`。
- **keyLength 允许 8-32**；`Read Only` 角色**禁止**创建/轮换（403）。
- 本 App 只允许创建/轮换 `image-only` 用途的 key；展示时掩码（前 4 后 4）。

### `GET /api/Account/opds-url` 🔒
- 返回 OPDS 完整 URL（含 key）。仅用于"复制到其他阅读器"展示，**不用于登录**。

### 其他账户端点（设置页）
| 端点 | 说明 |
| --- | --- |
| `POST /api/Account/update/username` | 改用户名（`Change Password` 或 Admin） |
| `POST /api/Account/update/email` | 改邮箱（需密码确认，`UpdateEmailDto{email,password}`） |
| `POST /api/Account/reset-password` | 改密 `{userName, password, oldPassword}`（新密码 6-256 位） |
| `POST /api/Account/update/age-restriction` | 年龄限制 `{ageRating, includeUnknowns}`（需 `Change Restriction`） |

> `POST /api/Account/update`（`UpdateUserDto`：userId/username/roles/libraries/ageRestriction/email）是 **Admin 管理他人账号**的接口，本 App **不调用**。
| `GET /api/Account/is-email-valid` | 邮箱状态 |
| `GET /api/Account/oidc-authenticated` | **不使用**（依赖浏览器 Cookie 的 OIDC 流程，本 App 不做） |

### `GET /api/Users/get-preferences` / `POST /api/Users/update-preferences` 🔒
- `UserPreferencesDto` 关键字段：
  `theme{name,normalizedName,fileName}, globalPageLayoutMode(0 Cards/1 List), blurUnreadSummaries, dataSaver, promptForDownloadSize, noTransitions, collapseSeriesRelationships, locale, promptForRereadsAfter, onDeckProgressDays, onDeckUpdateDays, aniListScrobblingEnabled, wantToReadSync, socialPreferences{...}, opdsPreferences{...}`
- **写回必须整体提交**（服务端为完整对象），所以 App 只改自己关心的字段并保留其余原值。
- `Read Only` 角色写回 403。

---

## 2. 库与系列

### `GET /api/Library/libraries` 🔒
- 200 → `LibraryDto[]`：`id, name, type, lastScanned, coverImage, includeInDashboard, includeInRecommended, ...`
- 非管理员时部分字段可能缺省（`GetLiteLibraryDtoByIdAsync` 仅 id/type/name）——**不要依赖空字段做逻辑**。

### `GET /api/Library?libraryId=` 🔒
- 权限校验用；无权限 403。非管理员仅返回 `LiteLibraryDto`（id/type/name）。

### `POST /api/Series/v2` 🔒 分页
query：`pageNumber, pageSize`
```jsonc
{
  "statements": [
    { "field": 19, "comparison": 0, "value": "3" }   // Libraries == 3
  ],
  "combination": 1,                                   // 0 Or / 1 And
  "sortOptions": { "sortField": 1, "isAscending": true },
  "limitTo": 0
}
```
- 200 → `SeriesDto[]` + `Pagination` 头。

**比较符必须按字段匹配（`ComparisonProfile` 校验，错配会抛异常而非返回空）**：`SeriesName(1)` 只接受
`Equal(0) / NotEqual(9) / BeginsWith(10) / EndsWith(11) / Matches(7)`；`Contains(5)` 只对**列表型**字段
（Tags/Genres/People 的 id 列表）有效，用在名字上会让整次请求 500/失败。客户端曾因此「筛选没反应」。
同理 `WantToRead(26)` 的值是 `"true"/"false"` 字符串（`bool.Parse`），且服务端在 And/Or 组合**之前**
单独预处理（`ApplyWantToReadFilter` + `ApplyLibraryFilter`），所以它和 `Libraries(19)` 都必须是
同一层 `statements` 里的兄弟项。

客户端只暴露三条：排序（`sortField` 1 名称 / 2 加入时间 / 7 上次阅读）、`SeriesName + Matches(7)` 名称搜索、
`WantToRead(26) == true` 收藏（想读）。`combination` 固定 `1`，避免用户条件把库范围并集掉。

### `GET /api/Series/{seriesId}` 🔒
- `SeriesDto`：`id, name, localizedName, sortName, pages, format, libraryId, libraryName, userRating, hasUserRated, totalReads, pagesRead, latestReadDate, minHoursToRead, maxHoursToRead, avgHoursToRead, releaseYear?(metadata), primaryColor, secondaryColor, aniListId, malId, ...`
- 404 → 内容已删除。

### `GET /api/Series/volumes?seriesId=` 🔒
- `VolumeDto[]`：`id, number, minNumber, maxNumber, name, pages, pagesRead, coverImage, chapters[]`
- `ChapterDto`：`id, number, range, minNumber, maxNumber, title, titleName, pages, isSpecial, pagesRead, volumeId, sortOrder, releaseDate, summary, wordCount, writers[], genres[], tags[], ...`
- ⚠️ **`number` 是 `[Obsolete]` 字段**（服务端标注 `Use MinNumber`）。`MinNumber`/`MaxNumber` 才是权威数值，且**带哨兵值**，见 §12.1。

### 哨兵编号（必读，否则一定会显示成 `-100000`）

Kavita 用**负数/超大数**编码「没有编号」和「番外容器」，它们不是真实卷号/话数：

| 字段值 | 含义 | 常量（`Kavita.Models/Constants/ParserConstants.cs`） |
| --- | --- | --- |
| `-100000` | **无编号**：文件名/ComicInfo 里没解析出章节号（或卷号） | `DefaultChapter` / `LooseLeafVolume` / `DefaultChapterNumber` |
| `100000` | **番外容器卷**：specials 都挂在这个合成卷下 | `SpecialVolumeNumber` / `SpecialVolume` |
| `0` | **真实**的第 0 卷/第 0 话（v0.8.0 起不再是「无编号」） | — |

**来源**（v0.8.0 发行说明原文）：

> Loose Leaf volumes are now encoded with the number **-100000** instead of 0. This now means users can have a Volume 0 without issue.
>
> Encoding: Special (**100000**), Loose Leaf Volume (**-100000**).

**客户端必须做三件事**：

1. **绝不把 `-100000` / `100000` 当数字渲染**。官方 Web UI 直接隐藏编号，改显示标题或「单卷 / 散章 / 番外」。
2. **把 `100000` 卷与 `-100000` 卷从常规卷列表里拆出来**，单独成「番外」「散章」区块。
3. **`0` 必须当普通编号处理**，不要因为「0 像空值」而过滤掉。

官方 Web 端对应实现：`UI/Web/src/app/_services/entity-title.service.ts`；
服务端甚至直接把这些章节从 Chapters 标签页剔除：

```csharp
// Kavita.Services/SeriesService.cs
private static bool ShouldIncludeChapter(ChapterDto chapter)
    => !chapter.IsSpecial && chapter.MinNumber.IsNot(Parser.DefaultChapterNumber);
```

本仓库实现：`domain/model/KavitaNumbering.kt`（常量与判定）+ `ui/common/EntityTitles.kt`（唯一格式化出口），
并有 `KavitaNumberingTest` 覆盖。**新增任何展示卷/章编号的地方都必须走 `EntityTitles`。**

### `GET /api/Series/volume?volumeId=` / `GET /api/Series/chapter?chapterId=` 🔒
- 单卷/单章详情（`VolumeDto` / `ChapterDto`）。

### `GET /api/Series/series-detail?seriesId=` ⚠️ 不稳定
- `SeriesDetailDto`：`specials[], chapters[], volumes[], storylineChapters[], libraryType, unreadCount, totalCount`。
- 官方标注"可能随时变化"：**仅用于聚合分类，失败必须降级**为本地按 `volumes` 推算。

### `POST /api/Series/on-deck` 🔒 分页
query：`pageNumber, pageSize, libraryId`（0=全部）
- 200 → `SeriesDto[]`（有进度的系列，按最近阅读排序）。

### `POST /api/Series/recently-added-v2` 🔒 分页
query：`pageNumber, pageSize`；body：`SeriesFilterV2Dto`（可 `{}`）。

### `POST /api/Series/recently-updated-series` 🔒 分页
query：`pageNumber, pageSize`
- 200 → `GroupedSeriesDto[]`：{`seriesName, localizedSeriesName, seriesId, libraryId, libraryType, chapterId, volumeId, created, format, count`}
- 首页"最近更新"用它渲染"第 X 章 新增"。

### `POST /api/Series/all-v2` 🔒 分页
query：`pageNumber, pageSize, userId?, context?`
- 与 `v2` 区别：`v2` 面向库内（系列筛选），`all-v2` 面向全库集合（context 见下）。
- `QueryContext`：`1 None / 2 Search / 4 Dashboard`（首页流用 `4`，搜索用 `2`，普通浏览用 `1`）。

### `POST /api/Series/remove-from-on-deck?seriesId=` 🔒
- 从 On Deck 移除（直到下次阅读事件）。用于长按卡片"移除"。

### 其他系列端点
| 端点 | 说明 |
| --- | --- |
| `GET /api/Series/related?seriesId=&relation=` | 相关作品，`RelationKind` 见枚举 |
| `GET /api/Series/all-related?seriesId=` | 全部关系分组 |
| `GET /api/Series/next-expected?seriesId=` | 预测下一章日期（可能 null） |
| `GET /api/Series/metadata?seriesId=` | `SeriesMetadataDto`（只读展示） |
| `POST /api/Series/refresh-metadata` / `POST /api/Series/scan` | **Admin 专属**，本 App 不调用（仅在管理员时以"更多"菜单触发） |
| `GET /api/Series/age-rating?ageRating=` | 数值 → 文案 |

---

## 3. 阅读（进度 / 书签 / 章节流转）

### `GET /api/Reader/chapter-info?chapterId=&includeDimensions=true&extractPdf=false` 🔒
- `ChapterInfoDto`：`chapterNumber, volumeNumber, volumeId, seriesName, seriesFormat, seriesId, libraryId, libraryType, chapterTitle, pages, fileName, isSpecial, title, seriesTotalPages, seriesTotalPagesRead, seriesPublicationStatus, pageDimensions[], doublePairs{}`
- **副作用**：触发服务端解包并缓存章节图片（首次会慢）。
- `pageDimensions` 仅在 `includeDimensions=true` 时填充；`doublePairs` 用于双页配对。

### `GET /api/Reader/image?chapterId=&page=&apiKey=&extractPdf=` 🔒 二进制
- **page 从 0 开始。**
- 必须带 `apiKey`（query）——服务端 `ExtractAuthKey` 优先取 query。
- 响应带 `ETag`/`Cache-Control(max-age 1h)` → Coil/OkHttp 直接缓存。

### `GET /api/Reader/pdf?chapterId=&apiKey=&extractPdf=` 🔒 二进制
- 返回整章 PDF 文件（用于 `PdfRenderer`）。

### `GET /api/Reader/thumbnail?chapterId=&pageNum=&apiKey=` 🔒 二进制
- 书签/进度缩略图。

### `GET /api/Reader/file-dimensions?chapterId=` 🔒
- `FileDimensionDto[]`：`width, height, pageNumber, fileName, isWide`。
- `isWide` 用于双页模式判定宽页。

### `GET /api/Reader/get-progress?chapterId=` 🔒
- `ProgressDto`：`volumeId, chapterId, pageNum, seriesId, libraryId, bookScrollId, lastModifiedUtc`
- **pageNum 是 1 基**；无进度时返回 `pageNum=0`，且 `volumeId/seriesId/libraryId` **均为 0**。
- 因此 `seriesId/libraryId/volumeId` 必须取自 `chapter-info` 或章节 DTO，**不能从 progress 反推**。

### `POST /api/Reader/progress` 🔒
```json
{ "libraryId": 1, "seriesId": 2, "volumeId": 3, "chapterId": 4, "pageNum": 5, "bookScrollId": null }
```
- body **全部必填**（`pageNum` 除外无默认，传 0 表示未开始）。
- 完成一章：`pageNum = pages + 1`。
- 重新阅读：先 `pageNum=0` 再 `1`（保证统计正确）。

### `GET /api/Reader/continue-point?seriesId=` 🔒
- 返回应继续阅读的 `ChapterDto`（无进度 → 第一章，跳过 specials）。

### `GET /api/Reader/has-progress?seriesId=` 🔒 → `boolean`

### `GET /api/Reader/next-chapter?seriesId=&volumeId=&currentChapterId=` 🔒 → `int`
### `GET /api/Reader/prev-chapter?...` 🔒 → `int`
- 返回章节 id；`0` 表示无。

### `POST /api/Reader/mark-read` 🔒
```json
{ "seriesId": 2, "generateReadingSession": false }
```
### `POST /api/Reader/mark-chapter-read`
```json
{ "seriesId": 2, "chapterId": 4, "generateReadingSession": false }
```
### `POST /api/Reader/mark-volume-read`
```json
{ "seriesId": 2, "volumeId": 3, "generateReadingSession": false }
```
- 对应 `mark-unread` / `mark-volume-unread` / `mark-multiple-read` / `mark-multiple-unread` / `mark-multiple-series-read`。
- `generateReadingSession=true` 会写入阅读会话统计（用户手动"标记已读"建议 false，避免伪造阅读时长）。

### `GET /api/Reader/time-left?seriesId=` / `time-left-for-chapter?seriesId=&chapterId=` 🔒
- `HourEstimateRangeDto`：`minHours, maxHours, avgHours, wordCount, pageCount`。

### 书签
| 端点 | 说明 |
| --- | --- |
| `GET /api/Reader/chapter-bookmarks?chapterId=` | 本章书签 `BookmarkDto[]` |
| `GET /api/Reader/volume-bookmarks?volumeId=` / `series-bookmarks?seriesId=` | 卷/系列书签 |
| `POST /api/Reader/all-bookmarks` | body `SeriesFilterV2Dto`（**仅支持 SeriesNameQuery 类语句**，即 `field:1` 的系列名筛选；分页不生效，返回全部匹配书签） |
| `POST /api/Reader/bookmark` | body `{page, volumeId, seriesId, chapterId, imageOffset?, xPath?}` |
| `POST /api/Reader/unbookmark` | 同上（定位） |
| `POST /api/Reader/remove-bookmarks` / `bulk-remove-bookmarks` | 批量删 |
| `GET /api/Image/bookmark?chapterId=&pageNum=&imageOffset=` | 书签页图片（需 apiKey） |

> **`BookmarkDto.page` 是 0 基图片索引**，不是 `pageNum`。Kavita Web 端阅读器提交的是 0 基的
> `pageNum`，`Reader/image?page=` 与 `Image/bookmark?pageNum=` 使用同一索引；客户端**只在展示时
> +1**。曾经按"进度 `pageNum` 是 1 基"的直觉给它 `+1`，结果书签存到下一页、列表点开错页，且章节
> 最后一页会 400（`Image/bookmark` 用 `page/chapterId/imageOffset` 唯一定位，越界即 `bookmark-doesnt-exist`）。
>
> `POST /api/Reader/all-bookmarks` 是唯一返回 `series` 内嵌对象的书签端点（`BookmarkDto.series`），
> 书签列表的系列名只能来自它。body 必须是 `SeriesFilterV2Dto`：服务端直接枚举 `Statements`，
> 空 body 会 500；`limitTo` 默认 0（不限制）。
>
> `Image/bookmark` 属于"封面类"媒体：隐私模式下同样**不发请求**（见 `PrivacyGuardInterceptor.COVER_PATHS`）。

### `GET /api/Reader/ptoc?chapterId=` / `POST /api/Reader/create-ptoc` / `DELETE /api/Reader/ptoc`
- 个人目录（PDF/漫画可用）：`PersonalToCDto{id, chapterId, pageNumber, title, bookScrollId, selectedText}`。P2。

---

## 4. 阅读配置（Reading Profiles）

### `GET /api/reading-profile/all` 🔒
- `UserReadingProfileDto[]`（不含 implicit），`kind`: `Default(0) / User(1) / Implicit(2)`。

### `GET /api/reading-profile/{libraryId}/{seriesId}?skipImplicit=false&deviceId=<id>` 🔒
- 返回该系列应生效的配置，**服务端已按优先级解析**：
  1. 本系列的 implicit
  2. 系列的配置
  3. 库的配置
  4. 默认配置
  同级中绑定当前 `deviceId` 的优先。

### 写操作（全部 `DisallowRole(ReadOnly)`）
| 端点 | 签名 |
| --- | --- |
| `POST /api/reading-profile/create` | body 完整 `UserReadingProfileDto`（`name` 必填） |
| `POST /api/reading-profile` | body 完整 `UserReadingProfileDto`（按 `id` 更新；**不更新关联系列/库**） |
| `DELETE /api/reading-profile?profileId=` | 删除（默认配置不可删） |
| `POST /api/reading-profile/series?libraryId=&seriesId=&deviceId=` | body `UserReadingProfileDto`，写**implicit** 配置（阅读器内改动即用它） |
| `POST /api/reading-profile/update-parent?libraryId=&seriesId=&deviceId=` | 把当前设置写回**上级（系列/库/默认）配置**，并清除 implicit |
| `POST /api/reading-profile/series/{seriesId}` | body `[profileIds]`，绑定系列（替换旧的） |
| `DELETE /api/reading-profile/series/{seriesId}` | 解绑系列 |
| `POST /api/reading-profile/library/{libraryId}` | body `[profileIds]`，绑定库 |
| `DELETE /api/reading-profile/library/{libraryId}` | 解绑库 |
| `POST /api/reading-profile/set-devices?profileId=` | body `[deviceIds]` |
| `POST /api/reading-profile/promote?profileId=&deviceId=` | 把 implicit 提升为用户配置 |
| `POST /api/reading-profile/bulk` | body `{profileIds:[...], seriesIds:[...]}` |

> `deviceId` 省略时服务端用当前请求的设备（由 `X-Device-Id`/UA 判定）；本 App 始终显式传，避免多设备串味。

### `UserReadingProfileDto` 关键字段（漫画）
`readingDirection(0 LTR/1 RTL)`、`scalingOption(0 FitHeight/1 FitWidth/2 Original/3 Automatic)`、`pageSplitOption(0 SplitLTR/1 SplitRTL/2 NoSplit/3 FitSplit)`、`readerMode(0 LeftRight/1 UpDown/2 Webtoon)`、`layoutMode(1 Single/2 Double/3 DoubleReversed)`、`autoCloseMenu`、`showScreenHints`、`emulateBook`、`backgroundColor("#000000")`、`swipeToPaginate`、`allowAutomaticWebtoonReaderDetection`、`widthOverride(int?)`、`disableWidthOverride(0/768/1280/1440)`。
EPUB：`bookReaderMargin/LineSpacing/FontSize/FontFamily/TapToPaginate/ReadingDirection/WritingStyle(0 Horizontal/1 Vertical)/ThemeName/LayoutMode(0 Default/1 Column1/2 Column2)/ImmersiveMode/DisableBookmarkIcon`。
PDF：`pdfTheme(0 Dark/1 Light)`、`pdfScrollMode(0/1/3)`、`pdfSpreadMode(0 None/1 Odd/2 Even)`。

### `GET /api/Device/client/devices?includeInactive=false` 🔒
- `ClientDeviceDto[]`：`id, friendlyName, uiFingerprint, currentClientInfo{...}, firstSeenUtc, lastSeenUtc`。
- `X-Device-Id` 只是"UI 指纹"，不是注册设备 id；要拿到可用于 `deviceId` 的整数 id，需 `GET /api/Device/client/devices` 里与本机指纹匹配的记录。
- 首次登录若列表为空：服务端会在后续请求中自动登记设备（本 App 不做"注册设备"UI）；未匹配到时不传 `deviceId`，让服务端按 UA 兜底。

---

## 5. 首页流 / 智能筛选 / 筛选编码

### `GET /api/Stream/dashboard?visibleOnly=true` 🔒
- `DashboardStreamDto[]`：`id, name, isProvided, order, smartFilterEncoded, smartFilterId, streamType, visible, entityType`
- `streamType`：`1 OnDeck / 2 RecentlyUpdated / 3 NewlyAdded / 4 SmartFilter`
- **顺序即显示顺序**（按 `order`）。

### `GET /api/Stream/sidenav?visibleOnly=true` 🔒
- `SideNavStreamDto`：`streamType 1 Collections / 2 ReadingLists / 3 Bookmarks / 4 Library / 5 SmartFilter / 6 ExternalSource / 7 AllSeries / 8 WantToRead / 9 BrowsePeople`。
- 移动端用于「发现」页入口顺序（不照搬 10 项限制）。

### 自定义（P2）
`POST /api/Stream/update-dashboard-stream`（body `DashboardStreamDto`）、`POST /api/Stream/update-dashboard-position` `{streamName,id,fromPosition,toPosition}`、`POST /api/Stream/add-dashboard-stream?smartFilterId=`、`DELETE /api/Stream/smart-filter-dashboard-stream?dashboardStreamId=`。

### 智能筛选
| 端点 | 说明 |
| --- | --- |
| `GET /api/Filter` | `SmartFilterDto[]`：`id, name, filter(encoded), entityType` |
| `POST /api/Filter/update/series` | body `SeriesFilterV2Dto`（`name` 必填；带 `id` 更新/无 `id` 新建），响应 **`200 OK` 无 body**，需再 `GET /api/Filter` 刷新列表 |
| `POST /api/Filter/encode/series` | body `SeriesFilterV2Dto` → 编码字符串 |
| `POST /api/Filter/decode` | body `{encodedFilter}` → `IFilterDto`（按 entityType 具体化） |
| `POST /api/Filter/rename?filterId=&name=` | 重命名 |
| `DELETE /api/Filter?filterId=` | 删除 |

> 其它实体：`update/reading-list`、`update/person`、`update/annotation`。

---

## 6. 检索与元数据

### `GET /api/Search/search?queryString=&includeChapterAndFiles=false` 🔒
- `SearchResultGroupDto`：`libraries[], series[], collections[], readingLists[], persons[], genres[], tags[], files[], chapters[], bookmarks[], annotations[]`
- `SearchResultDto`：`seriesId, name, localizedName, sortName, format, libraryName, libraryId, releaseYear, volumeCount, chapterCount`
- **只搜 `includeInSearch=true` 资料库**：`SearchController` 先用 `GetLibraryIdsForUserIdAsync(userId, QueryContext.Search)` 取库列表，`SeriesRepository.SearchSeriesAsync` 再按 `libraryIds.Contains(s.LibraryId)` 过滤。关掉某库的「包含在搜索中」后，该库所有系列都搜不到——这不是编码问题，客户端无法绕过（`GET /api/Library/libraries` 的 `includeInSearch` 字段可自查）。
- **匹配规则是 SQL LIKE 子串，不做简繁/异体折叠**：`s.Name / OriginalName / LocalizedName / NormalizedName` 各做一次 `%query%`。实测 `樂園`→命中，`乐园`→0 条；`女兒`→命中，`女儿`→0 条。要跨简繁必须有客户端变体展开。
- 单字可搜（`学`/`園`/`力`），空串 400。`queryString` 按 UTF-8 百分号编码，服务端会再做一次 `Uri.UnescapeDataString` 并去掉 `%`/`:`。

### 浏览
| 端点 | 说明 |
| --- | --- |
| `GET /api/Metadata/genres?libraryIds=` / `GET /api/Metadata/tags?libraryIds=` | `GenreTagDto[]` / `TagDto[]`（`{id,title}`）；`libraryIds` 逗号分隔可空 |
| `POST /api/Metadata/genres-with-counts` / `tags-with-counts` | 带 `seriesCount/chapterCount`，适合"浏览"页排序。**POST body 是 `UserParams`**（`{pageNumber,pageSize}`），不是空对象 |
| `POST /api/Person/all?pageNumber=&pageSize=` | 人物分页列表 `PersonDto[]`：`id, name, aliases[], description, roles[], coverImage` |
| `GET /api/Person?name=` | 按名称取人物详情 |
| `GET /api/Person/series-known-for?personId=` | 代表作 |
| `GET /api/Person/search?queryString=` | 人物搜索（typeahead） |

---

## 7. 合集 / 阅读清单 / 想读

### 合集
| 端点 | 说明 |
| --- | --- |
| `GET /api/Collection?ownedOnly=false&sortByLastModified=false` | `AppUserCollectionDto[]`：`id, title, summary, promoted, coverImage, itemCount, owner, source, totalSourceCount` |
| `GET /api/Collection/single?collectionId=` | 单个 |
| `GET /api/Series/series-by-collection?collectionId=` | 合集内系列（`SeriesDto[]`） |
| `GET /api/Collection/all-series?seriesId=&ownedOnly=` | 系列所属合集 |
| 写操作 | `POST /api/Collection/update`、`update-for-series`、`update-series`、`promote-multiple`、`delete-multiple`、`DELETE /api/Collection?tagId=` |

### 阅读清单
| 端点 | 说明 |
| --- | --- |
| `POST /api/ReadingList/lists?pageNumber=&pageSize=&includePromoted=true&sortByLastModified=false` | `ReadingListDto[]`：`id, title, summary, promoted, coverImage, itemCount, startingYear, endingYear, ageRating, ownerUserName, provider, canSync` |
| `GET /api/ReadingList?readingListId=` | 单个（无权限时服务端返回 **400** `reading-list-restricted`，不是 403） |
| `GET /api/ReadingList/items?readingListId=` | `ReadingListItemDto[]`（含 `seriesName, chapterNumber, volumeNumber, pagesRead, pagesTotal, chapterId, volumeId, seriesId, libraryId`） |
| `GET /api/ReadingList/next-chapter?currentChapterId=&readingListId=` | 清单内下一章 |
| `POST /api/ReadingList/create` / `update` | 创建/更新 |
| `POST /api/ReadingList/update-position` | `{readingListId, fromPosition, toPosition, itemId}`（P2 拖拽） |
| `POST /api/ReadingList/delete-item` | 移除项 |
| `DELETE /api/ReadingList?readingListId=` | 删清单 |

### 想读
| 端点 | 说明 |
| --- | --- |
| `POST /api/want-to-read/v2?pageNumber=&pageSize=` body `SeriesFilterV2Dto` | 列表 |
| `GET /api/want-to-read?seriesId=` | 是否在想读（**注意返回是 `true`/`false` 纯文本**） |
| `POST /api/want-to-read/add-series` / `remove-series` | body `{seriesIds:[1,2]}` |

---

## 8. 下载端点（**本 App 不使用**）

> 产品决定：**不做下载/离线阅读模块**（见 AGENTS §10）。以下端点仅作知识登记，**任何代码都不得调用**；
> CI 会用静态规则扫描 `api/Download/` 字符串作为门禁。

| 端点 | 说明 |
| --- | --- |
| `GET /api/Download/chapter-size?chapterId=` | 字节数 |
| `GET /api/Download/series-size?seriesId=` | 字节数 |
| `POST /api/Download/bulk-chapter-size` | `{chapterIds:[...]}` → `{id:size}` |
| `GET /api/Download/chapter?chapterId=` | zip 文件（**需 Download 角色**） |
| `GET /api/Download/series?seriesId=` / `volume?volumeId=` | 整包 zip |
| `POST /api/Download/bookmarks` | 书签页打包 |

- 这些端点要求 `Download` 或 `Admin` 角色。
- 唯一"落盘"行为是 PDF 阅读所需的临时文件（`GET /api/Reader/pdf` → app cache，LRU 50MB），那是渲染前置条件，不是下载功能。

---

## 9. 评分 / 评论 / 标注（K+）

| 端点 | 说明 |
| --- | --- |
| `POST /api/Rating/series` | `{seriesId, userRating}`（0-10，步进 0.5）→ 200 空 |
| `POST /api/Rating/chapter` | `{seriesId, chapterId, userRating}` |
| `GET /api/Rating/overall-series?seriesId=` | `RatingDto`（聚合均分与人数） |
| `GET /api/Review/my-series?seriesId=` | `UserRatingAndReviewDto`（我的评分+评论，用于编辑态预填） |
| `POST /api/Review/series` | `{seriesId, body}` → `UserReviewDto` |
| `DELETE /api/Review/series?seriesId=` | 删评论 |
| `GET /api/Review/all?seriesId=&rating=` | 评论列表（`userId`/`rating`/`filterQuery` 可选；含外部来源 `provider`） |
| `GET /api/Annotation/all?chapterId=` | 批注列表（`AnnotationDto[]`） |
| `GET /api/Annotation/all-for-series?seriesId=` | 系列批注（**返回单个 `AnnotationDto`，非数组**） |
| `POST /api/Annotation/like` / `unlike` | body `[ids]`（JSON 数组，直接作为 body） |
| `POST /api/Annotation/all-filtered?PageNumber=&PageSize=` | body `AnnotationFilterDto`，分页批注 |

`AnnotationDto` 关键：`id, xPath, endingXPath, selectedText, comment, chapterId, volumeId, seriesId, pageNumber, containsSpoiler, ownerUsername, likes[], createdUtc`。

---

## 10. 统计

| 端点 | 返回 |
| --- | --- |
| `GET /api/Stats/user-stats?userId=` | `ProfileStatBarDto`（阅读时长、页数、连续天数等） |
| `GET /api/Stats/reading-activity?userId=&year=&filter.StartDate=&filter.EndDate=` | 热力图数据 |
| `GET /api/Stats/reading-pace?userId=&year=&booksOnly=` | 阅读速度 |
| `GET /api/Stats/genre-breakdown?userId=` | 类型分布 `BreakDownDto<string>` |
| `GET /api/Stats/tag-breakdown?userId=` | 标签分布 |
| `GET /api/Stats/reads-by-month?userId=` | 月度阅读量 |
| `GET /api/Stats/day-breakdown?userId=` | 星期分布 |
| `GET /api/Stats/reading-history?PageNumber=&PageSize=&StartDate=&EndDate=&TimeZoneId=` | 阅读历史 `PagedList<ReadingHistoryItemDto>`（分页头） |
| `GET /api/Stats/reading-history/series/{seriesId}?tzId=&PageNumber=&PageSize=` | 系列阅读历史 |
| `GET /api/Stats/total-reads?userId=` | 总阅读次数 |

> 统计端点通用可选参数：`StartDate`、`EndDate`、`TimeZoneId`、`Libraries`（逗号分隔库 id）。
> 服务端统计以 `userId` 区分；只能查自己有权限的（分享 profile 的）用户。
> `reading-history` 用 **PascalCase** 的 `PageNumber/PageSize`（与其他端点的 `pageNumber` 大小写不同，ASP.NET 绑定不区分，但 C# 端定义为 `UserParams`，两者都可用）。

---

## 11. 图片端点（全部需 `apiKey` query）

| 端点 | 参数 |
| --- | --- |
| `/api/Image/series-cover` | `seriesId` |
| `/api/Image/volume-cover` | `volumeId` |
| `/api/Image/chapter-cover` | `chapterId` |
| `/api/Image/collection-cover` | `collectionTagId` |
| `/api/Image/readinglist-cover` | `readingListId` |
| `/api/Image/library-cover` | `libraryId` |
| `/api/Image/person-cover` | `personId` |
| `/api/Image/user-cover` | `userId` |
| `/api/Image/bookmark` | `chapterId, pageNum, imageOffset` |

- 服务端按 `?apiKey=` 的 key 校验用户；未授权返回占位图或 401。
- 建议尺寸参数不生效，客户端用 Coil 的 `size()` 采样。
- 封面比例 2:3（约 1000×1500）。

---

## 12. 枚举对照表（严格按数字映射）

### MangaFormat（文件格式）
| 值 | 名称 | 渲染路径 |
| --- | --- | --- |
| 0 | Image | 图片阅读器 |
| 1 | Archive | 图片阅读器（服务端已解包） |
| 2 | Unknown | 图片阅读器（兜底） |
| 3 | Epub | EPUB 阅读器 |
| 4 | Pdf | PDF 阅读器 |

### LibraryType
`0 Manga / 1 Comic(Flexible) / 2 Book / 3 Image / 4 LightNovel / 5 ComicVine`

### PublicationStatus
`0 OnGoing(连载) / 1 Hiatus(休刊) / 2 Completed(完结) / 3 Cancelled(腰斩) / 4 Ended(已完结但缺卷)`

### AgeRating
`-1 NotApplicable / 0 Unknown / 1 RatingPending / 2 EarlyChildhood / 3 Everyone / 4 G / 5 Everyone10+ / 6 PG / 7 KidsToAdults / 8 Teen / 9 MA15+ / 10 Mature17+ / 11 M / 12 R18+ / 13 AdultsOnly / 14 X18+`

### ReaderMode / ReadingDirection / LayoutMode / ScalingOption / PageSplitOption
| 枚举 | 值 |
| --- | --- |
| ReaderMode | `0 LeftRight / 1 UpDown / 2 Webtoon` |
| ReadingDirection | `0 LeftToRight / 1 RightToLeft` |
| LayoutMode | `1 Single / 2 Double / 3 DoubleReversed` |
| ScalingOption | `0 FitToHeight / 1 FitToWidth / 2 Original / 3 Automatic` |
| PageSplitOption | `0 SplitLeftToRight / 1 SplitRightToLeft / 2 NoSplit / 3 FitSplit` |
| BookPageLayoutMode | `0 Default / 1 Column1 / 2 Column2` |
| PdfTheme / PdfScrollMode / PdfSpreadMode | `0 Dark/1 Light`、`0/1/3`、`0 None/1 Odd/2 Even` |

### SeriesSortField（`sortField`）
`1 SortName / 2 CreatedDate / 3 LastModifiedDate / 4 LastChapterAdded / 5 TimeToRead / 6 ReleaseYear / 7 ReadProgress / 8 AverageRating(K+) / 9 Random / 10 UserRating / 11 UnreadChapterCount`

### SeriesFilterField（`field`）
`-1 None / 0 Summary / 1 SeriesName / 2 PublicationStatus / 3 Languages / 4 AgeRating / 5 UserRating / 6 Tags / 7 CollectionTags / 8 Translators / 9 Characters / 10 Publisher / 11 Editor / 12 CoverArtist / 13 Letterer / 14 Colorist / 15 Inker / 16 Penciller / 17 Writers / 18 Genres / 19 Libraries / 20 ReadProgress / 21 Formats / 22 ReleaseYear / 23 ReadTime / 24 Path / 25 FilePath / 26 WantToRead / 27 ReadingDate / 28 AverageRating / 29 Imprint / 30 Team / 31 Location / 32 ReadLast / 33 FileSize / 34 CollapseSeriesRelationships`

### FilterComparison（`comparison`）
`0 Equal / 1 GreaterThan / 2 GreaterThanEqual / 3 LessThan / 4 LessThanEqual / 5 Contains / 6 MustContains / 7 Matches / 8 NotContains / 9 NotEqual / 10 BeginsWith / 11 EndsWith / 12 IsBefore / 13 IsAfter / 14 IsInLast / 15 IsNotInLast / 16 IsEmpty / 17 IsNotEmpty`

### FilterCombination / FilterEntityType / QueryContext
`FilterCombination: 0 Or / 1 And`
`FilterEntityType: 0 Series / 1 ReadingList / 2 Person / 3 Annotation`
`QueryContext: 1 None / 2 Search / 4 Dashboard`（无集合/清单上下文，传错会改变排序语境）

### RelationKind
`1 Prequel / 2 Sequel / 3 SpinOff / 4 Adaptation / 5 SideStory / 6 Character / 7 Contains / 8 Other / 9 AlternativeSetting / 10 AlternativeVersion / 11 Doujinshi / 12 Parent / 13 Edition / 14 Annual / 16 Cameo`（`15` 空缺）

### ScrobbleProvider（评分/评论来源）
`0 Kavita / 1 AniList / 2 Mal / 4 Cbr / 5 Hardcover / 6 MangaBaka`（`4` 不是笔误，`3` 空缺）

### PersonRole（筛选/作者分组）
`3 Writer / 4 Penciller / 5 Inker / 6 Colorist / 7 Letterer / 8 CoverArtist / 9 Editor / 10 Publisher / 11 Character / 12 Translator / 13 Imprint / 14 Team / 15 Location`

### 阅读配置相关
`ReadingProfileKind: 0 Default / 1 User / 2 Implicit`
`WritingStyle: 0 Horizontal / 1 Vertical`
`BreakPoint: 0 Never / 768 / 1280 / 1440`

### SideNavStreamType / DashboardStreamType
`SideNav: 1 Collections / 2 ReadingLists / 3 Bookmarks / 4 Library / 5 SmartFilter / 6 ExternalSource / 7 AllSeries / 8 WantToRead / 9 BrowsePeople`
`Dashboard: 1 OnDeck / 2 RecentlyUpdated / 3 NewlyAdded / 4 SmartFilter`

---

## 13. 对接陷阱清单（照着核对）

1. **请求路径不要以 `/` 开头**：baseUrl 已含 `api/`，否则变成 `/api//api/...`。
2. **登录响应是 UserDto**，`token` 只在登录/刷新时返回；`GET /api/Account` 不返回 token。
3. **图片必须用 query apiKey**（`x-api-key` header 对 `<img>` 无效；App 里两者都带，但服务端优先 query）。
4. **进度 1 基、图片 0 基**，换算只写一处。
5. **完成章节 = pages+1**；`pageNum == pages` 是"停在最后一页"。
6. **分页信息只有响应头**，响应体是数组；且**必须传 pageSize**，否则服务端返回全部数据（`UserParams.PageSize` 默认 `int.MaxValue`）。
7. **筛选 POST 需要 body**，即使为空也要 `{}`。
8. **`GET /api/want-to-read?seriesId=` 返回纯文本 `true`/`false`**，不是 JSON。
9. **错误体多为纯文本**，不能假设 JSON。
10. **`Read Only` 角色**：创建 Auth Key/写偏好/评分/进度都会 403——UI 层先行隐藏。
11. **`Series/series-detail` 不稳定**，失败降级。
12. **章节列表顺序**用 `sortOrder` 数值排序，不用字符串 `number`（后者可能带 `-100000`）。
13. **EPUB 进度**用 `bookScrollId` 锚点，不能只用页码。
14. **`AuthKey` 长度 8-32**，`image-only` 名称是 Web 端约定；改名会导致取不到 key。
15. **服务端可能开启 OIDC 且禁用密码**：登录 401 `password-authentication-disabled` → 引导用户用 API Key。
16. **`libraryId` 在 `progress` 请求里是必填**，从 `chapterInfo.libraryId` 取，别用系列里的猜测。
17. **年龄限制**：服务端已做过滤，客户端不要自行推断；被拦截内容返回 404/空列表。
18. **`includeDimensions=true` 会显著变慢**（首次解包），只在阅读器首屏与双页判定时使用。
19. **`minNumber == -100000` 表示「无编号」，`== 100000` 表示番外容器卷**，都不能当数字显示；`0` 是真实编号。见 §2「哨兵编号」。这是把 `Vol. -100000` 显示给用户的直接原因。
20. **`GET /api/Health` 返回纯文本 `Ok`**，不是 JSON 字符串；用 JSON 转换器反序列化会抛 `SerializationException`，必须按原始 body 读。
21. **只支持明文 HTTP 的自建服务器很常见**：`https://` 探测会得到 `SSLException: Unable to parse TLS packet header`。此时应回退 `http://` 重试并提示用户，不要让用户自己猜协议。
22. **网络层拦截器的注册顺序即执行顺序**：任何需要影响鉴权/明文门禁的 header，必须注册在门禁之前，否则门禁先跑就看不到它。
