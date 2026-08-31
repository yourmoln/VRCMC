#Requires -Version 5.1

<#
.SYNOPSIS
Builds and publishes a VRCMC GitHub release.

.EXAMPLE
.\scripts\release.ps1 1.1.3 -Prerelease

.DESCRIPTION
The script updates all application version declarations, runs tests, builds
the signed Android APK and Windows installer,
commits and tags the release, pushes it, and uploads the artifacts to GitHub.

Authentication is read from GH_TOKEN, GITHUB_TOKEN, or Git Credential Manager.
The repository must be clean and synchronized with its upstream branch.
#>

[CmdletBinding()]
param(
    [Parameter(Mandatory = $true, Position = 0)]
    [ValidatePattern('^\d+\.\d+\.\d+$')]
    [string]$Version,

    [switch]$Prerelease,

    [ValidateScript({ Test-Path -LiteralPath $_ -PathType Leaf })]
    [string]$NotesFile,

    [ValidateScript({ Test-Path -LiteralPath $_ -PathType Leaf })]
    [string]$InnoSetupPath,

    [switch]$SkipTests
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$gradleWrapper = Join-Path $repoRoot 'gradlew.bat'
$versionCatalog = Join-Path $repoRoot 'gradle\libs.versions.toml'
$appInfoFile = Join-Path $repoRoot 'composeApp\src\commonMain\kotlin\com\vrcmc\app\update\AppUpdate.kt'
$installerScript = Join-Path $repoRoot 'installer\VRCMC.iss'
$localProperties = Join-Path $repoRoot 'local.properties'
$tag = "v$Version"
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
$metadataUpdated = $false
$releaseCommitted = $false

function Invoke-External {
    param(
        [Parameter(Mandatory = $true)][string]$FilePath,
        [Parameter()][string[]]$Arguments = @()
    )

    Write-Host "> $FilePath $($Arguments -join ' ')" -ForegroundColor DarkGray
    & $FilePath @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Command failed with exit code ${LASTEXITCODE}: $FilePath"
    }
}

function Get-GitOutput {
    param([Parameter(Mandatory = $true)][string[]]$Arguments)

    $output = & git @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Git command failed: git $($Arguments -join ' ')"
    }
    return @($output)
}

function Set-SingleMatch {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][string]$Pattern,
        [Parameter(Mandatory = $true)][string]$Replacement
    )

    $content = [System.IO.File]::ReadAllText($Path)
    $regex = New-Object System.Text.RegularExpressions.Regex($Pattern, [System.Text.RegularExpressions.RegexOptions]::Multiline)
    if ($regex.Matches($content).Count -ne 1) {
        throw "Expected exactly one version declaration matching '$Pattern' in $Path"
    }
    [System.IO.File]::WriteAllText($Path, $regex.Replace($content, $Replacement), $utf8NoBom)
}

function Get-Properties {
    param([Parameter(Mandatory = $true)][string]$Path)

    $properties = @{}
    foreach ($line in [System.IO.File]::ReadAllLines($Path)) {
        if ($line -match '^\s*([^#!][^=]*?)\s*=\s*(.*)$') {
            $properties[$matches[1].Trim()] = $matches[2].Trim()
        }
    }
    return $properties
}

function Find-InnoSetupCompiler {
    if ($InnoSetupPath) {
        return (Resolve-Path -LiteralPath $InnoSetupPath).Path
    }

    $command = Get-Command 'ISCC.exe' -ErrorAction SilentlyContinue
    if ($command) {
        return $command.Source
    }

    $candidates = @(
        (Join-Path $repoRoot '.gradle\tools\innosetup\ISCC.exe'),
        (Join-Path ${env:ProgramFiles} 'Inno Setup 6\ISCC.exe'),
        (Join-Path ${env:ProgramFiles(x86)} 'Inno Setup 6\ISCC.exe'),
        (Join-Path $env:LOCALAPPDATA 'Programs\Inno Setup 6\ISCC.exe')
    )
    foreach ($candidate in $candidates) {
        if ($candidate -and (Test-Path -LiteralPath $candidate -PathType Leaf)) {
            return (Resolve-Path -LiteralPath $candidate).Path
        }
    }
    throw 'Inno Setup 6 was not found. Install JRSoftware.InnoSetup with winget or pass -InnoSetupPath.'
}

function Get-GitHubToken {
    foreach ($variableName in @('GH_TOKEN', 'GITHUB_TOKEN')) {
        $value = [Environment]::GetEnvironmentVariable($variableName)
        if (-not [string]::IsNullOrWhiteSpace($value)) {
            return $value
        }
    }

    $credentialLines = @('protocol=https', 'host=github.com', '') | git credential fill 2>$null
    if ($LASTEXITCODE -eq 0) {
        foreach ($line in $credentialLines) {
            if ($line -match '^password=(.+)$') {
                return $matches[1]
            }
        }
    }
    throw 'No GitHub credential found. Set GH_TOKEN/GITHUB_TOKEN or sign in through Git Credential Manager.'
}

