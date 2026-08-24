[CmdletBinding()]
param(
    [Parameter(Mandatory = $false)]
    [string]$RisingWorldPath,

    [switch]$Install
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$buildConfigPath = Join-Path $projectRoot 'build.config.json'

# Resolution order: command-line parameter, environment variable, local JSON config.
if ([string]::IsNullOrWhiteSpace($RisingWorldPath)) {
    $RisingWorldPath = $env:RISING_WORLD_PATH
}

if ([string]::IsNullOrWhiteSpace($RisingWorldPath) -and (Test-Path -LiteralPath $buildConfigPath)) {
    $buildConfig = Get-Content -LiteralPath $buildConfigPath -Raw | ConvertFrom-Json
    $RisingWorldPath = $buildConfig.risingWorldPath
}

if ([string]::IsNullOrWhiteSpace($RisingWorldPath)) {
    throw "Set risingWorldPath in build.config.json, set RISING_WORLD_PATH, or pass -RisingWorldPath 'C:\path\to\RisingWorld'."
}

$RisingWorldPath = [System.IO.Path]::GetFullPath($RisingWorldPath)
$jdkBin = Join-Path $RisingWorldPath 'Data\Java\JDK\bin'
$javac = Join-Path $jdkBin 'javac.exe'
$jar = Join-Path $jdkBin 'jar.exe'
$sdkDirectory = Join-Path $RisingWorldPath 'Data\SDK'

if (-not (Test-Path -LiteralPath $javac)) { throw "Could not find bundled JDK compiler: $javac" }
if (-not (Test-Path -LiteralPath $jar)) { throw "Could not find bundled jar tool: $jar" }
if (-not (Test-Path -LiteralPath $sdkDirectory)) { throw "Could not find Rising World SDK: $sdkDirectory" }

$apiJar = Get-ChildItem -LiteralPath $sdkDirectory -Filter '*.jar' -File |
    Where-Object { $_.Name -match '(?i)(api|sdk|risingworld)' } |
    Select-Object -First 1
if ($null -eq $apiJar) {
    $apiJar = Get-ChildItem -LiteralPath $sdkDirectory -Filter '*.jar' -File | Select-Object -First 1
}
if ($null -eq $apiJar) { throw "No API .jar found in $sdkDirectory" }

$buildDirectory = Join-Path $projectRoot 'build'
$classesDirectory = Join-Path $buildDirectory 'classes'
$pluginJar = Join-Path $buildDirectory 'RisingWorldStarter.jar'
Remove-Item -LiteralPath $buildDirectory -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $classesDirectory | Out-Null

$sources = Get-ChildItem -LiteralPath (Join-Path $projectRoot 'src') -Filter '*.java' -Recurse -File
if ($sources.Count -eq 0) { throw 'No Java source files found under src.' }

& $javac '--release' '20' '-encoding' 'UTF-8' '-cp' $apiJar.FullName '-d' $classesDirectory @($sources.FullName)
if ($LASTEXITCODE -ne 0) { throw 'Compilation failed.' }

$jarResourcesDirectory = Join-Path $classesDirectory 'resources'
New-Item -ItemType Directory -Force -Path $jarResourcesDirectory | Out-Null
Copy-Item -LiteralPath (Join-Path $projectRoot 'resources\plugin.yml') -Destination (Join-Path $jarResourcesDirectory 'plugin.yml')
& $jar 'cf' $pluginJar '-C' $classesDirectory '.'
if ($LASTEXITCODE -ne 0) { throw 'JAR packaging failed.' }

Write-Host "Built: $pluginJar"

if ($Install) {
    $destination = Join-Path $RisingWorldPath 'Plugins\RisingWorldStarter'
    New-Item -ItemType Directory -Force -Path $destination | Out-Null
    Copy-Item -LiteralPath $pluginJar -Destination (Join-Path $destination 'RisingWorldStarter.jar') -Force
    Write-Host "Installed: $destination"
}
