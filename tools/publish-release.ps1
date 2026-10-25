#requires -Version 7.0
param(
    [Parameter(Mandatory = $true)][string]$NotesFile,
    [switch]$PrepareOnly
)
$ErrorActionPreference = 'Stop'
$taskProject = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$taskRepo = 'zongchenghuan0318/TVBox-fashion-UI'
$taskApi = "https://api.github.com/repos/$taskRepo"
$taskUtf8 = New-Object System.Text.UTF8Encoding($false)

function Write-Utf8([string]$Path, [string]$Text) {
    [System.IO.File]::WriteAllText($Path, $Text, $taskUtf8)
}
function Invoke-GitHub([string]$Method, [string]$Url, $Body = $null) {
    if ($Body) {
        Invoke-RestMethod -Method $Method -Uri $Url -Headers $taskHeaders `
            -ContentType 'application/json; charset=utf-8' -Body ($taskUtf8.GetBytes(($Body | ConvertTo-Json -Depth 8))) -DisableKeepAlive
    } else { Invoke-RestMethod -Method $Method -Uri $Url -Headers $taskHeaders -DisableKeepAlive }
}

Push-Location $taskProject
try {
    $taskNotes = Get-Content -LiteralPath (Resolve-Path $NotesFile) -Raw -Encoding UTF8
    if ([string]::IsNullOrWhiteSpace($taskNotes)) { throw 'Release notes must not be empty.' }
    $taskCommit = git rev-parse HEAD
    if ($LASTEXITCODE -ne 0) { throw 'Cannot read Git HEAD.' }
    if (!$PrepareOnly) {
        if (git status --porcelain) { throw 'Commit all release changes before publishing. Use -PrepareOnly to preview a local build.' }
        if (!$env:GITHUB_TOKEN) { throw 'Set GITHUB_TOKEN locally with Contents read/write permission for this repository.' }
        $taskRemote = git ls-remote origin refs/heads/main
        if ($LASTEXITCODE -ne 0 -or !$taskRemote -or ($taskRemote -split '\s+')[0] -ne $taskCommit) {
            throw 'HEAD must match the pushed origin/main commit.'
        }
        $taskHeaders = @{ Authorization = "Bearer $env:GITHUB_TOKEN"; Accept = 'application/vnd.github+json'; 'User-Agent' = 'ZMovie-Release' }
    }

    $taskLocal = Get-Content -LiteralPath 'local.properties' | Where-Object { $_ -match '^sdk\.dir=' } | Select-Object -First 1
    $taskSdk = ($taskLocal -split '=', 2)[1].Trim().Replace('\\', '\').Replace('\:', ':')
    $taskAapt = Join-Path $taskSdk 'build-tools/30.0.3/aapt.exe'
    $taskSigner = Join-Path $taskSdk 'build-tools/30.0.3/apksigner.bat'
    $taskJdkLine = Get-Content -LiteralPath 'gradle.properties' | Where-Object { $_ -match '^org\.gradle\.java\.home=' } | Select-Object -First 1
    $taskJdk = ($taskJdkLine -split '=', 2)[1].Trim()
    $taskPreviousJava = $env:JAVA_HOME
    try {
        $env:JAVA_HOME = $taskJdk
        & .\gradlew.bat :app:assembleRelease --offline
        if ($LASTEXITCODE -ne 0) { throw 'Release build failed.' }
        $taskApk = Join-Path $taskProject 'app/build/outputs/apk/release/Z影视.apk'
        $taskCertificate = & $taskSigner verify --print-certs $taskApk 2>&1
        if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed.' }
        $taskExpectedCertificate = '195cc65bd18f8e04184423ad84306a9ed9582f4f0be5cf7d26b19371e4bcba62'
        if (!(($taskCertificate -join "`n") -match "Signer #1 certificate SHA-256 digest: $taskExpectedCertificate")) {
            throw 'APK certificate does not match existing installations.'
        }
        # This SDK's aapt cannot open Unicode APK paths on Windows.
        $taskInspectDir = Join-Path ([System.IO.Path]::GetTempPath()) ('zmovie-release-' + [Guid]::NewGuid().ToString('N'))
        New-Item -ItemType Directory -Path $taskInspectDir | Out-Null
        $taskInspectApk = Join-Path $taskInspectDir 'Z.apk'
        try {
            Copy-Item -LiteralPath $taskApk -Destination $taskInspectApk
            $taskBadging = & $taskAapt dump badging $taskInspectApk
            if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect APK metadata.' }
        } finally {
            Remove-Item -LiteralPath $taskInspectApk -ErrorAction SilentlyContinue
            Remove-Item -LiteralPath $taskInspectDir
        }
    } finally { $env:JAVA_HOME = $taskPreviousJava }
    $taskPackage = $taskBadging | Where-Object { $_ -match '^package:' } | Select-Object -First 1
    if ($taskPackage -notmatch "name='com.studio.osc' versionCode='(\d+)' versionName='(\d+\.\d+\.\d+)'") {
        throw 'Unexpected APK package or version format.'
    }
    $taskVersionCode = [int]$Matches[1]
    $taskVersionName = $Matches[2]
    $taskTag = "v$taskVersionName"
    $taskOutput = Join-Path $taskProject "dist/$taskTag"
    New-Item -ItemType Directory -Path $taskOutput -Force | Out-Null
    $taskReleaseApk = Join-Path $taskOutput 'Z.apk'
    Copy-Item -LiteralPath $taskApk -Destination $taskReleaseApk -Force
    $taskHash = (Get-FileHash -LiteralPath $taskReleaseApk -Algorithm SHA256).Hash.ToLowerInvariant()
    $taskInfo = [ordered]@{
        versionCode = $taskVersionCode; versionName = $taskVersionName; changelog = $taskNotes.Trim()
        apkUrl = "https://github.com/$taskRepo/releases/download/$taskTag/Z.apk"
        sha256 = $taskHash; size = (Get-Item -LiteralPath $taskReleaseApk).Length
    }
    Write-Utf8 (Join-Path $taskOutput 'update.json') (($taskInfo | ConvertTo-Json) + "`n")
    Write-Utf8 (Join-Path $taskOutput 'SHA256SUMS.txt') "$taskHash  Z.apk`n"
    Write-Output "Prepared $taskTag ($taskVersionCode): $taskOutput"
    if ($PrepareOnly) { return }

    $taskReleases = @(Invoke-GitHub GET "$taskApi/releases?per_page=100")
    if ($taskReleases | Where-Object { !$_.draft -and $_.tag_name -eq $taskTag }) {
        throw 'This version is already published. Increment the version; published assets are never replaced.'
    }
    foreach ($taskRelease in $taskReleases | Where-Object { !$_.draft -and !$_.prerelease }) {
        $taskOldManifest = @($taskRelease.assets | Where-Object { $_.name -eq 'update.json' })
        if ($taskOldManifest.Count -eq 1) {
            $taskOldResponse = Invoke-WebRequest -Uri $taskOldManifest[0].browser_download_url -Headers @{ 'User-Agent' = 'ZMovie-Release' }
            $taskOldJson = if ($taskOldResponse.Content -is [byte[]]) { [Text.Encoding]::UTF8.GetString($taskOldResponse.Content) } else { [string]$taskOldResponse.Content }
            $taskOldInfo = $taskOldJson | ConvertFrom-Json
            if (!$taskOldInfo.versionCode) { throw 'Published version metadata is invalid.' }
            if ($taskVersionCode -le [long]$taskOldInfo.versionCode) { throw 'versionCode must exceed all published stable versions.' }
        }
    }
    if ($taskReleases | Where-Object { $_.draft -and $_.tag_name -eq $taskTag }) {
        throw 'A draft exists for this version. Inspect or remove the draft before retrying; it was not overwritten.'
    }
    $taskRef = Invoke-GitHub GET "$taskApi/git/matching-refs/tags/$taskTag"
    if (@($taskRef | Where-Object { $_.ref -eq "refs/tags/$taskTag" }).Count -gt 0) {
        throw 'Tag already exists. Inspect it before retrying; existing tags are never moved.'
    }
    $taskDraft = Invoke-GitHub POST "$taskApi/releases" @{
        tag_name = $taskTag; target_commitish = $taskCommit; name = "Z影视 $taskVersionName"
        body = $taskNotes.Trim(); draft = $true; prerelease = $false
    }
    $taskUpload = ($taskDraft.upload_url -split '\{')[0]
    foreach ($taskName in @('Z.apk', 'update.json', 'SHA256SUMS.txt')) {
        $taskFile = Join-Path $taskOutput $taskName
        $taskContentType = if ($taskName -eq 'Z.apk') { 'application/vnd.android.package-archive' } elseif ($taskName -eq 'update.json') { 'application/json; charset=utf-8' } else { 'text/plain; charset=utf-8' }
        $taskUploaded = Invoke-RestMethod -Method Post -Uri ($taskUpload + '?name=' + [Uri]::EscapeDataString($taskName)) `
            -Headers $taskHeaders -ContentType $taskContentType -InFile $taskFile -DisableKeepAlive
        $taskAssetHash = (Get-FileHash -LiteralPath $taskFile -Algorithm SHA256).Hash.ToLowerInvariant()
        if ($taskUploaded.state -ne 'uploaded' -or $taskUploaded.size -ne (Get-Item -LiteralPath $taskFile).Length `
                -or $taskUploaded.digest -ne "sha256:$taskAssetHash") {
            throw "Uploaded $taskName did not match. The Release remains a draft."
        }
    }
    $taskPublished = Invoke-GitHub PATCH "$taskApi/releases/$($taskDraft.id)" @{ draft = $false; make_latest = 'true' }
    if ($taskPublished.draft) { throw 'Release is still a draft.' }
    Write-Output "Published: $($taskPublished.html_url)"
} finally { Pop-Location }
