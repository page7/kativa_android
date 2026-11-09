# 素材清单（Assets）

> 规则：App 内**所有**图片与按钮图标都必须来自本清单登记的素材；素材格式为 **SVG 源文件 → 生成 PNG**。
> 开发期使用**自动生成的占位 SVG/PNG**；正式素材完成后由维护者通知，届时按本清单**批量替换**。
> 新增素材的流程见 §3；CI 会检查"工程内图片资源 ⊆ 清单登记项"。

---

## 1. 管线与目录约定

```
assigns/                     维护者临时投放的源素材（logo.svg = 品牌图标，kavita.svg = 旧版兼容回退）
assets/svg/                  正式 SVG 源，文件名 = 清单 ID（最终交付物，保留可重新生成）
assets/generated/svg/        生成器解析后的 SVG 快照（gitignore，仅用于排错）
app/src/main/res/
  drawable-nodpi/            大图（logo、欢迎页、空态插画）→ PNG
  drawable-mdpi|xhdpi|xxhdpi|xxxhdpi/  图标 → PNG（按密度出图，基准 1x = 24dp）
  mipmap-anydpi-v26/         自适应图标 XML（ic_launcher / ic_launcher_round）
  mipmap-*/                  传统图标 PNG 与自适应前景层 PNG
  drawable/                  小尺寸单密度 PNG（隐私占位等纯色图，可接受）
```

生成命令：

```bash
./gradlew :app:generateAssets           # 从 assets/manifest.json 生成全部 PNG
./gradlew :app:checkAssetsRegistered    # 校验 res/ 无未登记 PNG、svg/ 无拼错的 ID
./gradlew :tools:assets:test            # 源解析优先级单测
```

实现：`tools/assets`（Kotlin/JVM 子工程，jsvg 2.2.0 渲染）。清单源为 `assets/manifest.json`，生成的 SVG 落在
`assets/generated/svg/`，PNG 直接写入 `app/src/main/res/drawable-*/` 与 `mipmap-*/`。

- 占位 SVG 由脚本按清单条目自动生成（纯色矩形 + 文字标识，如 `ic_privacy`），保证每个条目**永远有图可用**。
- 正式素材放进 `assets/svg/<素材ID>.svg`（**文件名 = 清单 ID**），生成器**逐条**按下面的优先级取源：
  1. `assets/svg/<id>.svg` — 正式交付物（推荐）
  2. `assigns/<id>.svg` — 维护者临时投放，命名规则同上
  3. `assigns/logo.svg` — **品牌图标**，仅 `launcher` / `launcher-foreground`
  4. `assigns/kavita.svg` — 旧版兼容回退，仅 `launcher` / `launcher-foreground`
  5. 脚本占位图 — 以上都没有时兜底
  每次生成都会逐条打印 `id / kind / 取自哪个文件`，并在末尾列出**仍是占位的条目**，
  所以"放进去没生效"或"忘了替换"都会立刻暴露。
- **当前应用图标**：`assigns/logo.svg`（1024 viewBox，满幅 `#44D39D` 底 + 白色书页标记）→
  `ic_launcher_foreground` / `ic_launcher` / `ic_launcher_round` 三项。
  - 生成器按 **safe 模式**（默认）把整图对齐到 72dp 可见区，**不要**设 `bleed: "full"`：自适应图标画布
    108dp 只有中间 72dp 会显示，铺满 108dp 等于把标记放大 1.5 倍（实测白色标记从 50% 变成 73.5%）。
  - `mipmap-anydpi-v26/*.xml` 的 `<background>` 用 `@color/ic_launcher_background`（`#44D39D`），
    与图内底色一致，遮罩边缘无缝——正因如此才不需要靠 `full` 去"避免露缝"。
  - 白色标记当前精确为可见区的 **50% × 50%**，靠白色 path 上的
    `transform="matrix(1.017545 0 0 0.985148 -8.983 8.722)"` 归一得到（原 path 实际是
    49.138% × 50.754% 且垂直中心偏上 1.1 单位）。**若日后手动改 path 数字，这个 matrix 需要重算**。
