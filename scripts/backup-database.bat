@echo off
chcp 65001 >nul

REM ==========================================
REM 数据库备份脚本 (Windows)
REM 用于备份当前运行的数据库
REM ==========================================

setlocal enabledelayedexpansion

REM 配置
set "BACKUP_DIR=./backups"
set "DB_CONTAINER=stock-mysql"
set "DB_NAME=stock_platform"
set "DB_USER=root"
set "DB_PASS=@Syh20050608"

REM 获取时间戳
for /f "tokens=2-4 delims=/ " %%a in ('date /t') do (set mydate=%%c%%a%%b)
for /f "tokens=1-2 delims=/:" %%a in ('time /t') do (set mytime=%%a%%b)
set "TIMESTAMP=%mydate%_%mytime%"
set "TIMESTAMP=!TIMESTAMP: =0!"
set "BACKUP_FILE=%BACKUP_DIR%/stock_platform_backup_%TIMESTAMP%.sql"

REM 创建备份目录
if not exist "%BACKUP_DIR%" mkdir "%BACKUP_DIR%"

echo [INFO] 开始备份数据库...
echo [INFO] 备份文件: %BACKUP_FILE%

REM 执行备份
docker exec %DB_CONTAINER% mysqldump -u%DB_USER% -p%DB_PASS% --single-transaction --quick --lock-tables=false %DB_NAME% > "%BACKUP_FILE%"

if %errorlevel% equ 0 (
    echo [SUCCESS] 数据库备份成功: %BACKUP_FILE%
    
    REM 显示备份文件大小
    dir "%BACKUP_FILE%"
) else (
    echo [ERROR] 数据库备份失败
    exit /b 1
)

pause
