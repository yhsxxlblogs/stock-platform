@echo off
chcp 65001 >nul

REM ==========================================
REM 数据库迁移脚本 (Windows)
REM 将当前数据库数据迁移到用户新配置的数据库
REM ==========================================

setlocal enabledelayedexpansion

REM 配置
set "SOURCE_CONTAINER=stock-mysql"
set "SOURCE_DB=stock_platform"
set "SOURCE_USER=root"
set "SOURCE_PASS=@Syh20050608"

REM 目标数据库配置
set "TARGET_HOST=localhost"
set "TARGET_PORT=3306"
set "TARGET_DB=stock_platform"
set "TARGET_USER=root"

REM 检查源数据库
echo [INFO] 检查源数据库...
docker ps | findstr "%SOURCE_CONTAINER%" >nul
if errorlevel 1 (
    echo [ERROR] 源数据库容器 %SOURCE_CONTAINER% 未运行
    exit /b 1
)

REM 获取源数据库统计
echo [INFO] 源数据库统计:
docker exec %SOURCE_CONTAINER% mysql -u%SOURCE_USER% -p%SOURCE_PASS% %SOURCE_DB% -e "SELECT 'Users' as table_name, COUNT(*) as count FROM users UNION ALL SELECT 'Stocks', COUNT(*) FROM stocks UNION ALL SELECT 'StockBasic', COUNT(*) FROM stock_basic UNION ALL SELECT 'UserFavorites', COUNT(*) FROM user_favorites;" 2>nul

echo.

REM 输入目标数据库密码
set /p TARGET_PASS="请输入目标数据库密码: "

echo [INFO] 目标数据库: %TARGET_HOST%:%TARGET_PORT%/%TARGET_DB%

REM 测试目标数据库连接
echo [INFO] 测试目标数据库连接...
mysql -h%TARGET_HOST% -P%TARGET_PORT% -u%TARGET_USER% -p%TARGET_PASS% -e "SELECT 1;" >nul 2>&1
if errorlevel 1 (
    echo [ERROR] 无法连接到目标数据库，请检查配置
    exit /b 1
)

echo [SUCCESS] 目标数据库连接成功

REM 确认迁移
echo.
echo [WARNING] 即将迁移数据到目标数据库
set /p confirm="是否继续? (y/n): "
if /i not "%confirm%"=="y" (
    echo [INFO] 已取消迁移
    exit /b 0
)

REM 执行迁移
echo [INFO] 开始迁移数据...

REM 导出数据
echo [INFO] 导出源数据库数据...
set "MIGRATION_FILE=%TEMP%\migration_%RANDOM%.sql"

docker exec %SOURCE_CONTAINER% mysqldump -u%SOURCE_USER% -p%SOURCE_PASS% --single-transaction --quick --lock-tables=false --set-gtid-purged=OFF %SOURCE_DB% > "%MIGRATION_FILE%"

if errorlevel 1 (
    echo [ERROR] 导出数据失败
    exit /b 1
)

echo [SUCCESS] 数据导出完成

REM 导入到目标数据库
echo [INFO] 导入到目标数据库...
mysql -h%TARGET_HOST% -P%TARGET_PORT% -u%TARGET_USER% -p%TARGET_PASS% %TARGET_DB% < "%MIGRATION_FILE%"

if errorlevel 1 (
    echo [ERROR] 导入数据失败
    del "%MIGRATION_FILE%" 2>nul
    exit /b 1
)

REM 清理临时文件
del "%MIGRATION_FILE%" 2>nul

echo [SUCCESS] 数据迁移完成！

REM 验证迁移结果
echo [INFO] 验证迁移结果:
mysql -h%TARGET_HOST% -P%TARGET_PORT% -u%TARGET_USER% -p%TARGET_PASS% %TARGET_DB% -e "SELECT 'Users' as table_name, COUNT(*) as count FROM users UNION ALL SELECT 'Stocks', COUNT(*) FROM stocks UNION ALL SELECT 'StockBasic', COUNT(*) FROM stock_basic UNION ALL SELECT 'UserFavorites', COUNT(*) FROM user_favorites;"

echo.
echo [SUCCESS] 数据库迁移成功完成！
echo [INFO] 请更新 docker-compose.yml 中的数据库配置

pause