- `logo_full`（欢迎页/启动页大图）已替换为正式素材 `assets/svg/logo_full.svg`（1024 画布，品牌圆形标记居中，
  图形本体约占画布 31%×31%）。
- 图标规格：24dp 基准，线性图标 1.5px 描边，输出 mdpi(24)/xhdpi(48)/xxhdpi(72)/xxxhdpi(96)。
- 大图规格：logo 按长边 1024 出图（`drawable-nodpi`），运行时由 Coil/Compose 缩放。

### 1.1 颜色规范（重要：图标**不要自己上色**）

24dp 图标在 App 内统一走 `KativaIcon` → Material3 `Icon(tint = …)`，tint 的本质是
`ColorFilter(BlendMode.SrcIn)`：**保留 alpha、丢弃 RGB**。所以源图里画成什么颜色，
到屏幕上都会变成当前主题色（暗色顶栏 `onSurface`、亮色顶栏 `onPrimaryContainer`、阅读器内
`Color.White`、隐私开关激活态 `BrandPrimary` / 亮色顶栏上的 `OnBrand`）。
生成器**不做任何调色**，它只负责 SVG → PNG。

| 素材类型 | 交付要求 |
| --- | --- |
| `kind: "icon"` | **单色、完全不透明、透明底**。建议 `#000000`；不要渐变、不要多色（会被压成一色） |
| 同上 | **禁止整块背景矩形 / 圆角底板**。占位图现在那层 `fill-opacity=0.16` 的方框只是占位示意，正式图若带底板会被 tint 成半透明色块，形状全糊 |
| 同上 | 实心/空心是唯一的状态区分手段（如 `ic_bookmark` vs `ic_bookmark_filled`），不要靠颜色区分 |
| 同上 | `viewBox="0 0 24 24"`，`stroke-width="1.5"`，`stroke-linecap/linejoin="round"` |
| 全彩大图 | `logo_full`、`empty_*`、`error_generic`、`cover_fallback`、`privacy_placeholder`、launcher 系列走 `Image()`，**不经过 tint**，颜色必须自己画进 SVG |

全彩大图特别注意：

- `empty_shelf` / `empty_search` / `empty_network` / `error_generic` 会同时压在深色与浅色主题上，
  **必须透明底**（占位图现在的 `#14141C` 实心底在浅色主题下就是一块黑方块）。
- `privacy_placeholder` 的底色是产品语义（AGENTS.md §6），但它**跟随主题**：暗色 `#3A3A3A`、亮色 `#E9EBEF`
  （`#3A3A3A` 在浅色主题的白色卡片里像一个黑洞）。当前正式素材（`assets/svg/privacy_placeholder.svg`）
  是一张**透明底**的淡色水印，底色由 `PrivacyCoverPlaceholder` 的 `.background(PrivacyFill / PrivacyFillLight)`
  铺在图片层下面；换用该图的任何新位置**必须**同时铺对应背景，否则会露出主题色。

- **禁止**使用 Material Design Icons 原生图标、emoji 或运行时绘制的临时图形作为最终 UI 素材（仅允许出现在占位文件内部文字说明里）。

### 1.2 图标来源与许可

