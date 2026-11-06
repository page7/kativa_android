package com.kativa.tools.assets

import com.github.weisj.jsvg.SVGDocument
import com.github.weisj.jsvg.parser.SVGLoader
import com.github.weisj.jsvg.view.ViewBox
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Asset pipeline: reads assets/manifest.json, renders placeholder (or provided) SVG sources
 * into Android density buckets. See docs/ASSETS.md.
 *
 * Source resolution per manifest entry, in priority order:
 *   1. `assets/svg/<id>.svg`   — the delivered, final artwork
 *   2. `assigns/<id>.svg`      — maintainer drop with the same accepted naming
 *   3. `assigns/kavita.svg`    — legacy source for `launcher` / `launcher-foreground` only
 *   4. generated placeholder   — always available, so every entry resolves to something
 *
 * Usage (from repo root):
 *   ./gradlew :app:generateAssets
 */
fun main(args: Array<String>) {
    val root = File(args.firstOrNull() ?: ".").absoluteFile
    val manifestFile = File(root, "assets/manifest.json")
    require(manifestFile.isFile) { "manifest not found: $manifestFile" }

    val manifest = ManifestJson.parse(manifestFile.readText())
    val resDir = File(root, "app/src/main/res")
    val generatedSvgDir = File(root, "assets/generated/svg").apply { mkdirs() }

    var written = 0
    val placeholders = mutableListOf<String>()
    for (entry in manifest.entries) {
        val source = resolveSource(root, entry)
        if (source.file == null) placeholders += entry.id

        File(generatedSvgDir, "${entry.id}.svg").writeText(source.svg)

        val doc = loadSvg(source.svg, entry.id, File(generatedSvgDir, "${entry.id}.svg"))
        written += when (entry.kind) {
            "icon" -> renderIcon(doc, entry, resDir)
            "launcher-foreground" -> renderLauncherForeground(doc, entry, resDir)
            "launcher" -> renderLauncher(doc, entry, resDir)
            else -> renderNodpi(doc, entry, resDir)
        }

        val origin = source.file?.let { it.relativeTo(root).invariantSeparatorsPath } ?: "generated placeholder"
        println("  ${entry.id.padEnd(24)} ${entry.kind.padEnd(18)} <- $origin")
    }

    val formal = manifest.entries.size - placeholders.size
    println("Generated ${manifest.entries.size} entries ($written PNG files): $formal from source SVG, ${placeholders.size} placeholder.")
    if (placeholders.isNotEmpty()) {
        println("Still placeholder (drop assets/svg/<id>.svg to replace): ${placeholders.joinToString(", ")}")
    }
}

internal data class SvgSource(val svg: String, val file: File?)

/**
 * Picks the artwork for one entry. `file == null` means "no delivered SVG, use the placeholder",
 * which the caller reports so the placeholder backlog stays visible on every run.
 *
 * `internal` so the priority order is unit-tested instead of assumed — the previous version claimed
 * to honour `assets/svg/<id>.svg` in the docs but always rendered placeholders.
 */
internal fun resolveSource(root: File, entry: ManifestEntry): SvgSource {
    val candidates = buildList {
        add(File(root, "assets/svg/${entry.id}.svg"))
        add(File(root, "assigns/${entry.id}.svg"))
        if (entry.kind == "launcher" || entry.kind == "launcher-foreground") {
            // Brand mark first, then the older Kareadita-style artwork as a compatibility fallback.
            add(File(root, "assigns/logo.svg"))
            add(File(root, "assigns/kavita.svg"))
        }
    }
    candidates.firstOrNull { it.isFile }?.let { file ->
        val text = file.readText().removePrefix("\uFEFF").trim()
        require(text.isNotEmpty()) { "SVG source is empty: ${file.absolutePath}" }
        return SvgSource(text, file)
    }
    val placeholder = when (entry.kind) {
        "privacy" -> PlaceholderSvg.privacy(entry)
        "cover" -> PlaceholderSvg.cover(entry)
        "illustration" -> PlaceholderSvg.forEntry(entry)
        else -> PlaceholderSvg.icon(entry)
    }
    return SvgSource(placeholder, null)
}

