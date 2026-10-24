param([string]$JavaHome)
$ErrorActionPreference = 'Stop'
$taskProject = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
if (!$JavaHome) {
    $taskJdkSetting = Get-Content -LiteralPath (Join-Path $taskProject 'gradle.properties') |
        Where-Object { $_ -match '^org\.gradle\.java\.home=' } | Select-Object -First 1
    if ($taskJdkSetting) { $JavaHome = ($taskJdkSetting -split '=', 2)[1].Trim() }
}
if (!$JavaHome) { $JavaHome = $env:JAVA_HOME }
$taskJavac = Join-Path $JavaHome 'bin/javac.exe'
$taskJava = Join-Path $JavaHome 'bin/java.exe'
$taskOutput = Join-Path $taskProject 'app/build/speed-gesture-regression'
New-Item -ItemType Directory -Path $taskOutput -Force | Out-Null
$taskStateSource = Join-Path $taskProject 'app/src/main/java/com/github/tvbox/osc/player/controller/SpeedGestureState.java'
$taskTestSource = Join-Path $PSScriptRoot 'SpeedGestureStateTest.java'
& $taskJavac -encoding UTF-8 --release 8 -d $taskOutput $taskStateSource $taskTestSource
if ($LASTEXITCODE -ne 0) { throw "Speed gesture compilation failed: $LASTEXITCODE" }
& $taskJava -classpath $taskOutput SpeedGestureStateTest
if ($LASTEXITCODE -ne 0) { throw "Speed gesture regression failed: $LASTEXITCODE" }
