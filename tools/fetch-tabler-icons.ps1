# Fetches the 24dp app icons from Tabler Icons and writes the normalised sources into
# assets/svg/<manifest-id>.svg, where the asset pipeline picks them up (docs/ASSETS.md §1.2).
#
#   powershell -ExecutionPolicy Bypass -File tools/fetch-tabler-icons.ps1
#   ./gradlew :app:generateAssets
#
# Normalisation (must stay in sync with docs/ASSETS.md §1.2):
#   * stroke-width 2 -> 1.5  (the project's 24dp line-icon spec)
#   * currentColor  -> #000000  (jsvg cannot resolve CSS variables; colour comes from runtime tint)
#
# Only the 24dp icons are handled here. Illustrations / launch artwork are maintained separately.
param(
    [string]$Version = '3.51.0',
    [string]$RepoRoot = (Split-Path -Parent $PSScriptRoot)
)

$ErrorActionPreference = 'Stop'
$outDir = Join-Path $RepoRoot 'assets\svg'
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

# manifest id -> tabler icon name
$map = [ordered]@{
    'ic_home'                = 'home'
    'ic_library'             = 'library'
    'ic_compass'             = 'compass'
    'ic_person'              = 'user'
    'ic_search'              = 'search'
    'ic_privacy_off'         = 'eye'
    'ic_privacy_on'          = 'eye-off'
    'ic_back'                = 'arrow-left'
    'ic_close'               = 'x'
    'ic_more'                = 'dots'
    'ic_settings'            = 'settings'
    'ic_delete'              = 'trash'
    'ic_edit'                = 'pencil'
    'ic_bookmark'            = 'bookmark'
    'ic_bookmark_filled'     = 'bookmark'
    'ic_prev_chapter'        = 'chevrons-left'
    'ic_next_chapter'        = 'chevrons-right'
    'ic_settings_reader'     = 'adjustments-horizontal'
    'ic_toc'                 = 'list-details'
    'ic_double_page'         = 'columns-2'
    'ic_webtoon'             = 'arrows-up-down'
    'ic_brightness'          = 'brightness'
    'ic_volume'              = 'volume'
    'ic_expand_less'         = 'chevron-up'
    'ic_expand_more'         = 'chevron-down'
    'ic_sort'                = 'arrows-sort'
    'ic_visibility'          = 'eye'
    # Deliberately distinct from ic_privacy_on (eye-off): password reveal vs. privacy mode.
    'ic_visibility_off'      = 'eye-closed'
    'ic_read'                = 'circle-check'
    'ic_unread'              = 'circle'
    'ic_cover_fallback_mark' = 'book-2'
}

# Solid variants only where the state is expressed by fill, never by colour (docs/ASSETS.md §1.1).
$filled = @('ic_bookmark_filled')

$license = Invoke-WebRequest -UseBasicParsing -Uri "https://cdn.jsdelivr.net/npm/@tabler/icons@$Version/LICENSE"
[System.IO.File]::WriteAllText(
    (Join-Path $outDir 'LICENSE.txt'),
    "Tabler Icons - https://tabler.io/icons`nVersion: $Version`nUsed for: every 24dp icon in this folder (docs/ASSETS.md 1.2)`n`n" + $license.Content,
    (New-Object System.Text.UTF8Encoding($false))
)

foreach ($id in $map.Keys) {
    $name = $map[$id]
    $variant = if ($filled -contains $id) { 'filled' } else { 'outline' }
    $url = "https://cdn.jsdelivr.net/npm/@tabler/icons@$Version/icons/$variant/$name.svg"
    $svg = (Invoke-WebRequest -UseBasicParsing -Uri $url).Content

    $svg = [regex]::Replace($svg, 'stroke-width="2"', 'stroke-width="1.5"')
    $svg = [regex]::Replace($svg, 'currentColor', '#000000')
    $svg = [regex]::Replace($svg, '\s+class="[^"]*"', '')

    [System.IO.File]::WriteAllText(
        (Join-Path $outDir "$id.svg"),
        $svg.Trim(),
        (New-Object System.Text.UTF8Encoding($false))
    )
    Write-Host ("{0,-24} <- tabler/{1}/{2}" -f $id, $variant, $name)
}

Write-Host "Done. Now run: ./gradlew :app:generateAssets"
