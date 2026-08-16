# verify-lang.ps1
# 校验 zh_cn.json / en_us.json 的顶层 key 集合一致（lang 双文件同步约束，见 AGENTS.md）。
# CI 与本地均可运行；任一文件缺失 / JSON 非法 / key 集合不一致时以非零码退出。
#
# 用法: pwsh -File ./scripts/verify-lang.ps1

$ErrorActionPreference = "Stop"

$langDir = Join-Path $PSScriptRoot "..\rtsbuilding-main\src\main\resources\assets\rtsbuilding\lang"
$zhPath = Join-Path $langDir "zh_cn.json"
$enPath = Join-Path $langDir "en_us.json"

$failures = @()

# ── 文件存在性 ──
foreach ($p in @($zhPath, $enPath)) {
    if (-not (Test-Path $p)) {
        $failures += "缺少 lang 文件: $p"
    }
}

if ($failures.Count -gt 0) {
    $failures | ForEach-Object { Write-Error $_ }
    exit 1
}

# ── JSON 解析 + key 提取 ──
function Get-LangKeys($path) {
    $raw = Get-Content $path -Raw -Encoding UTF8
    try {
        $json = $raw | ConvertFrom-Json -ErrorAction Stop
    } catch {
        throw "JSON 解析失败: $path → $($_.Exception.Message)"
    }
    return @($json.PSObject.Properties.Name)
}

$zhKeys = Get-LangKeys $zhPath
$enKeys = Get-LangKeys $enPath

# ── key 集合比对 ──
$onlyZh = @($zhKeys | Where-Object { $_ -notin $enKeys })
$onlyEn = @($enKeys | Where-Object { $_ -notin $zhKeys })

$isOk = $true
if ($onlyZh.Count -gt 0) {
    $isOk = $false
    Write-Host "以下 key 仅存在于 zh_cn.json（缺于 en_us.json）:" -ForegroundColor Yellow
    $onlyZh | ForEach-Object { Write-Host "  - $_" }
}
if ($onlyEn.Count -gt 0) {
    $isOk = $false
    Write-Host "以下 key 仅存在于 en_us.json（缺于 zh_cn.json）:" -ForegroundColor Yellow
    $onlyEn | ForEach-Object { Write-Host "  - $_" }
}

if (-not $isOk) {
    Write-Host "lang key 集合不一致（zh=$($zhKeys.Count) / en=$($enKeys.Count)）" -ForegroundColor Red
    exit 1
}

Write-Host "lang key 校验通过: zh_cn=$($zhKeys.Count) en_us=$($enKeys.Count) 集合一致" -ForegroundColor Green
exit 0
