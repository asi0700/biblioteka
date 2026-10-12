param([switch]$Setup, [switch]$Demo)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot

try {
    Set-Location -LiteralPath $projectRoot
    $javaCommand = if ($env:JAVA_HOME -and (Test-Path -LiteralPath "$env:JAVA_HOME\bin\java.exe")) {
        "$env:JAVA_HOME\bin\java.exe"
    } else {
        (Get-Command java.exe -ErrorAction Stop).Source
    }

    $frontendJar = Join-Path $projectRoot 'frontend\target\library-frontend-1.0-SNAPSHOT.jar'
    $backendJar = Join-Path $projectRoot 'backend\target\library-backend-1.0-SNAPSHOT.jar'
    $dependencies = Join-Path $projectRoot 'frontend\target\dependency'
    if (!(Test-Path -LiteralPath $frontendJar) -or !(Test-Path -LiteralPath $backendJar) -or !(Test-Path -LiteralPath "$dependencies\postgresql-42.7.7.jar")) {
        throw 'Build the project first. See docs/local-run.md.'
    }
    $classPath = "$frontendJar;$backendJar;$dependencies\*"
    $envFile = Join-Path $projectRoot '.env'

    if ($Setup) {
        & $javaCommand '-cp' $classPath 'ru.library.bootstrap.LocalSetup' $envFile
        exit $LASTEXITCODE
    }

    if (!(Test-Path -LiteralPath $envFile)) {
        throw 'Database connection is not configured. Run setup-db.cmd first.'
    }
    $keys = @('LIBRARY_DB_URL', 'LIBRARY_DB_USER', 'LIBRARY_DB_PASSWORD', 'LIBRARY_ZONE')
    $values = @{}
    foreach ($rawLine in Get-Content -LiteralPath $envFile -Encoding UTF8) {
        $line = $rawLine.Replace([string][char]0xFEFF, '').Trim()
        if (!$line -or $line.StartsWith('#')) { continue }
        $separator = $line.IndexOf('=')
        if ($separator -lt 1) { throw 'Invalid .env format: expected NAME=value.' }
        $key = $line.Substring(0, $separator).Trim()
        if ($key -notin $keys) { continue }
        $value = $line.Substring($separator + 1).Trim()
        if ($value.Length -ge 2 -and (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'")))) {
            $value = $value.Substring(1, $value.Length - 2)
        }
        $values[$key] = $value
    }
    foreach ($key in @('LIBRARY_DB_URL', 'LIBRARY_DB_USER', 'LIBRARY_DB_PASSWORD')) {
        if ([string]::IsNullOrWhiteSpace($values[$key])) { throw "Missing $key in .env. Run setup-db.cmd." }
    }
    if ([string]::IsNullOrWhiteSpace($values['LIBRARY_ZONE'])) { $values['LIBRARY_ZONE'] = 'Europe/Moscow' }
    foreach ($key in $keys) {
        [Environment]::SetEnvironmentVariable($key, $values[$key], 'Process')
    }
    $mainClass = if ($Demo) { 'ru.library.bootstrap.DemoDataLoader' } else { 'ru.library.frontend.Launcher' }
    & $javaCommand '-cp' $classPath $mainClass
    exit $LASTEXITCODE
} catch {
    [Console]::Error.WriteLine($_.Exception.Message)
    exit 1
}
