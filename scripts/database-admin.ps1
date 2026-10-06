param([Parameter(Mandatory = $true)][string]$Command)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$backendPath = Join-Path $projectRoot 'backend'
$applicationJar = Get-ChildItem -LiteralPath (Join-Path $backendPath 'target') -Filter 'pickleball-api-*.jar' | Select-Object -First 1
if (-not $applicationJar) { throw 'Build the backend with .\mvnw.cmd -DskipTests package first.' }
$libraryPath = Join-Path $backendPath 'target/database-tools-libs'
New-Item -ItemType Directory -Force -Path $libraryPath | Out-Null
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [IO.Compression.ZipFile]::OpenRead($applicationJar.FullName)
try {
    foreach ($entry in $archive.Entries) {
        if ($entry.FullName -like 'BOOT-INF/lib/*.jar') {
            $targetPath = Join-Path $libraryPath $entry.Name
            if (-not (Test-Path -LiteralPath $targetPath)) {
                [IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $targetPath, $false)
            }
        }
    }
} finally { $archive.Dispose() }
& java --class-path "$libraryPath/*" (Join-Path $PSScriptRoot 'DatabaseAdmin.java') $Command $projectRoot
if ($LASTEXITCODE -ne 0) { throw "Database command failed (exit $LASTEXITCODE)." }