§2.2 / §2.3 的 24dp 线性图标取自 **[Tabler Icons](https://tabler.io/icons)** `v3.51.0`
（MIT License，Copyright © 2020-2026 Paweł Kuna，全文见 `assets/svg/LICENSE.txt`）。
原始 SVG 落在 `assets/svg/<清单ID>.svg`，是**可重新生成的唯一源**，只做两处规范化：

| 上游 | 本项目 | 原因 |
| --- | --- | --- |
| `stroke-width="2"` | `stroke-width="1.5"` | 与 §1.1 的 24dp 线性规格一致（1.5px 描边） |
| `stroke="currentColor"` / `fill="currentColor"` | `#000000` | jsvg 不解析 CSS 变量；颜色由运行期 tint 决定（§1.1） |

`ic_bookmark_filled` 取 Tabler 的 `filled/bookmark`（实心），其余取 `outline/*`。上游图标改动时，
按上表重新下载覆盖同名 SVG 再跑 `generateAssets` 即可，**不需要改代码**。

逐个 ID 与上游图标的对应关系：

| 清单 ID | Tabler 图标 | 清单 ID | Tabler 图标 |
| --- | --- | --- | --- |
| `ic_home` | `home` | `ic_prev_chapter` | `chevrons-left` |
| `ic_library` | `library` | `ic_next_chapter` | `chevrons-right` |
| `ic_compass` | `compass` | `ic_settings_reader` | `adjustments-horizontal` |
| `ic_person` | `user` | `ic_toc` | `list-details` |
| `ic_search` | `search` | `ic_double_page` | `columns-2` |
| `ic_privacy_off` | `eye` | `ic_webtoon` | `arrows-up-down` |
| `ic_privacy_on` | `eye-off` | `ic_brightness` | `brightness` |
| `ic_back` | `arrow-left` | `ic_volume` | `volume` |
| `ic_close` | `x` | `ic_expand_less` | `chevron-up` |
| `ic_more` | `dots` | `ic_expand_more` | `chevron-down` |
| `ic_settings` | `settings` | `ic_sort` | `arrows-sort` |
| `ic_delete` | `trash` | `ic_visibility` | `eye` |
| `ic_edit` | `pencil` | `ic_visibility_off` | `eye-closed` |
| `ic_bookmark` | `bookmark`（outline） | `ic_read` | `circle-check` |
| `ic_bookmark_filled` | `bookmark`（filled） | `ic_unread` | `circle` |
| `ic_favorite` | `heart`（outline） | `ic_favorite_filled` | `heart`（filled） |
| `ic_cover_fallback_mark` | `book-2` | | |

> `ic_visibility` 与 `ic_privacy_off` 共用 `eye`（两者语义都是"可见"，且从不出现在同一屏）；
> `ic_visibility_off` 特意取 `eye-closed` 而非 `eye-off`，让密码显隐与隐私开关的"隐藏"态
> 在视觉上可区分，不会看成同一个按钮。

插画类（`cover_fallback`、`empty_*`、`error_generic`、launcher 系列）中的 launcher 三项来自
`assigns/logo.svg`；其余已于 2026-11-09 全部替换为正式素材（含此前的 `logo_full` / `privacy_placeholder`）。

---

## 2. 清单

状态：`占位`（开发期，待正式素材）/ `正式`（已替换，可验收）。

### 2.1 品牌与启动

| ID | 用途 | 规格 | PNG 输出 | 状态 |
| --- | --- | --- | --- | --- |
| `logo_full` | 欢迎页/启动页居中 logo | 长边 1024，透明底，**全彩** | `drawable-nodpi/logo_full.png` | 正式（`assets/svg/logo_full.svg`） |
| `ic_launcher_foreground` | 自适应图标前景层 | 108dp 画布，safe（72dp 可见区） | `mipmap-*/ic_launcher_foreground.png` | 正式（`assigns/logo.svg`） |
| `ic_launcher` | 传统图标（API < 26） | 48dp，**全彩** | `mipmap-*/ic_launcher.png` | 正式（`assigns/logo.svg`） |
| `ic_launcher_round` | 圆形图标 | 48dp，**全彩** | `mipmap-*/ic_launcher_round.png` | 正式（`assigns/logo.svg`） |

> 应用图标底色烘在图内（`assigns/logo.svg` 自带满幅 `#44D39D` 底）。自适应图标的
> `<background>` 用 `@color/ic_launcher_background`（同色 `#44D39D`），只用于被遮罩裁掉的边缘区域，
> 保证不露出与图内不一致的色边。
> 旧文档里的 `logo_mark` / `launcher_background` 从未存在于清单，已按实际情况更正。

### 2.2 导航与顶栏图标（24dp 线性）

| ID | 用途 | PNG 输出 | 状态 |
| --- | --- | --- | --- |
| `ic_home` | 底部导航 · 首页 | `drawable-xhdpi/ic_home.png` | 正式（Tabler） |
| `ic_library` | 底部导航 · 书架 | `drawable-xhdpi/ic_library.png` | 正式（Tabler） |
| `ic_compass` | 底部导航 · 发现 | `drawable-xhdpi/ic_compass.png` | 正式（Tabler） |
| `ic_person` | 底部导航 · 我的 | `drawable-xhdpi/ic_person.png` | 正式（Tabler） |
| `ic_search` | 顶栏搜索 | `drawable-xhdpi/ic_search.png` | 正式（Tabler） |
| `ic_privacy_off` | 隐私模式关闭态 | `drawable-xhdpi/ic_privacy_off.png` | 正式（Tabler） |
| `ic_privacy_on` | 隐私模式开启态（激活） | `drawable-xhdpi/ic_privacy_on.png` | 正式（Tabler） |
| `ic_back` | 返回 | `drawable-xhdpi/ic_back.png` | 正式（Tabler） |
| `ic_close` | 关闭 / 清空输入 | `drawable-xhdpi/ic_close.png` | 正式（Tabler） |
| `ic_more` | 更多（⋯） | `drawable-xhdpi/ic_more.png` | 正式（Tabler） |
| `ic_settings` | 设置入口 | `drawable-xhdpi/ic_settings.png` | 正式（Tabler） |
| `ic_delete` | 删除条目 | `drawable-xhdpi/ic_delete.png` | 正式（Tabler） |
| `ic_edit` | 编辑服务器 | `drawable-xhdpi/ic_edit.png` | 正式（Tabler） |
| `ic_sort` | 排序入口 | `drawable-xhdpi/ic_sort.png` | 正式（Tabler） |
| `ic_expand_less` / `ic_expand_more` | 卷折叠箭头 | `drawable-xhdpi/ic_expand_*.png` | 正式（Tabler） |
| `ic_visibility` / `ic_visibility_off` | 密码显隐 | `drawable-xhdpi/ic_visibility*.png` | 正式（Tabler） |
| `ic_read` / `ic_unread` | 已读/未读标记 | `drawable-xhdpi/ic_*read.png` | 正式（Tabler） |
| `ic_favorite` / `ic_favorite_filled` | 想读（收藏）未选中 / 选中 | `drawable-xhdpi/ic_favorite*.png` | 正式（Tabler） |
| `ic_cover_fallback_mark` | 封面缺失时的角标 | `drawable-xhdpi/ic_cover_fallback_mark.png` | 正式（Tabler） |

### 2.3 阅读器图标（24dp 线性）

| ID | 用途 | PNG 输出 | 状态 |
| --- | --- | --- | --- |
| `ic_bookmark` | 书签（未选中，**线性空心**） | `drawable-xhdpi/ic_bookmark.png` | 正式（Tabler） |
| `ic_bookmark_filled` | 书签（已选中，**实心**） | `drawable-xhdpi/ic_bookmark_filled.png` | 正式（Tabler） |
| `ic_prev_chapter` / `ic_next_chapter` | 章节切换（阅读器底栏） | `drawable-xhdpi/ic_*_chapter.png` | 正式（Tabler） |
| `ic_settings_reader` | 阅读设置（阅读器顶栏） | `drawable-xhdpi/ic_settings_reader.png` | 正式（Tabler） |
| `ic_toc` | 目录（系列详情「Volumes & chapters」标题） | `drawable-xhdpi/ic_toc.png` | 正式（Tabler） |
| `ic_double_page` | 双页模式（阅读设置分组标题） | `drawable-xhdpi/ic_double_page.png` | 正式（Tabler） |
| `ic_webtoon` | Webtoon 模式（阅读设置分组标题） | `drawable-xhdpi/ic_webtoon.png` | 正式（Tabler） |
| `ic_brightness` | 亮度覆盖（阅读设置分组标题） | `drawable-xhdpi/ic_brightness.png` | 正式（Tabler） |
| `ic_volume` | 音量键翻页（阅读设置开关行） | `drawable-xhdpi/ic_volume.png` | 正式（Tabler） |

### 2.4 占位与空态

| ID | 用途 | 规格 | PNG 输出 | 状态 |
| --- | --- | --- | --- | --- |
| `privacy_placeholder` | **隐私模式封面占位**（`PrivacyFill` 底 + 淡 logo 水印）→ `CoverImage` 隐私分支 | 2:3，500×750 | `drawable-nodpi/privacy_placeholder.png` | 正式（`assets/svg/privacy_placeholder.svg`） |
| `cover_fallback` | 无封面时的通用封面 → `CoverImage` 空白 URL 分支 | 2:3 | `drawable-nodpi/cover_fallback.png` | 正式（`assets/svg/cover_fallback.svg`） |
| `empty_shelf` | 书架/流列表空态（`EmptyState.illustration`） | 512×512 | `drawable-nodpi/empty_shelf.png` | 正式（`assets/svg/empty_shelf.svg`） |
| `empty_search` | 搜索无结果（`EmptyState.illustration`） | 512×512 | `drawable-nodpi/empty_search.png` | 正式（`assets/svg/empty_search.svg`） |
| `empty_network` | 无网络/超时/DNS/明文拦截（`KativaError.illustration`） | 512×512 | `drawable-nodpi/empty_network.png` | 正式（`assets/svg/empty_network.svg`） |
| `error_generic` | 通用错误（`ErrorState` 默认插图） | 256×256 | `drawable-nodpi/error_generic.png` | 正式（`assets/svg/error_generic.svg`） |

### 2.5 格式与徽标（可选，M2 起）

> 尚未实现、也**未进入 `assets/manifest.json`**。启用时须先在清单追加条目并跑生成器，
> 否则 `checkAssetsRegistered` 会把未登记素材判失败。

| ID | 用途 | 状态 |
| --- | --- | --- |
| `badge_kavita_plus` | K+ 徽标 | 未建（未登记） |
| `format_manga` / `format_comic` / `format_book` / `format_pdf` | 格式小标 | 未建（未登记） |

---

## 3. 工作流程

### 开发期（占位）

1. 新功能需要新图片 → **先在 `assets/manifest.json` 追加一条**（id/kind/尺寸/code），并在本表登记。
2. 运行 `./gradlew :app:generateAssets`，脚本按 `kind` 自动生成占位 SVG → PNG：
   - `icon` → 24dp × 5 密度（mdpi…xxxhdpi）
   - `illustration` / `cover` / `privacy` → `drawable-nodpi` 单张
   - `launcher-foreground` / `launcher` → `mipmap-*`（108dp 画布 + 72dp 安全区 / 48dp）
3. 代码只引用资源 ID（`R.drawable.ic_privacy_on`），不直接引用文件路径。

### 正式素材替换（一次性批量）

1. 维护者提供正式 SVG（命名 = 清单 ID，如 `ic_privacy_on.svg`），放进 `assets/svg/`（或 `assigns/`，规则相同）。
2. 生成器**已支持**按 ID 优先取正式 SVG，无需改代码（优先级见 §1）。
3. 跑 `./gradlew :app:generateAssets` 覆盖 PNG；确认输出里该条目的来源是 `assets/svg/...` 而不是
   `generated placeholder`，再逐条把本表状态改为 `正式`；提交时截图确认。
4. 全部为 `正式` 后删除占位生成物（`assets/generated/` 已被 gitignore，无需清理）。

> 图标来源与规范化规则见 §1.2（Tabler v3.51.0）。重新抓取同名 SVG 覆盖即可，**不要**手改 path 数字。

### 验收

- `docs/ASSETS.md` 中所有条目状态 = `正式`（M3 门禁）。
- `./gradlew :app:checkAssetsRegistered` 通过（`res/` 下每个 PNG 都能在清单里找到）。
- 隐私模式开关两态截图回归（占位图一致）。

---

## 4. 变更记录

| 日期 | 变更 |
| --- | --- |
| 2026-09-02 | 初版清单（占位阶段） |
| 2026-11-05 | M0 落地：清单迁到 `assets/manifest.json`（43 项）；生成器改为 `tools/assets` 子工程；应用图标改用 `assigns/kavita.svg` |
| 2026-11-06 | 清理零引用素材：移除 `logo_wordmark` 与 8 个无消费方图标（`ic_add`/`ic_arrow_right`/`ic_check`/`ic_lock`/`ic_play`/`ic_refresh`/`ic_server`/`ic_swap`），清单 50 → 41 项、res PNG 218 → 177；`KativaIcons.Add` 一并删除 |
| 2026-11-06 | **素材接线**：全部保留素材均有消费方——`CoverImage` 改用 `privacy_placeholder`/`cover_fallback` 真实图；`EmptyState`/`ErrorState` 新增 `illustration` 参数并接 `empty_shelf`/`empty_search`/`empty_network`/`error_generic`；阅读器接入 `ic_prev_chapter`/`ic_next_chapter`/`ic_settings_reader`/`ic_toc`/`ic_double_page`/`ic_webtoon`/`ic_brightness`/`ic_volume`；补齐音量键翻页功能（`VolumeKeyPaging` + 设置开关 + 单测）。同时修正 2.4 表中不存在的 `empty_offline`（实现用的是 `empty_network`） |
| 2026-11-06 | 修复「正式 SVG 放了不生效」：生成器改为逐条按 ID 解析（`assets/svg/<id>.svg` → `assigns/<id>.svg` → `assigns/kavita.svg` → 占位），并打印每条来源与仍占位清单；新增 §1.1 颜色规范（图标单色透明底、由运行期 tint 上色；全彩大图必须透明底） |
| 2026-11-06 | **应用改名 Kavita Comic + 品牌图标**：`app_name` 四语言改为 `Kavita Comic`；launcher 三项改用 `assigns/logo.svg`。新增清单字段 `bleed`（`launcher-foreground` 专用，默认 `safe`；`full` = 108dp 铺满——**本项目不使用**，因为只有中间 72dp 可见，铺满会把标记放大 1.5 倍）；新增 `mipmap-anydpi-v26/ic_launcher{,_round}.xml` 自适应图标与 `@color/ic_launcher_background`（同色，仅填被裁掉的边缘）。`checkAssetsRegistered` 的白名单加入 `logo.svg`（品牌源被多个条目共享，本就不是清单 ID） |
| 2026-11-06 | 图标白色标记归一到 50%×50%：原 path 实际 49.138% × 50.754% 且垂直中心偏上，非正方形，靠缩放无法精确命中，故在白色 path 上加 `matrix(1.017545 0 0 0.985148 -8.983 8.722)`（`tx = 512 - sx·cx` 公式保证中心落在 viewBox 中心）。实测 192px PNG = 144/288 = 50%，设备端 168px 圆盘内白色 84px = 50% |
| 2026-11-09 | **31 个 24dp 图标替换为 Tabler Icons v3.51.0**（MIT）：源 SVG 落在 `assets/svg/<清单ID>.svg`，生成器只做 `stroke-width 2 → 1.5`、`currentColor → #000000` 两处规范化；清单状态 31 项 → `final`。插画类（`logo_full`/`privacy_placeholder`/`cover_fallback`/`empty_*`/`error_generic`）与 launcher 系列**未改动**，由维护者另行提供。新增 §1.2 记录来源、映射表与许可 |
| 2026-11-09 | **`logo_full` / `privacy_placeholder` 替换为正式素材**：源文件 `assets/svg/logo_full.svg`（1024 透明底，品牌圆标居中，图形本体约 31%×31%）与 `assets/svg/privacy_placeholder.svg`（500×750 透明底淡色水印，主体约 31.6%×21.1%），清单状态 → `final`。`privacy_placeholder` 的深灰底仍由 `PrivacyCoverPlaceholder` 的 `PrivacyFill` 背景负责（AGENTS §6 语义不变，且图片透明底可同时适配深/浅主题）。剩余占位：`cover_fallback`、`empty_*`、`error_generic` |
