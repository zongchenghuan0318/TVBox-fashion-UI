param([string]$JavaHome)
$ErrorActionPreference = 'Stop'
$taskProject = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
if (!$JavaHome) {
    $taskJdk = Get-Content (Join-Path $taskProject 'gradle.properties') | Where-Object { $_ -match '^org\.gradle\.java\.home=' } | Select-Object -First 1
    $JavaHome = ($taskJdk -split '=', 2)[1].Trim()
}
$taskCache = if ($env:GRADLE_USER_HOME) { $env:GRADLE_USER_HOME } else { Join-Path $env:USERPROFILE '.gradle' }
$taskModules = Join-Path $taskCache 'caches/modules-2/files-2.1'
$taskLibraries = @('com.squareup.okhttp3/okhttp/3.12.11', 'com.squareup.okio/okio/2.6.0',
    'com.google.code.gson/gson/2.8.7', 'junit/junit/4.13.2', 'org.hamcrest/hamcrest-core/1.3',
    'org.jetbrains.kotlin/kotlin-stdlib/1.4.31')
$taskJars = foreach ($taskLibrary in $taskLibraries) {
    $taskJar = Get-ChildItem (Join-Path $taskModules $taskLibrary) -Recurse -Filter '*.jar' | Select-Object -First 1
    if (!$taskJar) { throw "Missing cached dependency $taskLibrary. Resolve the existing Gradle test dependencies first." }
    $taskJar.FullName
}
$taskOutput = Join-Path $taskProject 'app/build/update-regression'
New-Item -ItemType Directory -Path $taskOutput -Force | Out-Null
$taskClasspath = (@($taskOutput) + @($taskJars)) -join ';'
& (Join-Path $JavaHome 'bin/javac.exe') -encoding UTF-8 --release 8 -cp $taskClasspath -d $taskOutput `
    (Join-Path $taskProject 'app/src/main/java/com/github/tvbox/osc/util/UpdateClient.java') `
    (Join-Path $taskProject 'app/src/test/java/com/github/tvbox/osc/util/UpdateClientTest.java')
if ($LASTEXITCODE -ne 0) { throw 'Update regression compilation failed.' }
& (Join-Path $JavaHome 'bin/java.exe') -cp $taskClasspath org.junit.runner.JUnitCore com.github.tvbox.osc.util.UpdateClientTest
if ($LASTEXITCODE -ne 0) { throw 'Update regressions failed.' }
