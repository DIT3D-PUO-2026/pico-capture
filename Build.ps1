param(
    [string]$Sdk = $env:ANDROID_HOME,
    [string]$Output
)
$ErrorActionPreference = 'Stop'
if (-not $Output) {
    $versionLine = Get-Content (Join-Path $PSScriptRoot 'gradle.properties') | Where-Object { $_ -match '^appVersionName=' } | Select-Object -First 1
    if (-not $versionLine) { throw 'appVersionName is missing from gradle.properties' }
    $Output = Join-Path $PSScriptRoot ('PicoCapture-' + ($versionLine -split '=',2)[1].Trim() + '.apk')
}
$previousAndroidHome = $env:ANDROID_HOME
try {
    if ($Sdk) { $env:ANDROID_HOME = $Sdk }
    Push-Location $PSScriptRoot
    try {
        & (Join-Path $PSScriptRoot 'gradlew.bat') ':app:assembleDebug' '--console=plain'
        if ($LASTEXITCODE -ne 0) { throw 'Gradle build failed; see the output above.' }
    } finally {
        Pop-Location
    }
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'app/build/outputs/apk/debug/app-debug.apk') -Destination $Output -Force
    Write-Output "Built $Output"
} finally {
    $env:ANDROID_HOME = $previousAndroidHome
}
