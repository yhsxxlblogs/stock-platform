#!/bin/bash
# ==========================================
# Stock Platform 宝塔面板部署脚本
# 适用于 2核2G 服务器
# ==========================================

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
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

# 检查 Docker 是否安装
check_docker() {
    info "检查 Docker 环境..."
    if ! command -v docker &> /dev/null; then
        error "Docker 未安装，请先安装 Docker"
        exit 1
    fi
    if ! command -v docker-compose &> /dev/null; then
        error "Docker Compose 未安装，请先安装 Docker Compose"
        exit 1
    fi
    info "Docker 环境检查通过"
}

# 创建必要目录
setup_directories() {
    info "创建必要目录..."
    mkdir -p uploads/avatars
    mkdir -p logs
    info "目录创建完成"
}

# 设置环境变量
setup_env() {
    info "设置环境变量..."
    if [ ! -f .env ]; then
        cat > .env << EOF
# MySQL 配置
MYSQL_ROOT_PASSWORD=your_secure_password
MYSQL_DATABASE=stock_platform

# 时区设置
TZ=Asia/Shanghai

# JWT 密钥（生产环境请修改）
JWT_SECRET=your_jwt_secret_key_here

# 服务器域名（用于CORS配置）
SERVER_DOMAIN=your-domain.com
EOF
        warn "已创建默认 .env 文件，请修改其中的密码和密钥！"
    else
        info ".env 文件已存在"
    fi
}

# 构建并启动服务
deploy() {
    info "开始部署 Stock Platform..."
    
    # 拉取最新代码
    info "拉取最新代码..."
    git pull origin main || warn "拉取代码失败，使用本地代码"
    
    # 构建并启动
    info "构建 Docker 镜像..."
    docker-compose build --no-cache
    
    info "启动服务..."
    docker-compose up -d
    
    # 等待服务启动
    info "等待服务启动（约60秒）..."
    sleep 60
    
    # 检查服务状态
    info "检查服务状态..."
    docker-compose ps
    
    info "部署完成！"
    info "前端访问地址: http://服务器IP:8080"
    info "后端API地址: http://服务器IP:9090/api"
}

# 更新部署
update() {
    info "开始更新 Stock Platform..."
    
    # 拉取最新代码
    git pull origin main
    
    # 重新构建并启动
    docker-compose down
    docker-compose build --no-cache
    docker-compose up -d
    
    info "更新完成！"
}

# 查看日志
logs() {
    docker-compose logs -f --tail=100
}

# 停止服务
stop() {
    info "停止服务..."
    docker-compose down
    info "服务已停止"
}

# 重启服务
restart() {
    info "重启服务..."
    docker-compose restart
    info "服务已重启"
}

# 清理数据（危险操作）
clean() {
    warn "这将删除所有数据，包括数据库！"
    read -p "确定要继续吗？(yes/no): " confirm
    if [ "$confirm" = "yes" ]; then
        docker-compose down -v
        rm -rf uploads/*
        info "数据已清理"
    else
        info "操作已取消"
    fi
}

# 显示帮助
help() {
    echo "Stock Platform 部署脚本"
    echo ""
    echo "用法: ./deploy.sh [命令]"
    echo ""
    echo "命令:"
    echo "  deploy    首次部署"
    echo "  update    更新部署"
    echo "  logs      查看日志"
    echo "  stop      停止服务"
    echo "  restart   重启服务"
    echo "  clean     清理所有数据（危险）"
    echo "  help      显示帮助"
}

# 主逻辑
case "${1:-deploy}" in
    deploy)
        check_docker
        setup_directories
        setup_env
        deploy
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
    restart)
        restart
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
