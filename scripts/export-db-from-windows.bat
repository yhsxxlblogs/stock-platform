@echo off
chcp 65001 >nul
REM ==========================================
REM Windows 数据库导出脚本
REM 在本机 Windows 上执行，导出 MySQL 数据库为 Linux 兼容格式
REM ==========================================

setlocal enabledelayedexpansion

REM 配置
set "SCRIPT_DIR=%~dp0"
set "DB_HOST=localhost"
set "DB_PORT=3306"
set "DB_NAME=stock_platform"
set "DB_USER=root"
set "DB_PASS=@Syh20050608"
set "OUTPUT_FILE=%SCRIPT_DIR%stock_platform_for_linux.sql"

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
echo [INFO] 这可能需要几分钟，请耐心等待...
echo.

powershell -ExecutionPolicy Bypass -Command "
    $ErrorActionPreference = 'Stop'
    
    $outputFile = '%OUTPUT_FILE%'
    $tempFile = [System.IO.Path]::GetTempFileName()
    
    try {
        # 执行 mysqldump，直接输出到临时文件
        $psi = New-Object System.Diagnostics.ProcessStartInfo
        $psi.FileName = 'mysqldump'
        $psi.Arguments = @(
            '--host=%DB_HOST%',
            '--port=%DB_PORT%',
            '--user=%DB_USER%',
            '--password=%DB_PASS%',
            '--single-transaction',
            '--quick',
            '--lock-tables=false',
            '--set-charset=utf8mb4',
            '--skip-comments',
            '--routines',
            '--triggers',
            '%DB_NAME%'
        ) -join ' '
        $psi.RedirectStandardOutput = $true
        $psi.RedirectStandardError = $true
        $psi.UseShellExecute = $false
        $psi.CreateNoWindow = $true
        
        $process = [System.Diagnostics.Process]::Start($psi)
        $stdout = $process.StandardOutput.ReadToEnd()
        $stderr = $process.StandardError.ReadToEnd()
        $process.WaitForExit()
        
        if ($process.ExitCode -ne 0) {
            Write-Host '[ERROR] mysqldump 执行失败:' -ForegroundColor Red
            Write-Host $stderr -ForegroundColor Red
            exit 1
        }
        
        # 以 UTF-8 无 BOM 格式写入文件
        $utf8NoBom = New-Object System.Text.UTF8Encoding $false
        [System.IO.File]::WriteAllText($outputFile, $stdout, $utf8NoBom)
        
        Write-Host '[SUCCESS] 数据库导出成功!' -ForegroundColor Green
        
    } catch {
        Write-Host '[ERROR] 导出失败:' -ForegroundColor Red
        Write-Host $_.Exception.Message -ForegroundColor Red
        exit 1
    }
"

if %errorlevel% neq 0 (
    echo [ERROR] 导出失败
    pause
    exit /b 1
)

REM 显示文件信息
echo.
echo [INFO] 导出文件信息:
for %%F in ("%OUTPUT_FILE%") do (
    echo   文件名: %%~nxF
    echo   大小: %%~zF 字节
    echo   路径: %%~dpF
)

REM 验证文件内容
echo.
echo [INFO] 验证文件格式:
powershell -Command "
    $bytes = [System.IO.File]::ReadAllBytes('%OUTPUT_FILE%')
    if ($bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF) {
        Write-Host '[WARNING] 文件包含 BOM 头' -ForegroundColor Yellow
    } else {
        Write-Host '[OK] 文件无 BOM 头' -ForegroundColor Green
    }
    Write-Host ('[INFO] 文件大小: ' + $bytes.Length + ' 字节')
"

REM 显示文件前5行
echo.
echo [INFO] 文件前 5 行预览:
powershell -Command "Get-Content '%OUTPUT_FILE%' -TotalCount 5"

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
