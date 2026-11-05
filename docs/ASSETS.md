# 素材清单（Assets）

> 规则：App 内**所有**图片与按钮图标都必须来自本清单登记的素材；素材格式为 **SVG 源文件 → 生成 PNG**。
> 开发期使用**自动生成的占位 SVG/PNG**；正式素材完成后由维护者通知，届时按本清单**批量替换**。
> 新增素材的流程见 §3；CI 会检查"工程内图片资源 ⊆ 清单登记项"。

---

## 1. 管线与目录约定

```
assigns/                     维护者提供的源素材（当前已有 kavita.svg）
assets/svg/                  正式 SVG 源（最终交付物，保留可重新生成）
assets/svg/_placeholders/    自动生成的占位 SVG（开发期）
assets/generated/            脚本输出 PNG（占位或正式，按来源生成）
app/src/main/res/
  drawable-nodpi/            大图（logo、欢迎页、空态插画）→ PNG
  drawable-mdpi|xhdpi|xxhdpi|xxxhdpi/  图标 → PNG（按密度出图，基准 1x = 24dp）
  drawable/                  小尺寸单密度 PNG（隐私占位等纯色图，可接受）
```

生成命令（占位期）：

```bash
./gradlew :app:generateAssets           # 从 assets/manifest.json 生成全部 PNG
./gradlew :app:checkAssetsRegistered    # 校验 res/ 下没有未登记素材
```

实现：`tools/assets`（Kotlin/JVM 子工程，jsvg 2.2.0 渲染）。清单源为 `assets/manifest.json`，生成的 SVG 落在
`assets/generated/svg/`，PNG 直接写入 `app/src/main/res/drawable-*/` 与 `mipmap-*/`。

- 占位 SVG 由脚本按清单条目自动生成（纯色矩形 + 文字标识，如 `ic_privacy`），保证每个条目**永远有图可用**。
- 维护者提供的正式素材放进 `assigns/` → 复制到 `assets/svg/` 同名文件 → 生成 PNG → 更新清单状态。
  **当前已有**：`assigns/kavita.svg`（64×64 圆形底 `#4AC694` + `#424C72` 图形，22 个 path，Illustrator 导出）→ 用于 `logo_mark`、`launcher_foreground`；`logo_full`/`logo_wordmark` 待维护者确认是否由它放大或另给。
- 图标规格：24dp 基准，线性图标 1.5px 描边，输出 mdpi(24)/xhdpi(48)/xxhdpi(72)/xxxhdpi(96)。
- 大图规格：logo 按长边 1024 出图（`drawable-nodpi`），运行时由 Coil/Compose 缩放。
- **禁止**使用 Material Design Icons 原生图标、emoji 或运行时绘制的临时图形作为最终 UI 素材（仅允许出现在占位文件内部文字说明里）。

---

## 2. 清单

状态：`占位`（开发期，待正式素材）/ `正式`（已替换，可验收）。

### 2.1 品牌与启动

| ID | 用途 | 规格 | PNG 输出 | 状态 |
| --- | --- | --- | --- | --- |
| `logo_full` | 欢迎页居中 logo（P01A） | 长边 1024，透明底 | `drawable-nodpi/logo_full.png` | 占位 |
| `logo_mark` | 应用图标（adaptive foreground） | 108dp safe zone | `mipmap-anydpi-v26/ic_launcher_foreground.png` | 源已有（`assigns/kavita.svg`，待生成 PNG） |
| `logo_wordmark` | Splash 顶部字标 | 长边 512 | `drawable-nodpi/logo_wordmark.png` | 占位（待确认是否由 kavita.svg 派生） |
| `launcher_background` | 应用图标背景 | 108dp 纯色 `#4AC694` | `drawable/ic_launcher_background.png` | 占位 |

### 2.2 导航与顶栏图标（24dp 线性）

