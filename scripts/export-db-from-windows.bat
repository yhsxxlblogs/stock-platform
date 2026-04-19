@echo off
chcp 65001 >nul
REM ==========================================
REM Windows Database Export Script
REM Export MySQL database to Linux-compatible format
REM ==========================================

setlocal enabledelayedexpansion

REM Configuration
set "SCRIPT_DIR=%~dp0"
set "DB_HOST=localhost"
set "DB_PORT=3306"
set "DB_NAME=stock_platform"
set "DB_USER=root"
set "DB_PASS=@Syh20050608"
set "OUTPUT_FILE=%SCRIPT_DIR%stock_platform_for_linux.sql"

echo ==========================================
echo Windows MySQL Export Tool (Linux Compatible)
echo ==========================================
echo.
echo [INFO] Exporting database: %DB_NAME%
echo [INFO] Output file: %OUTPUT_FILE%
echo.

REM Check if mysqldump exists
where mysqldump >nul 2>nul
if %errorlevel% neq 0 (
    echo [ERROR] mysqldump not found. Please ensure MySQL is installed and added to PATH.
    pause
    exit /b 1
)

echo [INFO] Exporting database...
echo [INFO] This may take a few minutes, please wait...
echo.

REM Create temp file
set "TEMP_FILE=%TEMP%\stock_platform_temp_%RANDOM%.sql"

REM Run mysqldump
mysqldump --host=%DB_HOST% --port=%DB_PORT% --user=%DB_USER% --password=%DB_PASS% --single-transaction --quick --lock-tables=false --set-charset=utf8mb4 --routines --triggers %DB_NAME% > "%TEMP_FILE%"

if %errorlevel% neq 0 (
    echo [ERROR] mysqldump failed
    if exist "%TEMP_FILE%" del "%TEMP_FILE%"
    pause
    exit /b 1
)

echo [INFO] Converting to UTF-8 without BOM...

REM Create PowerShell script file
set "PS_SCRIPT=%TEMP%\convert_encoding_%RANDOM%.ps1"
(
echo $inputFile = '%TEMP_FILE%'
echo $outputFile = '%OUTPUT_FILE%'
echo $content = [System.IO.File]::ReadAllText($inputFile, [System.Text.Encoding]::Default)
echo $utf8NoBom = New-Object System.Text.UTF8Encoding $false
echo [System.IO.File]::WriteAllText($outputFile, $content, $utf8NoBom)
echo Write-Host '[SUCCESS] Export completed!' -ForegroundColor Green
) > "%PS_SCRIPT%"

REM Run PowerShell script
powershell -ExecutionPolicy Bypass -File "%PS_SCRIPT%"

if %errorlevel% neq 0 (
    echo [ERROR] Encoding conversion failed
    if exist "%TEMP_FILE%" del "%TEMP_FILE%"
    if exist "%PS_SCRIPT%" del "%PS_SCRIPT%"
    pause
    exit /b 1
)

REM Clean up temp files
if exist "%TEMP_FILE%" del "%TEMP_FILE%"
if exist "%PS_SCRIPT%" del "%PS_SCRIPT%"

REM Show file info
echo.
echo [INFO] Export file info:
for %%F in ("%OUTPUT_FILE%") do (
    echo   File: %%~nxF
    echo   Size: %%~zF bytes
    echo   Path: %%~dpF
)

REM Verify file
echo.
echo [INFO] Verifying file format:
powershell -Command "$bytes = [System.IO.File]::ReadAllBytes('%OUTPUT_FILE%'); if ($bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF) { Write-Host '[WARNING] File has BOM header' -ForegroundColor Yellow } else { Write-Host '[OK] File has no BOM header' -ForegroundColor Green }; Write-Host ('[INFO] File size: ' + $bytes.Length + ' bytes')"

REM Show first 5 lines
echo.
echo [INFO] First 5 lines preview:
powershell -Command "Get-Content '%OUTPUT_FILE%' -TotalCount 5"

echo.
echo ==========================================
echo [SUCCESS] Export completed!
echo ==========================================
echo.
echo Next steps:
echo 1. Upload to server:
echo    scp scripts\stock_platform_for_linux.sql root@YOUR_SERVER_IP:/tmp/
echo.
echo 2. Run import script on server:
echo    bash scripts/import-db-to-docker.sh
echo.

pause
