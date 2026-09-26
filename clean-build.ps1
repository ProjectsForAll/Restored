# Removes the Gradle build/ folder when files are locked by daemons or open handles.
# Usage: .\clean-build.ps1

$ErrorActionPreference = 'Continue'

$ProjectRoot = $PSScriptRoot
$BuildDir = Join-Path $ProjectRoot 'build'
$Gradlew = Join-Path $ProjectRoot 'gradlew.bat'

Set-Location $ProjectRoot

Write-Host "Stopping Gradle daemons..."
if (Test-Path $Gradlew) {
   & $Gradlew --stop 2>$null
} else {
   Write-Warning "gradlew.bat not found; skipping daemon stop."
}

Start-Sleep -Seconds 2

function Clear-ReadOnlyAttributes {
   param([string]$Path)

   if (-not (Test-Path $Path)) {
      return
   }

   Get-ChildItem -Path $Path -Recurse -Force -ErrorAction SilentlyContinue | ForEach-Object {
      if ($_.Attributes -band [IO.FileAttributes]::ReadOnly) {
         $_.Attributes = $_.Attributes -band (-bnot [IO.FileAttributes]::ReadOnly)
      }
   }
}

function Remove-DirectoryForce {
   param([string]$Path)

   if (-not (Test-Path $Path)) {
      return $true
   }

   Clear-ReadOnlyAttributes -Path $Path

   try {
      Remove-Item -LiteralPath $Path -Recurse -Force -ErrorAction Stop
      return $true
   } catch {
      return $false
   }
}

function Remove-WithRobocopyMirror {
   param([string]$Path)

   $emptyDir = Join-Path $env:TEMP ("opendonut-empty-{0}" -f [guid]::NewGuid().ToString('N'))
   New-Item -ItemType Directory -Path $emptyDir -Force | Out-Null

   try {
      & robocopy $emptyDir $Path /MIR /R:1 /W:1 /NFL /NDL /NJH /NJS | Out-Null
      # robocopy exit codes 0-7 indicate success for mirror operations
      return ($LASTEXITCODE -le 7)
   } finally {
      Remove-Item -LiteralPath $emptyDir -Recurse -Force -ErrorAction SilentlyContinue
   }
}

if (-not (Test-Path $BuildDir)) {
   Write-Host "build directory does not exist."
   exit 0
}

Write-Host "Removing $BuildDir ..."

if (Remove-DirectoryForce -Path $BuildDir) {
   Write-Host "Successfully removed build directory."
   exit 0
}

Write-Host "Direct delete failed; trying robocopy mirror trick..."
Remove-WithRobocopyMirror -Path $BuildDir | Out-Null
Remove-DirectoryForce -Path $BuildDir | Out-Null

if (Test-Path $BuildDir) {
   Write-Host ""
   Write-Host "FAILED: Could not delete build directory." -ForegroundColor Red
   Write-Host "Close any terminal or IDE whose working directory is under build\,"
   Write-Host "stop running Gradle/Java tasks for this project, then run this script again."
   exit 1
}

Write-Host "Successfully removed build directory."
exit 0
