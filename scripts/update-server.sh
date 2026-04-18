#!/bin/bash

# ==========================================
# 服务器自动更新脚本
# 从 GitHub 拉取最新代码并重新部署
# ==========================================

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

print_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# 配置
PROJECT_DIR="/path/to/stock-platform"  # 修改为您的实际路径
COMPOSE_FILE="docker-compose.server.yml"
BACKUP_DIR="./backups"

# 显示帮助
show_help() {
    echo "服务器更新脚本"
    echo ""
    echo "用法: ./update-server.sh [选项]"
    echo ""
    echo "选项:"
    echo "  --help, -h          显示帮助信息"
    echo "  --backup            更新前备份数据库"
    echo "  --no-build          不重新构建镜像（只重启）"
    echo "  --sync-data         更新后同步股票数据"
    echo ""
}

# 解析参数
BACKUP_BEFORE_UPDATE=false
NO_BUILD=false
SYNC_DATA=false

while [[ $# -gt 0 ]]; do
    case $1 in
        --help|-h)
            show_help
            exit 0
            ;;
        --backup)
            BACKUP_BEFORE_UPDATE=true
            shift
            ;;
        --no-build)
            NO_BUILD=true
            shift
            ;;
        --sync-data)
            SYNC_DATA=true
            shift
            ;;
        *)
            print_error "未知参数: $1"
            show_help
            exit 1
            ;;
    esac
done

# 进入项目目录
cd "$PROJECT_DIR" || {
    print_error "无法进入项目目录: $PROJECT_DIR"
    exit 1
}

print_info "开始更新项目..."
print_info "项目目录: $(pwd)"

# 检查 git 状态
if [ ! -d ".git" ]; then
    print_error "当前目录不是 git 仓库"
    exit 1
fi

# 备份数据库（如果需要）
if [ "$BACKUP_BEFORE_UPDATE" = true ]; then
    print_info "备份数据库..."
    mkdir -p "$BACKUP_DIR"
    TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
    docker exec stock-mysql mysqldump -uroot -p${MYSQL_ROOT_PASSWORD:-@Syh20050608} stock_platform > "$BACKUP_DIR/stock_platform_backup_${TIMESTAMP}.sql" 2>/dev/null || {
        print_warning "数据库备份失败，继续更新..."
    }
fi

# 拉取最新代码
print_info "拉取最新代码..."
git fetch origin
git pull origin main

if [ $? -ne 0 ]; then
    print_error "拉取代码失败"
    exit 1
fi

print_success "代码已更新到最新版本"

# 停止旧容器
print_info "停止旧容器..."
docker-compose -f "$COMPOSE_FILE" down

# 重新部署
if [ "$NO_BUILD" = true ]; then
    print_info "启动容器（不重新构建）..."
    docker-compose -f "$COMPOSE_FILE" up -d
else
    print_info "重新构建并启动容器..."
    docker-compose -f "$COMPOSE_FILE" up -d --build
fi

# 等待服务就绪
print_info "等待服务就绪..."
sleep 10

# 检查服务状态
print_info "检查服务状态..."
docker-compose -f "$COMPOSE_FILE" ps

# 同步股票数据（如果需要）
if [ "$SYNC_DATA" = true ]; then
    print_info "同步股票数据..."
    sleep 5
    docker exec -i stock-mysql mysql -uroot -p${MYSQL_ROOT_PASSWORD:-@Syh20050608} stock_platform < scripts/sync_stocks.sql 2>/dev/null || {
        print_warning "股票数据同步失败"
    }
fi

print_success "项目更新完成！"
print_info "查看日志: docker-compose -f $COMPOSE_FILE logs -f"
