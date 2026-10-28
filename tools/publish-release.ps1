#requires -Version 5.1
param(
    [Parameter(Mandatory = $true)][string]$NotesFile,
    [switch]$PrepareOnly
)
$ErrorActionPreference = 'Stop'
$taskProject = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$taskRepo = 'zongchenghuan0318/TVBox-fashion-UI'
$taskApi = "https://api.github.com/repos/$taskRepo"
$taskGiteeRepo = 'the-eternal-cycle/TVBox-fashion-UI'
$taskGiteeBase = "https://gitee.com/$taskGiteeRepo"
$taskGiteeApi = "https://gitee.com/api/v5/repos/$taskGiteeRepo"
$taskUtf8 = New-Object System.Text.UTF8Encoding($false)

function Write-Utf8([string]$Path, [string]$Text) {
    [System.IO.File]::WriteAllText($Path, $Text, $taskUtf8)
}
function Invoke-GitHub([string]$Method, [string]$Url, $Body = $null) {
    if ($Body) {
        Invoke-RestMethod -Method $Method -Uri $Url -Headers $taskHeaders `
            -ContentType 'application/json; charset=utf-8' -Body ($taskUtf8.GetBytes(($Body | ConvertTo-Json -Depth 8)))
    } else { Invoke-RestMethod -Method $Method -Uri $Url -Headers $taskHeaders }
}
function Send-GiteeAttachment([string]$ReleaseId, [string]$Path, [string]$Name, [string]$ContentType) {
    $boundary = [Guid]::NewGuid().ToString('N')
    $head = "--$boundary`r`nContent-Disposition: form-data; name=`"file`"; filename=`"$Name`"`r`nContent-Type: $ContentType`r`n`r`n"
    $tail = "`r`n--$boundary--`r`n"
    $stream = New-Object System.IO.MemoryStream
    $headBytes = [Text.Encoding]::UTF8.GetBytes($head)
    $tailBytes = [Text.Encoding]::UTF8.GetBytes($tail)
    $fileBytes = [System.IO.File]::ReadAllBytes($Path)
    $stream.Write($headBytes, 0, $headBytes.Length)
    $stream.Write($fileBytes, 0, $fileBytes.Length)
    $stream.Write($tailBytes, 0, $tailBytes.Length)
    Invoke-RestMethod -Method Post -Uri "$taskGiteeApi/releases/$ReleaseId/attach_files" -Headers $taskGiteeHeaders `
        -ContentType "multipart/form-data; boundary=$boundary" -Body $stream.ToArray() -TimeoutSec 600
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
        if (!$env:GITEE_TOKEN) { throw 'Set GITEE_TOKEN locally with projects permission for the Gitee mirror repository.' }
        $taskRemote = git ls-remote origin refs/heads/main
        if ($LASTEXITCODE -ne 0 -or !$taskRemote -or ($taskRemote -split '\s+')[0] -ne $taskCommit) {
            throw 'HEAD must match the pushed origin/main commit.'
        }
        $taskHeaders = @{ Authorization = "Bearer $env:GITHUB_TOKEN"; Accept = 'application/vnd.github+json'; 'User-Agent' = 'ZMovie-Release' }
        $taskGiteeHeaders = @{ Authorization = "token $env:GITEE_TOKEN"; 'User-Agent' = 'ZMovie-Release' }
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
        # PowerShell 5.1 会把 Gradle 写到 stderr 的警告当成异常，这里只按退出码判断构建结果
        $taskPreviousPreference = $ErrorActionPreference
        $ErrorActionPreference = 'Continue'
        & .\gradlew.bat :app:assembleRelease --offline
        $taskBuildExit = $LASTEXITCODE
        $ErrorActionPreference = $taskPreviousPreference
        if ($taskBuildExit -ne 0) { throw 'Release build failed.' }
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
    # Gitee 是主更新源，GitHub 作为备用地址；仓库 release/ 与 Gitee 附件用这份清单。
    $taskGiteeApk = "$taskGiteeBase/releases/download/$taskTag/Z.apk"
    $taskGithubApk = "https://github.com/$taskRepo/releases/download/$taskTag/Z.apk"
    $taskSize = (Get-Item -LiteralPath $taskReleaseApk).Length
    $taskGiteeManifest = (([ordered]@{
        versionCode = $taskVersionCode; versionName = $taskVersionName; changelog = $taskNotes.Trim()
        apkUrl = $taskGiteeApk
        apkUrlFallback = $taskGithubApk
        sha256 = $taskHash; size = $taskSize
    } | ConvertTo-Json) + "`n")
    # GitHub 附件必须让 1.1.19 及更早客户端能解析：它们只认 GitHub 主地址，因此这里只写 GitHub 地址。
    $taskGithubManifest = (([ordered]@{
        versionCode = $taskVersionCode; versionName = $taskVersionName; changelog = $taskNotes.Trim()
        apkUrl = $taskGithubApk
        sha256 = $taskHash; size = $taskSize
    } | ConvertTo-Json) + "`n")
    Write-Utf8 (Join-Path $taskOutput 'update.json') $taskGiteeManifest
    Write-Utf8 (Join-Path $taskOutput 'github-update.json') $taskGithubManifest
    Write-Utf8 (Join-Path $taskOutput 'SHA256SUMS.txt') "$taskHash  Z.apk`n"
    $taskRepoManifestDir = Join-Path $taskProject 'release'
    $taskRepoManifest = Join-Path $taskRepoManifestDir 'update.json'
    if ($PrepareOnly) {
        New-Item -ItemType Directory -Path $taskRepoManifestDir -Force | Out-Null
        Write-Utf8 $taskRepoManifest $taskGiteeManifest
    } elseif (!(Test-Path -LiteralPath $taskRepoManifest)) {
        throw 'release/update.json is missing. Run -PrepareOnly and commit it before publishing.'
    } elseif ([System.IO.File]::ReadAllText($taskRepoManifest, [Text.Encoding]::UTF8) -ne $taskGiteeManifest) {
        throw 'release/update.json does not match this build. Run -PrepareOnly and commit the refreshed manifest.'
    }
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

    # 1) Gitee 先建发行版并上传附件，保证 APK 地址在清单提交前已经可用。
    $taskGiteeReleases = @(Invoke-RestMethod -Method Get -Uri "$taskGiteeApi/releases?per_page=100" -Headers $taskGiteeHeaders)
    if ($taskGiteeReleases | Where-Object { $_.tag_name -eq $taskTag }) {
        throw 'This version already has a Gitee release. Increment the version; published assets are never replaced.'
    }
    $taskGiteeRelease = Invoke-RestMethod -Method Post -Uri "$taskGiteeApi/releases" -Headers $taskGiteeHeaders `
        -ContentType 'application/json; charset=utf-8' -Body ($taskUtf8.GetBytes(([ordered]@{
            tag_name = $taskTag; target_commitish = $taskCommit; name = "Z影视 $taskVersionName"
            body = $taskNotes.Trim(); prerelease = $false
        } | ConvertTo-Json))) -TimeoutSec 60
    if (!$taskGiteeRelease.id) { throw 'Cannot create the Gitee release.' }
    foreach ($taskName in @('Z.apk', 'update.json', 'SHA256SUMS.txt')) {
        $taskFile = Join-Path $taskOutput $taskName
        $taskContentType = if ($taskName -eq 'Z.apk') { 'application/vnd.android.package-archive' } elseif ($taskName -eq 'update.json') { 'application/json; charset=utf-8' } else { 'text/plain; charset=utf-8' }
        $taskGiteeUpload = Send-GiteeAttachment $taskGiteeRelease.id $taskFile $taskName $taskContentType
        if ($taskGiteeUpload.name -ne $taskName -or $taskGiteeUpload.size -ne (Get-Item -LiteralPath $taskFile).Length) {
            throw "Gitee upload of $taskName did not match. Remove the Gitee release before retrying."
        }
    }
    Write-Output "Gitee published: $taskGiteeBase/releases/tag/$taskTag"

    # 2) GitHub 备用源：建草稿、上传、核对哈希后设为 Latest。
    $taskDraft = Invoke-GitHub POST "$taskApi/releases" @{
        tag_name = $taskTag; target_commitish = $taskCommit; name = "Z影视 $taskVersionName"
        body = $taskNotes.Trim(); draft = $true; prerelease = $false
    }
    $taskUpload = ($taskDraft.upload_url -split '\{')[0]
    # update.json 上传的是 GitHub 主地址版本，保证旧客户端仍能解析；本地文件名不影响附件名。
    $taskAssets = @(
        @{ name = 'Z.apk'; file = 'Z.apk' }
        @{ name = 'update.json'; file = 'github-update.json' }
        @{ name = 'SHA256SUMS.txt'; file = 'SHA256SUMS.txt' }
    )
    foreach ($taskAsset in $taskAssets) {
        $taskName = $taskAsset.name
        $taskFile = Join-Path $taskOutput $taskAsset.file
        $taskContentType = if ($taskName -eq 'Z.apk') { 'application/vnd.android.package-archive' } elseif ($taskName -eq 'update.json') { 'application/json; charset=utf-8' } else { 'text/plain; charset=utf-8' }
        $taskUploaded = Invoke-RestMethod -Method Post -Uri ($taskUpload + '?name=' + [Uri]::EscapeDataString($taskName)) `
            -Headers $taskHeaders -ContentType $taskContentType -InFile $taskFile
        $taskAssetHash = (Get-FileHash -LiteralPath $taskFile -Algorithm SHA256).Hash.ToLowerInvariant()
        if ($taskUploaded.state -ne 'uploaded' -or $taskUploaded.size -ne (Get-Item -LiteralPath $taskFile).Length `
                -or $taskUploaded.digest -ne "sha256:$taskAssetHash") {
            throw "Uploaded $taskName did not match. The Release remains a draft."
        }
    }
    $taskPublished = Invoke-GitHub PATCH "$taskApi/releases/$($taskDraft.id)" @{ draft = $false; make_latest = 'true' }
    if ($taskPublished.draft) { throw 'Release is still a draft.' }
    Write-Output "GitHub published: $($taskPublished.html_url)"
} finally { Pop-Location }