private fun loadSvg(svg: String, id: String, sourceFile: File): SVGDocument {
    val loader = SVGLoader()
    val doc = svg.byteInputStream().use { stream ->
        loader.load(stream, sourceFile.toURI(), com.github.weisj.jsvg.parser.LoaderContext.createDefault())
    }
    requireNotNull(doc) { "Failed to parse generated SVG for $id" }
    return doc
}

private val ICON_DENSITIES = listOf("mdpi" to 1.0f, "hdpi" to 1.5f, "xhdpi" to 2.0f, "xxhdpi" to 3.0f, "xxxhdpi" to 4.0f)

private fun renderIcon(doc: SVGDocument, entry: ManifestEntry, resDir: File): Int {
    var n = 0
    for ((bucket, scale) in ICON_DENSITIES) {
        val px = (24 * scale).toInt()
        val dir = File(resDir, "drawable-$bucket").apply { mkdirs() }
        writePng(doc, px, px, File(dir, "${entry.id}.png"))
        n++
    }
    return n
}

private fun renderDensity(doc: SVGDocument, id: String, dp: Int, resDir: File, prefix: String): Int {
    var n = 0
    for ((bucket, scale) in ICON_DENSITIES) {
        val px = (dp * scale).toInt()
        val dir = File(resDir, "$prefix-$bucket").apply { mkdirs() }
        writePng(doc, px, px, File(dir, "$id.png"))
        n++
    }
    return n
}

/**
 * Adaptive icon foreground: 108dp canvas.
 *
 * `bleed = "safe"` (default) centres the artwork inside the 72dp safe zone with a transparent
 * surround — the classic "logo on a background colour" setup. `bleed = "full"` draws edge to edge
 * for artwork that brings its own full-bleed background (the brand mark in `assigns/logo.svg`),
 * because shrinking a full-bleed square into the safe zone makes the launcher mask cut an
 * obviously smaller square out of it.
 */
private fun renderLauncherForeground(doc: SVGDocument, entry: ManifestEntry, resDir: File): Int {
    var n = 0
    for ((bucket, scale) in ICON_DENSITIES) {
        val canvas = (108 * scale).toInt()
        val dir = File(resDir, "mipmap-$bucket").apply { mkdirs() }
        if (entry.bleed == "full") {
            writePng(doc, canvas, canvas, File(dir, "${entry.id}.png"))
        } else {
            val art = (72 * scale).toInt()
            writePngCentered(doc, canvas, art, File(dir, "${entry.id}.png"))
        }
        n++
    }
    return n
}

/** Legacy launcher bitmap (pre-26): full artwork at 48dp equivalent. */
private fun renderLauncher(doc: SVGDocument, entry: ManifestEntry, resDir: File): Int {
    var n = 0
    for ((bucket, scale) in ICON_DENSITIES) {
        val px = (48 * scale).toInt()
        val dir = File(resDir, "mipmap-$bucket").apply { mkdirs() }
        writePng(doc, px, px, File(dir, "${entry.id}.png"))
        n++
    }
    return n
}

private fun renderNodpi(doc: SVGDocument, entry: ManifestEntry, resDir: File): Int {
    val dir = File(resDir, "drawable-nodpi").apply { mkdirs() }
    writePng(doc, entry.width, entry.height, File(dir, "${entry.id}.png"))
    return 1
}

private fun writePng(doc: SVGDocument, width: Int, height: Int, out: File) {
    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
    val g = image.createGraphics()
    try {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g.color = Color(0, 0, 0, 0)
        g.fillRect(0, 0, width, height)
        doc.render(null, g, ViewBox(0f, 0f, width.toFloat(), height.toFloat()))
    } finally {
        g.dispose()
    }
    out.parentFile?.mkdirs()
    ImageIO.write(image, "png", out)
}

