@echo off
chcp 65001 >nul

REM ==========================================
REM MarketPulse 股票平台一键部署脚本 (Windows)
REM ==========================================

echo ==========================================
echo   MarketPulse 股票平台一键部署
echo ==========================================
echo.

REM 检查是否在项目根目录
if not exist "docker-compose.yml" (
    echo [ERROR] 请在项目根目录运行此脚本
    exit /b 1
)

REM 检查 Docker 是否安装
echo [INFO] 检查 Docker 环境...
docker --version >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Docker 未安装，请先安装 Docker
    echo [INFO] 安装指南: https://docs.docker.com/get-docker/
    exit /b 1
)

docker-compose --version >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Docker Compose 未安装，请先安装 Docker Compose
    echo [INFO] 安装指南: https://docs.docker.com/compose/install/
    exit /b 1
)

REM 检查 Docker 是否运行
docker info >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Docker 服务未运行，请启动 Docker 服务
    exit /b 1
)

echo [SUCCESS] Docker 环境检查通过
echo.

REM 检查端口占用
echo [INFO] 检查端口占用情况...
netstat -ano | findstr ":80 " >nul && (
    echo [WARNING] 端口 80 已被占用
)
netstat -ano | findstr ":9090 " >nul && (
    echo [WARNING] 端口 9090 已被占用
)
netstat -ano | findstr ":3308 " >nul && (
    echo [WARNING] 端口 3308 已被占用
)
netstat -ano | findstr ":6380 " >nul && (
    echo [WARNING] 端口 6380 已被占用
)

echo.
echo [INFO] 如果端口被占用，可能会导致部署失败
echo.

REM 检查环境变量文件
if not exist ".env" (
    echo [WARNING] .env 文件不存在，将使用默认配置
    echo [INFO] 建议复制 .env.example 为 .env 并修改配置
) else (
    echo [SUCCESS] 环境变量配置已存在
)

echo.

REM 清理旧容器
echo [INFO] 清理旧容器...
docker-compose down --remove-orphans >nul 2>&1

REM 询问是否删除旧镜像
echo.
set /p cleanup="是否删除旧的 Docker 镜像以节省空间? (y/n): "
if /i "%cleanup%"=="y" (
    docker-compose rm -f >nul 2>&1
    echo [SUCCESS] 旧镜像已清理
)

echo.

REM 构建和启动服务
echo [INFO] 开始构建 Docker 镜像...

REM 使用 BuildKit 加速构建
set COMPOSE_DOCKER_CLI_BUILD=1
set DOCKER_BUILDKIT=1

docker-compose build --no-cache --parallel
if errorlevel 1 (
    echo [ERROR] 镜像构建失败
    exit /b 1
)

echo [SUCCESS] 镜像构建完成
echo [INFO] 启动服务...

docker-compose up -d
if errorlevel 1 (
    echo [ERROR] 服务启动失败
    exit /b 1
)

echo [SUCCESS] 服务已启动
echo.

REM 等待服务就绪
echo [INFO] 等待服务就绪...
echo [INFO] 等待 MySQL 就绪...
:wait_mysql
timeout /t 2 /nobreak >nul
docker-compose ps mysql | findstr "healthy" >nul
if errorlevel 1 (
    echo|set /p=.
    goto wait_mysql
)
echo.
echo [SUCCESS] MySQL 已就绪

echo [INFO] 等待后端服务就绪...
:wait_backend
timeout /t 3 /nobreak >nul
curl -s http://localhost:9090/api/stocks/public/list >nul 2>&1
if errorlevel 1 (
    echo|set /p=.
    goto wait_backend
)
echo.
echo [SUCCESS] 后端服务已就绪

echo [INFO] 等待前端服务就绪...
:wait_frontend
timeout /t 2 /nobreak >nul
curl -s http://localhost >nul 2>&1
if errorlevel 1 (
    echo|set /p=.
    goto wait_frontend
)
echo.
echo [SUCCESS] 前端服务已就绪

REM 同步股票数据
echo [INFO] 检查股票数据同步...
timeout /t 5 /nobreak >nul

REM 检查stocks表是否为空
for /f "tokens=*" %%a in ('docker exec stock-mysql mysql -uroot -p@Syh20050608 stock_platform -N -e "SELECT COUNT(*) FROM stocks;" 2^>nul') do set STOCKS_COUNT=%%a

if "%STOCKS_COUNT%"=="0" (
    echo [INFO] stocks表为空，正在从stock_basic同步数据...
    type scripts\sync_stocks.sql | docker exec -i stock-mysql mysql -uroot -p@Syh20050608 stock_platform 2>nul
    echo [SUCCESS] 股票数据同步完成
) else (
    echo [INFO] stocks表已有 %STOCKS_COUNT% 条记录，跳过同步
)

echo.
echo ==========================================
echo        MarketPulse 部署成功！
echo ==========================================
echo.
echo 访问地址:
echo   - 前端页面: http://localhost
echo   - 后端API:  http://localhost:9090/api
echo   - API文档:  http://localhost:9090/api/swagger-ui.html
echo.
echo 默认账号:
echo   - 用户名: syh
echo   - 密码:   123456
echo.
echo 数据管理脚本:
echo   - 备份数据库: .\scripts\backup-database.bat
echo   - 迁移数据库: .\scripts\migrate-database.bat
echo   - 同步股票数据: .\scripts\init-database.bat
echo.
echo 常用命令:
echo   - 查看日志: docker-compose logs -f
echo   - 停止服务: docker-compose down
echo   - 重启服务: docker-compose restart
echo   - 查看状态: docker-compose ps
echo.
echo ==========================================

pause