| ID | 用途 | PNG 输出 | 状态 |
| --- | --- | --- | --- |
| `ic_home` | 底部导航 · 首页 | `drawable-xhdpi/ic_home.png` | 占位 |
| `ic_library` | 底部导航 · 书架 | `drawable-xhdpi/ic_library.png` | 占位 |
| `ic_compass` | 底部导航 · 发现 | `drawable-xhdpi/ic_compass.png` | 占位 |
| `ic_person` | 底部导航 · 我的 | `drawable-xhdpi/ic_person.png` | 占位 |
| `ic_search` | 顶栏搜索 | `drawable-xhdpi/ic_search.png` | 占位 |
| `ic_privacy_off` | 隐私模式关闭态 | `drawable-xhdpi/ic_privacy_off.png` | 占位 |
| `ic_privacy_on` | 隐私模式开启态（激活） | `drawable-xhdpi/ic_privacy_on.png` | 占位 |
| `ic_back` | 返回 | `drawable-xhdpi/ic_back.png` | 占位 |
| `ic_more` | 更多（⋯） | `drawable-xhdpi/ic_more.png` | 占位 |
| `ic_settings` | 设置入口 | `drawable-xhdpi/ic_settings.png` | 占位 |
| `ic_add` | 添加服务器（+） | `drawable-xhdpi/ic_add.png` | 占位 |
| `ic_delete` | 删除条目 | `drawable-xhdpi/ic_delete.png` | 占位 |
| `ic_edit` | 编辑服务器 | `drawable-xhdpi/ic_edit.png` | 占位 |
| `ic_server` | 服务器卡片图标 | `drawable-xhdpi/ic_server.png` | 占位 |
| `ic_swap` | 切换服务器 | `drawable-xhdpi/ic_swap.png` | 占位 |

### 2.3 阅读器图标（24dp 线性）

| ID | 用途 | PNG 输出 | 状态 |
| --- | --- | --- | --- |
| `ic_bookmark` / `ic_bookmark_filled` | 书签 | `drawable-xhdpi/ic_bookmark*.png` | 占位 |
| `ic_prev_chapter` / `ic_next_chapter` | 章节切换 | `drawable-xhdpi/ic_*_chapter.png` | 占位 |
| `ic_settings_reader` | 阅读设置 | `drawable-xhdpi/ic_settings_reader.png` | 占位 |
| `ic_toc` | 目录（EPUB） | `drawable-xhdpi/ic_toc.png` | 占位 |
| `ic_double_page` | 双页模式 | `drawable-xhdpi/ic_double_page.png` | 占位 |
| `ic_webtoon` | Webtoon 模式 | `drawable-xhdpi/ic_webtoon.png` | 占位 |
| `ic_brightness` | 亮度覆盖 | `drawable-xhdpi/ic_brightness.png` | 占位 |
| `ic_volume` | 音量键翻页 | `drawable-xhdpi/ic_volume.png` | 占位 |

### 2.4 占位与空态

| ID | 用途 | 规格 | PNG 输出 | 状态 |
| --- | --- | --- | --- | --- |
| `privacy_placeholder` | **隐私模式封面占位**（`#3A3A3A` + 淡 logo 水印） | 2:3，500×750 | `drawable-nodpi/privacy_placeholder.png` | 占位 |
| `cover_fallback` | 无封面时的通用封面 | 2:3 | `drawable-nodpi/cover_fallback.png` | 占位 |
| `empty_shelf` | 书架空态 | 512×512 | `drawable-nodpi/empty_shelf.png` | 占位 |
| `empty_search` | 搜索无结果 | 512×512 | `drawable-nodpi/empty_search.png` | 占位 |
| `empty_offline` | 无网络空态 | 512×512 | `drawable-nodpi/empty_offline.png` | 占位 |
| `error_generic` | 通用错误 | 256×256 | `drawable-nodpi/error_generic.png` | 占位 |

### 2.5 格式与徽标（可选，M2 起）

| ID | 用途 | 状态 |
| --- | --- | --- |
| `badge_kavita_plus` | K+ 徽标 | 占位 |
| `format_manga` / `format_comic` / `format_book` / `format_pdf` | 格式小标 | 占位 |

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

1. 维护者提供正式 SVG（命名 = 清单 ID，如 `ic_privacy_on.svg`），放进 `assets/svg/` 或 `assigns/`。
2. 让生成器优先取同名正式 SVG（当前 `launcher` 类走 `assigns/kavita.svg`，其余仍是脚本占位）。
3. 跑 `./gradlew :app:generateAssets` 覆盖 PNG；逐条把本表状态改为 `正式`；提交时截图确认。
4. 全部为 `正式` 后删除占位生成物。

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