private fun writePngCentered(doc: SVGDocument, canvas: Int, artSize: Int, out: File) {
    val image = BufferedImage(canvas, canvas, BufferedImage.TYPE_INT_ARGB)
    val g = image.createGraphics()
    try {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g.color = Color(0, 0, 0, 0)
        g.fillRect(0, 0, canvas, canvas)
        val offset = (canvas - artSize) / 2
        g.translate(offset.toDouble(), offset.toDouble())
        doc.render(null, g, ViewBox(0f, 0f, artSize.toFloat(), artSize.toFloat()))
    } finally {
        g.dispose()
    }
    out.parentFile?.mkdirs()
    ImageIO.write(image, "png", out)
}

object PlaceholderSvg {
    private const val BRAND = "#4AC694"
    private const val DEEP = "#2D7A56"
    private const val PRIVACY = "#3A3A3A"

    fun icon(entry: ManifestEntry): String {
        val label = entry.code.replace("\n", " ").take(2)
        return """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="24" height="24">
              <rect x="2.5" y="2.5" width="19" height="19" rx="5" fill="$BRAND" fill-opacity="0.16" stroke="$BRAND" stroke-opacity="0.65" stroke-width="1.2"/>
              <text x="12" y="15.4" text-anchor="middle" font-family="SansSerif" font-size="7.6" font-weight="700" fill="$BRAND">$label</text>
            </svg>
        """.trimIndent()
    }

    fun privacy(entry: ManifestEntry): String = """
        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${entry.width} ${entry.height}" width="${entry.width}" height="${entry.height}">
          <rect width="${entry.width}" height="${entry.height}" fill="$PRIVACY"/>
          <g fill="$BRAND" fill-opacity="0.10">
            <circle cx="${entry.width / 2}" cy="${entry.height * 0.42f}" r="${entry.width * 0.16f}"/>
            <rect x="${entry.width * 0.5f - entry.width * 0.24f}" y="${entry.height * 0.58f}"
                  width="${entry.width * 0.48f}" height="${entry.width * 0.10f}" rx="${entry.width * 0.05f}"/>
          </g>
        </svg>
    """.trimIndent()

    fun cover(entry: ManifestEntry): String {
        val label = entry.code.ifBlank { "NO COVER" }.replace("\n", " ")
        return """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${entry.width} ${entry.height}" width="${entry.width}" height="${entry.height}">
              <rect width="${entry.width}" height="${entry.height}" fill="#20202A"/>
              <rect x="1" y="1" width="${entry.width - 2}" height="${entry.height - 2}" rx="16" fill="none" stroke="$BRAND" stroke-opacity="0.45" stroke-width="4"/>
              <text x="${entry.width / 2}" y="${entry.height / 2}" text-anchor="middle" font-family="SansSerif"
                    font-size="${entry.height / 12}" font-weight="600" fill="#7C7C88">$label</text>
            </svg>
        """.trimIndent()
    }

    fun forEntry(entry: ManifestEntry): String {
        val w = entry.width.toFloat()
        val h = entry.height.toFloat()
        val label = entry.code.ifBlank { entry.id.uppercase() }
        return """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 $w $h" width="$w" height="$h">
              <rect width="$w" height="$h" fill="#14141C"/>
              <circle cx="${w / 2}" cy="${h * 0.40f}" r="${minOf(w, h) * 0.16f}" fill="$BRAND" fill-opacity="0.55"/>
              <rect x="${w * 0.26f}" y="${h * 0.60f}" width="${w * 0.48f}" height="${maxOf(4f, h * 0.035f)}" rx="${h * 0.018f}" fill="$DEEP"/>
              <text x="${w / 2}" y="${h * 0.78f}" text-anchor="middle" font-family="SansSerif"
                    font-size="${minOf(w, h) * 0.07f}" font-weight="600" fill="#9AA0AA">$label</text>
            </svg>
        """.trimIndent()
    }
}