function Get-GitHubRepository {
    param([Parameter(Mandatory = $true)][string]$RemoteUrl)

    if ($RemoteUrl -match '^(?:https://github\.com/|git@github\.com:)([^/]+)/([^/]+?)(?:\.git)?$') {
        return "$($matches[1])/$($matches[2])"
    }
    throw "The origin remote is not a supported GitHub URL: $RemoteUrl"
}

function New-GitHubHeaders {
    param([Parameter(Mandatory = $true)][string]$Token)

    return @{
        Accept = 'application/vnd.github+json'
        Authorization = "Bearer $Token"
        'X-GitHub-Api-Version' = '2022-11-28'
        'User-Agent' = 'VRCMC-release-script'
    }
}

function Test-GitHubReleaseExists {
    param(
        [Parameter(Mandatory = $true)][string]$Repository,
        [Parameter(Mandatory = $true)][string]$ReleaseTag,
        [Parameter(Mandatory = $true)][hashtable]$Headers
    )

    try {
        Invoke-RestMethod -Method Get -Uri "https://api.github.com/repos/$Repository/releases/tags/$ReleaseTag" -Headers $Headers | Out-Null
        return $true
    }
    catch {
        if ($_.Exception.Response -and [int]$_.Exception.Response.StatusCode -eq 404) {
            return $false
        }
        throw
    }
}

function Publish-GitHubRelease {
    param(
        [Parameter(Mandatory = $true)][string]$Repository,
        [Parameter(Mandatory = $true)][string]$ReleaseTag,
        [Parameter(Mandatory = $true)][string]$TargetBranch,
        [Parameter(Mandatory = $true)][hashtable]$Headers,
        [Parameter(Mandatory = $true)][System.IO.FileInfo[]]$Assets
    )

    $payload = [ordered]@{
        tag_name = $ReleaseTag
        target_commitish = $TargetBranch
        name = $ReleaseTag
        draft = $false
        prerelease = [bool]$Prerelease
    }
    if ($NotesFile) {
        $payload.body = [System.IO.File]::ReadAllText((Resolve-Path -LiteralPath $NotesFile).Path)
    }
    else {
        $payload.generate_release_notes = $true
    }

    $release = Invoke-RestMethod `
        -Method Post `
        -Uri "https://api.github.com/repos/$Repository/releases" `
        -Headers $Headers `
        -ContentType 'application/json; charset=utf-8' `
        -Body ($payload | ConvertTo-Json)

    $uploadBase = $release.upload_url -replace '\{\?name,label\}$', ''
    foreach ($asset in $Assets) {
        Write-Host "Uploading $($asset.Name)..." -ForegroundColor Cyan
        $encodedName = [Uri]::EscapeDataString($asset.Name)
        Invoke-RestMethod `
            -Method Post `
            -Uri "${uploadBase}?name=$encodedName" `
            -Headers $Headers `
            -ContentType 'application/octet-stream' `
            -InFile $asset.FullName | Out-Null
    }
    return $release.html_url
}

