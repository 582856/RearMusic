$ErrorActionPreference = 'Stop'
if (!$env:JAVA_HOME -or !(Test-Path -LiteralPath "$env:JAVA_HOME/bin/java.exe")) {
    throw 'Set JAVA_HOME to JDK 17.'
}
$version = & "$env:JAVA_HOME/bin/java.exe" -version 2>&1 | Out-String
if ($version -notmatch 'version "17[.\"]') { throw 'JDK 17 is required.' }
$sdkPath = $env:ANDROID_HOME
if (!$sdkPath) { $sdkPath = $env:ANDROID_SDK_ROOT }
if (!$sdkPath -or !(Test-Path -LiteralPath "$sdkPath/platforms") -or !(Test-Path -LiteralPath "$sdkPath/build-tools")) {
    throw 'Set ANDROID_HOME to an Android SDK containing platforms and build-tools.'
}
if (!(Test-Path "$PSScriptRoot/gradle/wrapper/gradle-wrapper.jar")) { throw 'Gradle wrapper missing.' }
Write-Output 'RESULT=success'
