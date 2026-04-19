#!/bin/bash
# ==========================================
# Stock Platform 本地开发部署脚本
# 适用于 Windows(WSL)/Mac/Linux
# ==========================================

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 打印带颜色的信息
info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

log() {
    echo -e "${BLUE}[LOG]${NC} $1"
}

# 检查 Docker 是否安装
check_docker() {
    info "检查 Docker 环境..."
    if ! command -v docker &> /dev/null; then
        error "Docker 未安装，请先安装 Docker"
        echo "安装指南: https://docs.docker.com/get-docker/"
        exit 1
    fi
    if ! command -v docker-compose &> /dev/null; then
        error "Docker Compose 未安装，请先安装 Docker Compose"
        exit 1
    fi
    
    # 检查 Docker 是否运行
    if ! docker info &> /dev/null; then
        error "Docker 服务未启动，请先启动 Docker"
        echo "Windows: 启动 Docker Desktop"
        echo "Mac: 启动 Docker Desktop"
        echo "Linux: sudo systemctl start docker"
        exit 1
    fi
    
    info "Docker 环境检查通过 ✓"
}

# 创建必要目录
setup_directories() {
    info "创建必要目录..."
    mkdir -p uploads/avatars
    mkdir -p logs
    info "目录创建完成 ✓"
}

# 设置环境变量
setup_env() {
    info "设置环境变量..."
    if [ ! -f .env ]; then
        cat > .env << EOF
# ==========================================
# 本地开发环境配置
# ==========================================

# MySQL 配置
MYSQL_ROOT_PASSWORD=@Syh20050608
MYSQL_DATABASE=stock_platform

# 时区设置
TZ=Asia/Shanghai

# JWT 密钥（本地开发使用默认即可）
JWT_SECRET=stockPlatformSecretKey2024LocalDev

# Token 过期时间（毫秒）
JWT_EXPIRATION=86400000
EOF
        info "已创建默认 .env 文件"
    else
        info ".env 文件已存在"
    fi
}

# 构建并启动服务
deploy() {
    info "================================"
    info "开始本地部署 Stock Platform"
    info "================================"
    
    # 构建并启动
    info "构建 Docker 镜像（首次需要较长时间）..."
    docker-compose -f docker-compose.local.yml build --no-cache
    
    info "启动服务..."
    docker-compose -f docker-compose.local.yml up -d
    
    # 等待服务启动
    info "等待服务启动（约60-90秒）..."
    echo ""
    
    # 显示进度
    for i in {1..6}; do
        echo -n "."
        sleep 10
    done
    echo ""
    
    # 检查服务状态
    info "检查服务状态..."
    docker-compose -f docker-compose.local.yml ps
    
    echo ""
    info "================================"
    info "🎉 本地部署完成！"
    info "================================"
    echo ""
    log "访问地址:"
    log "  🌐 前端页面: http://localhost:8080"
    log "  🔌 后端API:  http://localhost:9090/api"
    log "  📊 API文档:  http://localhost:9090/api/swagger-ui.html"
    log "  🗄️  MySQL:    localhost:3306"
    log "  💾 Redis:    localhost:6379"
    echo ""
    log "常用命令:"
    log "  查看日志: docker-compose -f docker-compose.local.yml logs -f"
    log "  停止服务: docker-compose -f docker-compose.local.yml down"
    log "  重启服务: docker-compose -f docker-compose.local.yml restart"
    echo ""
}

# 更新部署
update() {
    info "开始更新 Stock Platform..."
    
    # 重新构建并启动
    docker-compose -f docker-compose.local.yml down
    docker-compose -f docker-compose.local.yml build --no-cache
    docker-compose -f docker-compose.local.yml up -d
    
    info "更新完成！"
}

# 快速启动（不重建镜像）
start() {
    info "快速启动服务..."
    docker-compose -f docker-compose.local.yml up -d
    info "服务已启动"
    log "访问: http://localhost:8080"
}

# 查看日志
logs() {
    docker-compose -f docker-compose.local.yml logs -f --tail=100
}

# 停止服务
stop() {
    info "停止服务..."
    docker-compose -f docker-compose.local.yml down
    info "服务已停止"
}

# 完全清理（包括数据）
clean() {
    warn "这将删除所有容器和数据卷！"
    read -p "确定要继续吗？(yes/no): " confirm
    if [ "$confirm" = "yes" ]; then
        docker-compose -f docker-compose.local.yml down -v
        rm -rf uploads/*
        info "数据已清理"
    else
        info "操作已取消"
    fi
}

# 显示帮助
help() {
    echo "Stock Platform 本地部署脚本"
    echo ""
    echo "用法: ./deploy-local.sh [命令]"
    echo ""
    echo "命令:"
    echo "  deploy    首次部署（构建镜像并启动）"
    echo "  start     快速启动（使用已有镜像）"
    echo "  update    更新部署（重建镜像）"
    echo "  logs      查看日志"
    echo "  stop      停止服务"
    echo "  clean     清理所有数据（危险）"
    echo "  help      显示帮助"
    echo ""
    echo "示例:"
    echo "  ./deploy-local.sh deploy    # 首次部署"
    echo "  ./deploy-local.sh logs      # 查看日志"
}

# 主逻辑
case "${1:-deploy}" in
    deploy)
        check_docker
        setup_directories
        setup_env
        deploy
        ;;
    start)
        check_docker
        start
        ;;
    update)
        check_docker
        update
        ;;
    logs)
        logs
        ;;
    stop)
        stop
        ;;
    clean)
        clean
        ;;
    help)
        help
        ;;
    *)
        error "未知命令: $1"
        help
        exit 1
        ;;
esac
