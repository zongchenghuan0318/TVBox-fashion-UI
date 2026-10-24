param(
    [string]$JavaHome,
    [string]$AndroidSdkRoot
)
$ErrorActionPreference = 'Stop'
$taskProject = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
if (!$JavaHome) {
    $taskJdkSetting = Get-Content -LiteralPath (Join-Path $taskProject 'gradle.properties') |
        Where-Object { $_ -match '^org\.gradle\.java\.home=' } | Select-Object -First 1
    if ($taskJdkSetting) { $JavaHome = ($taskJdkSetting -split '=', 2)[1].Trim() }
}
if (!$JavaHome) { $JavaHome = $env:JAVA_HOME }
if (!$AndroidSdkRoot) {
    $taskSdkSetting = Get-Content -LiteralPath (Join-Path $taskProject 'local.properties') |
        Where-Object { $_ -match '^sdk\.dir=' } | Select-Object -First 1
    if ($taskSdkSetting) { $AndroidSdkRoot = ($taskSdkSetting -split '=', 2)[1].Trim().Replace('\:', ':').Replace('\\', '\') }
}
$taskJavac = Join-Path $JavaHome 'bin/javac.exe'
$taskJava = Join-Path $JavaHome 'bin/java.exe'
$taskAndroidJar = Join-Path $AndroidSdkRoot 'platforms/android-30/android.jar'
$taskCompiledPlayer = Join-Path $taskProject 'player/build/intermediates/javac/debug/classes'
$taskAnnotation = Get-ChildItem -LiteralPath (Join-Path $env:USERPROFILE '.gradle/caches/modules-2/files-2.1/androidx.annotation/annotation/1.3.0') -Recurse -Filter '*.jar' |
    Select-Object -First 1 -ExpandProperty FullName
foreach ($taskRequired in @($taskJavac, $taskJava, $taskAndroidJar, $taskCompiledPlayer, $taskAnnotation)) {
    if (!$taskRequired -or !(Test-Path -LiteralPath $taskRequired)) {
        throw "Missing local prerequisite: $taskRequired. Build :player:compileDebugJavaWithJavac first; this harness never downloads dependencies."
    }
}
$taskOutput = Join-Path $taskProject 'player/build/controller-ticker-regression'
New-Item -ItemType Directory -Path $taskOutput -Force | Out-Null
$taskClasspath = @($taskOutput, $taskCompiledPlayer, $taskAndroidJar, $taskAnnotation) -join ';'
$taskControllerSource = Join-Path $taskProject 'player/src/main/java/xyz/doikki/videoplayer/controller/BaseVideoController.java'
$taskHarnessSource = Join-Path $PSScriptRoot 'ControllerTickerRegression.java'

# Compile the production source, not a copy or a stale controller class.
& $taskJavac -encoding UTF-8 -source 8 -target 8 -classpath $taskClasspath -d $taskOutput $taskControllerSource $taskHarnessSource
if ($LASTEXITCODE -ne 0) { throw "Ticker harness compilation failed: $LASTEXITCODE" }
& $taskJava -classpath $taskClasspath ControllerTickerRegression
if ($LASTEXITCODE -ne 0) { throw "Ticker regression failed: $LASTEXITCODE" }
