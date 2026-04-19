@echo off
chcp 65001 >nul
REM ==========================================
REM Windows 数据库导出脚本
REM 在本机 Windows 上执行，导出 MySQL 数据库为 Linux 兼容格式
REM ==========================================

setlocal enabledelayedexpansion

REM 配置
set "BACKUP_DIR=%~dp0"
set "DB_HOST=localhost"
set "DB_PORT=3306"
set "DB_NAME=stock_platform"
set "DB_USER=root"
set "DB_PASS=@Syh20050608"
set "OUTPUT_FILE=%BACKUP_DIR%stock_platform_for_linux.sql"

echo ==========================================
echo  Windows MySQL 导出工具（Linux兼容版）
echo ==========================================
echo.
echo [INFO] 导出数据库: %DB_NAME%
echo [INFO] 目标文件: %OUTPUT_FILE%
echo.

REM 检查 mysqldump 是否存在
where mysqldump >nul 2>nul
if %errorlevel% neq 0 (
    echo [ERROR] 未找到 mysqldump，请确保 MySQL 已安装并添加到环境变量
    pause
    exit /b 1
)

REM 使用 PowerShell 导出，确保 UTF-8 无 BOM 格式
echo [INFO] 正在导出数据库...

powershell -Command "
    $ErrorActionPreference = 'Stop'
    
    # 执行 mysqldump
    $process = Start-Process -FilePath 'mysqldump' -ArgumentList @(
        '--host=%DB_HOST%',
        '--port=%DB_PORT%',
        '--user=%DB_USER%',
        '--password=%DB_PASS%',
        '--single-transaction',
        '--quick',
        '--lock-tables=false',
        '--set-charset=utf8mb4',
        '--skip-comments',
        '%DB_NAME%'
    ) -RedirectStandardOutput '$env:TEMP\stock_platform_raw.sql' -Wait -PassThru -NoNewWindow
    
    if ($process.ExitCode -ne 0) {
        throw 'mysqldump 执行失败'
    }
    
    # 读取内容并以 UTF-8 无 BOM 格式写入
    $content = Get-Content -Path '$env:TEMP\stock_platform_raw.sql' -Raw -Encoding UTF8
    
    # 移除 BOM 如果存在
    $utf8NoBom = New-Object System.Text.UTF8Encoding $false
    [System.IO.File]::WriteAllText('%OUTPUT_FILE%', $content, $utf8NoBom)
    
    # 清理临时文件
    Remove-Item '$env:TEMP\stock_platform_raw.sql' -ErrorAction SilentlyContinue
    
    Write-Host '[SUCCESS] 数据库导出成功!' -ForegroundColor Green
"

if %errorlevel% neq 0 (
    echo [ERROR] 导出失败
    pause
    exit /b 1
)

REM 显示文件信息
echo.
echo [INFO] 导出文件信息:
dir "%OUTPUT_FILE%" | findstr "stock_platform_for_linux"

REM 显示文件前几行验证
echo.
echo [INFO] 文件前 5 行预览:
head -n 5 "%OUTPUT_FILE%" 2>nul || powershell -Command "Get-Content '%OUTPUT_FILE%' -Head 5"

echo.
echo ==========================================
echo [SUCCESS] 导出完成！
echo ==========================================
echo.
echo 下一步：
echo 1. 将此文件上传到服务器：
echo    scp scripts\stock_platform_for_linux.sql root@你的服务器IP:/tmp/
echo.
echo 2. 在服务器上执行导入脚本：
echo    bash scripts/import-db-to-docker.sh
echo.

pause
