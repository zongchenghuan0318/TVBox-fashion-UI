#requires -Version 5.1
param([switch]$PrepareOnly)
$ErrorActionPreference = 'Stop'
$project = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$repo = 'zongchenghuan0318/TVBox-fashion-UI'
$api = "https://api.github.com/repos/$repo"
$utf8 = New-Object System.Text.UTF8Encoding($false)

function GitHub([string]$Method, [string]$Url, $Body = $null) {
    if ($null -ne $Body) {
        Invoke-RestMethod -Method $Method -Uri $Url -Headers $headers -ContentType 'application/json; charset=utf-8' -Body ($utf8.GetBytes(($Body | ConvertTo-Json -Depth 8)))
    } else { Invoke-RestMethod -Method $Method -Uri $Url -Headers $headers }
}

Push-Location $project
try {
    if (-not $PrepareOnly -and (git status --porcelain)) { throw 'Working tree must be clean. Commit your changes before publishing.' }
    $branch = git branch --show-current
    if ($LASTEXITCODE -ne 0 -or $branch -ne 'main') { throw 'Publishing requires the main branch.' }
    $buildFile = Get-Content 'app/build.gradle' -Raw -Encoding UTF8
    if ($buildFile -notmatch '(?m)^\s*versionCode\s+(\d+)\s*$') { throw 'Cannot read versionCode.' }
    $versionCode = [int]$Matches[1]
    if ($buildFile -notmatch "(?m)^\s*versionName\s+'(\d+\.\d+\.\d+)'\s*$") { throw 'Cannot read versionName.' }
    $version = $Matches[1]
    $tag = "v$version"
    $notesPath = "docs/releases/$version.md"
    if (-not (Test-Path -LiteralPath $notesPath)) { throw "Missing release notes: $notesPath" }
    $notes = (Get-Content -LiteralPath $notesPath -Raw -Encoding UTF8).Trim()
    if (-not $notes) { throw 'Release notes are empty.' }

    $jdkLine = Get-Content 'gradle.properties' | Where-Object { $_ -match '^org\.gradle\.java\.home=' } | Select-Object -First 1
    $jdk = if ($jdkLine) { ($jdkLine -split '=', 2)[1].Trim() } else { $env:JAVA_HOME }
    $sdk = ((Get-Content 'local.properties' | Where-Object { $_ -match '^sdk\.dir=' } | Select-Object -First 1) -split '=', 2)[1].Trim().Replace('\\', '\').Replace('\:', ':')
    $previousJava = $env:JAVA_HOME
    try {
        $env:JAVA_HOME = $jdk
        $previousPreference = $ErrorActionPreference
        $ErrorActionPreference = 'Continue'
        & .\gradlew.bat :app:assembleRelease --offline
        $buildExit = $LASTEXITCODE
        $ErrorActionPreference = $previousPreference
        if ($buildExit -ne 0) { throw 'Release build failed.' }
        $apk = Join-Path $project 'app/build/outputs/apk/release/Z影视.apk'
        $cert = & (Join-Path $sdk 'build-tools/30.0.3/apksigner.bat') verify --print-certs $apk 2>&1
        if ($LASTEXITCODE -ne 0 -or (($cert -join "`n") -notmatch 'Signer #1 certificate SHA-256 digest: 195cc65bd18f8e04184423ad84306a9ed9582f4f0be5cf7d26b19371e4bcba62')) { throw 'APK signature verification failed or certificate does not match.' }
        $tempApk = Join-Path ([IO.Path]::GetTempPath()) ("zmovie-$([guid]::NewGuid().ToString('N')).apk")
        try {
            Copy-Item -LiteralPath $apk -Destination $tempApk
            $badging = & (Join-Path $sdk 'build-tools/30.0.3/aapt.exe') dump badging $tempApk
            if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect APK metadata.' }
        } finally { Remove-Item -LiteralPath $tempApk -ErrorAction SilentlyContinue }
    } finally { $env:JAVA_HOME = $previousJava }
    $package = $badging | Where-Object { $_ -match '^package:' } | Select-Object -First 1
    if ($package -notmatch "name='com\.studio\.osc' versionCode='(\d+)' versionName='(\d+\.\d+\.\d+)'" -or [int]$Matches[1] -ne $versionCode -or $Matches[2] -ne $version) { throw 'APK package or version differs from build.gradle.' }
    $output = Join-Path $project "dist/$tag"
    New-Item -ItemType Directory -Path $output -Force | Out-Null
    $releaseApk = Join-Path $output 'Z.apk'
    Copy-Item -LiteralPath $apk -Destination $releaseApk -Force
    $hash = (Get-FileHash -LiteralPath $releaseApk -Algorithm SHA256).Hash.ToLowerInvariant()
    $manifest = ([ordered]@{ versionCode=$versionCode; versionName=$version; changelog=$notes; apkUrl="https://github.com/$repo/releases/download/$tag/Z.apk"; sha256=$hash; size=(Get-Item -LiteralPath $releaseApk).Length } | ConvertTo-Json) + "`n"
    [IO.File]::WriteAllText((Join-Path $output 'update.json'), $manifest, $utf8)
    [IO.File]::WriteAllText((Join-Path $output 'SHA256SUMS.txt'), "$hash  Z.apk`n", $utf8)
    Write-Host "Prepared $tag; SHA-256 $hash"
    if ($PrepareOnly) { Write-Host 'Prepare-only: no repository or remote changes.'; return }

    $token = $env:GITHUB_TOKEN
    if (-not $token -or $token -match '[^\x21-\x7e]') {
        $credential = "protocol=https`nhost=github.com`n`n" | git -c credential.interactive=never credential fill 2>$null
        $password = $credential | Where-Object { $_ -like 'password=*' } | Select-Object -First 1
        if (-not $password) { throw 'GitHub token not found. Set GITHUB_TOKEN or sign in via Git Credential Manager.' }
        $token = $password.Substring(9)
    }
    $headers = @{ Authorization="Bearer $token"; Accept='application/vnd.github+json'; 'User-Agent'='ZMovie-Release' }
    $releases = @(GitHub GET "$api/releases?per_page=100")
    if ($releases | Where-Object { $_.tag_name -eq $tag }) { throw 'Release already exists, including drafts; inspect it before retrying.' }
    $refs = @(GitHub GET "$api/git/matching-refs/tags/$tag")
    if ($refs | Where-Object { $_.ref -eq "refs/tags/$tag" }) { throw 'Tag already exists; it will not be moved.' }
    $latest = $releases | Where-Object { -not $_.draft -and -not $_.prerelease } | Select-Object -First 1
    if ($latest) {
        $oldAsset = $latest.assets | Where-Object { $_.name -eq 'update.json' } | Select-Object -First 1
        if (-not $oldAsset) { throw 'Latest release has no update.json; cannot verify versionCode.' }
        $oldInfo = Invoke-RestMethod -Uri $oldAsset.browser_download_url -Headers @{ 'User-Agent'='ZMovie-Release' }
        if (-not $oldInfo.versionCode -or $versionCode -le [int]$oldInfo.versionCode) { throw 'versionCode must be greater than the latest stable release.' }
        if ($latest.tag_name -match '^v(\d+\.\d+\.\d+)$' -and [version]$version -le [version]$Matches[1]) { throw 'versionName must be greater than the latest stable release.' }
    }
    $remote = git ls-remote origin refs/heads/main
    if ($LASTEXITCODE -ne 0 -or -not $remote) { throw 'Cannot read origin/main.' }
    $remoteSha = ($remote -split '\s+')[0]
    git merge-base --is-ancestor $remoteSha HEAD
    if ($LASTEXITCODE -ne 0) { throw 'origin/main is not an ancestor of HEAD; fetch and reconcile first.' }

    $repoManifest = Join-Path $project 'release/update.json'
    $oldManifest = if (Test-Path -LiteralPath $repoManifest) { [IO.File]::ReadAllText($repoManifest, [Text.Encoding]::UTF8) } else { '' }
    if ($oldManifest -cne $manifest) {
        [IO.File]::WriteAllText($repoManifest, $manifest, $utf8)
        git add -- release/update.json
        if ($LASTEXITCODE -ne 0) { throw 'Cannot stage release manifest.' }
        git diff --cached --quiet -- release/update.json
        if ($LASTEXITCODE -eq 1) {
            git commit -m "更新 $version 发布清单" -m "Co-Authored-By: Claude Haiku 5.5 <noreply@anthropic.com>" -- release/update.json
            if ($LASTEXITCODE -ne 0) { throw 'Cannot commit release manifest.' }
        } elseif ($LASTEXITCODE -ne 0) { throw 'Cannot check release manifest.' }
    }
    $commit = git rev-parse HEAD
    git push origin main
    if ($LASTEXITCODE -ne 0) { throw 'GitHub push failed; no release was created.' }
    $remote = git ls-remote origin refs/heads/main
    if ($LASTEXITCODE -ne 0 -or ($remote -split '\s+')[0] -ne $commit) { throw 'Remote main does not match the release commit.' }

    $draft = GitHub POST "$api/releases" @{ tag_name=$tag; target_commitish=$commit; name="Z影视 $version"; body=$notes; draft=$true; prerelease=$false }
    $upload = ($draft.upload_url -split '\{')[0]
    foreach ($name in @('Z.apk','update.json','SHA256SUMS.txt')) {
        $file = Join-Path $output $name
        $asset = Invoke-RestMethod -Method Post -Uri ($upload + '?name=' + [Uri]::EscapeDataString($name)) -Headers $headers -ContentType 'application/octet-stream' -InFile $file
        $assetHash = (Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash.ToLowerInvariant()
        if ($asset.state -ne 'uploaded' -or $asset.size -ne (Get-Item -LiteralPath $file).Length -or $asset.digest -ne "sha256:$assetHash") { throw "Upload verification failed for $name; release remains a draft." }
        Write-Host "Verified $name"
    }
    $published = GitHub PATCH "$api/releases/$($draft.id)" @{ draft=$false; make_latest='true' }
    if ($published.draft) { throw 'Release remains a draft.' }
    Write-Host "Published $($published.html_url)"
} finally { Pop-Location }