Push-Location $repoRoot
try {
    Write-Host "Preparing VRCMC $Version release..." -ForegroundColor Cyan

    $status = @(Get-GitOutput @('status', '--porcelain=v1', '--untracked-files=all'))
    if ($status.Count -ne 0) {
        throw "The working tree is not clean:`n$($status -join [Environment]::NewLine)"
    }

    $branch = (Get-GitOutput @('branch', '--show-current') | Select-Object -First 1)
    if ([string]::IsNullOrWhiteSpace($branch)) {
        throw 'Releases must be created from a branch, not a detached HEAD.'
    }
    $upstream = (Get-GitOutput @('rev-parse', '--abbrev-ref', '--symbolic-full-name', '@{upstream}') | Select-Object -First 1)
    Invoke-External git @('fetch', 'origin', '--tags', '--prune')
    $aheadBehind = ((Get-GitOutput @('rev-list', '--left-right', '--count', "HEAD...$upstream")) -join ' ').Trim() -split '\s+'
    if ($aheadBehind.Count -ne 2 -or $aheadBehind[0] -ne '0' -or $aheadBehind[1] -ne '0') {
        throw "The current branch must be synchronized with $upstream before releasing (ahead=$($aheadBehind[0]), behind=$($aheadBehind[1]))."
    }
    if ((& git rev-parse --verify --quiet "refs/tags/$tag") -or $LASTEXITCODE -eq 0) {
        throw "Tag $tag already exists."
    }

    $originUrl = (Get-GitOutput @('remote', 'get-url', 'origin') | Select-Object -First 1)
    $repository = Get-GitHubRepository $originUrl
    $token = Get-GitHubToken
    $githubHeaders = New-GitHubHeaders $token
    Invoke-RestMethod -Method Get -Uri 'https://api.github.com/user' -Headers $githubHeaders | Out-Null
    if (Test-GitHubReleaseExists $repository $tag $githubHeaders) {
        throw "GitHub Release $tag already exists."
    }

    if (-not (Test-Path -LiteralPath $localProperties -PathType Leaf)) {
        throw 'local.properties is required for Android release signing.'
    }
    $signingProperties = Get-Properties $localProperties
    foreach ($key in @('store_file', 'store_pass', 'key_alias', 'key_pass')) {
        if (-not $signingProperties.ContainsKey($key) -or [string]::IsNullOrWhiteSpace($signingProperties[$key])) {
            throw "Missing Android signing property '$key' in local.properties."
        }
    }
    $keyStorePath = $signingProperties['store_file']
    if (-not [System.IO.Path]::IsPathRooted($keyStorePath)) {
        $keyStorePath = Join-Path $repoRoot $keyStorePath
    }
    if (-not (Test-Path -LiteralPath $keyStorePath -PathType Leaf)) {
        throw "Android signing keystore was not found: $keyStorePath"
    }
    $iscc = Find-InnoSetupCompiler

    $catalogContent = [System.IO.File]::ReadAllText($versionCatalog)
    $currentVersionMatch = [regex]::Match($catalogContent, '(?m)^app-version\s*=\s*"(\d+\.\d+\.\d+)"\s*$')
    if (-not $currentVersionMatch.Success) {
        throw 'Could not read the current application version from the Gradle version catalog.'
    }
    $currentVersion = $currentVersionMatch.Groups[1].Value
    if ([version]$Version -lt [version]$currentVersion) {
        throw "Release version $Version cannot be older than $currentVersion."
    }
    if ([version]$Version -eq [version]$currentVersion) {
        Write-Host "Version declarations already target $Version; verifying them..." -ForegroundColor Cyan
    }
    else {
        Write-Host "Updating version declarations ($currentVersion -> $Version)..." -ForegroundColor Cyan
    }
    Set-SingleMatch $versionCatalog '^app-version\s*=\s*"[^"]+"\s*$' "app-version = `"$Version`""
    Set-SingleMatch $appInfoFile '^\s*const val VERSION\s*=\s*"[^"]+"\s*$' "    const val VERSION = `"$Version`""
    Set-SingleMatch $installerScript '^#define AppVersion\s+"[^"]+"\s*$' "#define AppVersion `"$Version`""
    $metadataUpdated = $true

    if (-not $SkipTests) {
        Write-Host 'Running desktop and Android unit tests...' -ForegroundColor Cyan
        Invoke-External $gradleWrapper @(':composeApp:desktopTest', ':composeApp:testDebugUnitTest')
    }

    Write-Host 'Building signed Android APK...' -ForegroundColor Cyan
    Invoke-External $gradleWrapper @(':composeApp:assembleRelease')
    $apk = Get-Item -LiteralPath (Join-Path $repoRoot "composeApp\build\outputs\apk\release\VRCMC-v$Version.apk")

    Write-Host 'Building Windows application and installer...' -ForegroundColor Cyan
    Invoke-External $gradleWrapper @(':composeApp:createReleaseDistributable')
    Invoke-External $iscc @((Join-Path $repoRoot 'installer\VRCMC.iss'))
    $installer = Get-Item -LiteralPath (Join-Path $repoRoot "composeApp\build\installer\VRCMC-v$Version-setup.exe")

    $releaseDirectory = Join-Path $repoRoot 'composeApp\build\release'
    New-Item -ItemType Directory -Path $releaseDirectory -Force | Out-Null
    $checksumsPath = Join-Path $releaseDirectory 'SHA256SUMS.txt'
    $checksumLines = @($apk, $installer) | ForEach-Object {
        $hash = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
        "$hash  $($_.Name)"
    }
    [System.IO.File]::WriteAllLines($checksumsPath, [string[]]$checksumLines, $utf8NoBom)
    $checksums = Get-Item -LiteralPath $checksumsPath

    $metadataStatus = @(Get-GitOutput @('status', '--porcelain=v1', '--', $versionCatalog, $appInfoFile, $installerScript))
    if ($metadataStatus.Count -ne 0) {
        Write-Host 'Committing release metadata...' -ForegroundColor Cyan
        Invoke-External git @('add', '--', $versionCatalog, $appInfoFile, $installerScript)
        Invoke-External git @('commit', '-m', "release: $tag")
    }
    else {
        Write-Host 'Version metadata is already committed; tagging the current commit...' -ForegroundColor Cyan
    }
    $releaseCommitted = $true
    Invoke-External git @('tag', '-a', $tag, '-m', "VRCMC $Version")
    Invoke-External git @('push', 'origin', "HEAD:refs/heads/$branch", "refs/tags/$tag")

    $releaseUrl = Publish-GitHubRelease `
        -Repository $repository `
        -ReleaseTag $tag `
        -TargetBranch $branch `
        -Headers $githubHeaders `
        -Assets @($apk, $installer, $checksums)

    Write-Host "Published ${tag}: $releaseUrl" -ForegroundColor Green
}
catch {
    if ($metadataUpdated -and -not $releaseCommitted) {
        Write-Warning 'Release failed before commit; restoring version metadata.'
        & git restore --staged --worktree -- $versionCatalog $appInfoFile $installerScript 2>$null
    }
    throw
}
finally {
    Pop-Location
}
