#!/bin/bash

# ==========================================
# MarketPulse 股票平台一键部署脚本
# 支持 Linux / macOS / Windows (Git Bash)
# ==========================================

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 打印带颜色的信息
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

# 检查 Docker 是否安装
check_docker() {
    print_info "检查 Docker 环境..."
    
    if ! command -v docker &> /dev/null; then
        print_error "Docker 未安装，请先安装 Docker"
        print_info "安装指南: https://docs.docker.com/get-docker/"
        exit 1
    fi
    
    if ! command -v docker-compose &> /dev/null; then
        print_error "Docker Compose 未安装，请先安装 Docker Compose"
        print_info "安装指南: https://docs.docker.com/compose/install/"
        exit 1
    fi
    
    # 检查 Docker 是否运行
    if ! docker info &> /dev/null; then
        print_error "Docker 服务未运行，请启动 Docker 服务"
        exit 1
    fi
    
    print_success "Docker 环境检查通过"
}

# 检查端口是否被占用
check_ports() {
    print_info "检查端口占用情况..."
    
    local ports=("80" "9090" "3308" "6380")
    local port_in_use=false
    
    for port in "${ports[@]}"; do
        if lsof -Pi :$port -sTCP:LISTEN -t &> /dev/null || netstat -tuln 2>/dev/null | grep -q ":$port "; then
            print_warning "端口 $port 已被占用"
            port_in_use=true
        fi
    done
    
    if [ "$port_in_use" = true ]; then
        print_warning "部分端口已被占用，可能会导致部署失败"
        read -p "是否继续部署? (y/n): " -n 1 -r
        echo
        if [[ ! $REPLY =~ ^[Yy]$ ]]; then
            print_info "已取消部署"
            exit 0
        fi
    else
        print_success "端口检查通过"
    fi
}

# 检查环境变量文件
check_env() {
    print_info "检查环境变量配置..."
    
    if [ ! -f ".env" ]; then
        print_warning ".env 文件不存在，将使用默认配置"
        print_info "建议复制 .env.example 为 .env 并修改配置"
    else
        print_success "环境变量配置已存在"
    fi
}

# 清理旧容器和镜像
cleanup() {
    print_info "清理旧容器..."
    
    # 停止并删除旧容器
    docker-compose down --remove-orphans 2>/dev/null || true
    
    # 删除未使用的镜像（可选）
    read -p "是否删除旧的 Docker 镜像以节省空间? (y/n): " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        docker-compose rm -f 2>/dev/null || true
        print_success "旧镜像已清理"
    fi
}

# 构建和启动服务
deploy() {
    print_info "开始构建 Docker 镜像..."
    
    # 使用 BuildKit 加速构建
    export COMPOSE_DOCKER_CLI_BUILD=1
    export DOCKER_BUILDKIT=1
    
    # 构建镜像
    docker-compose build --no-cache --parallel
    
    print_success "镜像构建完成"
    print_info "启动服务..."
    
    # 启动服务
    docker-compose up -d
    
    print_success "服务已启动"
}

# 等待服务就绪
wait_for_services() {
    print_info "等待服务就绪..."
    
    local max_attempts=30
    local attempt=1
    
    # 等待 MySQL
    print_info "等待 MySQL 就绪..."
    while [ $attempt -le $max_attempts ]; do
        if docker-compose ps mysql | grep -q "healthy"; then
            print_success "MySQL 已就绪"
            break
        fi
        echo -n "."
        sleep 2
        attempt=$((attempt + 1))
    done
    
    if [ $attempt -gt $max_attempts ]; then
        print_error "MySQL 启动超时，请检查日志: docker-compose logs mysql"
        exit 1
    fi
    
    # 等待后端服务
    attempt=1
    print_info "等待后端服务就绪..."
    while [ $attempt -le $max_attempts ]; do
        if curl -s http://localhost:9090/api/stocks/public/list &> /dev/null; then
            print_success "后端服务已就绪"
            break
        fi
        echo -n "."
        sleep 3
        attempt=$((attempt + 1))
    done
    
    if [ $attempt -gt $max_attempts ]; then
        print_error "后端服务启动超时，请检查日志: docker-compose logs backend"
        exit 1
    fi
    
    # 等待前端服务
    attempt=1
    print_info "等待前端服务就绪..."
    while [ $attempt -le $max_attempts ]; do
        if curl -s http://localhost &> /dev/null; then
            print_success "前端服务已就绪"
            break
        fi
        echo -n "."
        sleep 2
        attempt=$((attempt + 1))
    done
    
    if [ $attempt -gt $max_attempts ]; then
        print_error "前端服务启动超时，请检查日志: docker-compose logs frontend"
        exit 1
    fi
}

# 同步股票数据
sync_stock_data() {
    print_info "检查股票数据同步..."
    
    # 等待MySQL完全就绪
    sleep 5
    
    # 检查stocks表是否为空
    STOCKS_COUNT=$(docker exec stock-mysql mysql -uroot -p${MYSQL_ROOT_PASSWORD:-@Syh20050608} stock_platform -N -e "SELECT COUNT(*) FROM stocks;" 2>/dev/null || echo "0")
    
    if [ "$STOCKS_COUNT" -eq "0" ]; then
        print_info "stocks表为空，正在从stock_basic同步数据..."
        docker exec -i stock-mysql mysql -uroot -p${MYSQL_ROOT_PASSWORD:-@Syh20050608} stock_platform < scripts/sync_stocks.sql 2>/dev/null
        print_success "股票数据同步完成"
    else
        print_info "stocks表已有 $STOCKS_COUNT 条记录，跳过同步"
    fi
}

# 显示部署信息
show_info() {
    echo ""
    echo "=========================================="
    echo -e "${GREEN}      MarketPulse 部署成功！${NC}"
    echo "=========================================="
    echo ""
    echo "访问地址:"
    echo "  - 前端页面: http://localhost"
    echo "  - 后端API:  http://localhost:9090/api"
    echo "  - API文档:  http://localhost:9090/api/swagger-ui.html"
    echo ""
    echo "默认账号:"
    echo "  - 用户名: syh"
    echo "  - 密码:   123456"
    echo ""
    echo "数据管理脚本:"
    echo "  - 备份数据库: ./scripts/backup-database.sh"
    echo "  - 迁移数据库: ./scripts/migrate-database.sh"
    echo "  - 同步股票数据: ./scripts/init-database.sh"
    echo ""
    echo "常用命令:"
    echo "  - 查看日志: docker-compose logs -f"
    echo "  - 停止服务: docker-compose down"
    echo "  - 重启服务: docker-compose restart"
    echo "  - 查看状态: docker-compose ps"
    echo ""
    echo "=========================================="
}

# 主函数
main() {
    echo "=========================================="
    echo "  MarketPulse 股票平台一键部署"
    echo "=========================================="
    echo ""
    
    # 检查是否在项目根目录
    if [ ! -f "docker-compose.yml" ]; then
        print_error "请在项目根目录运行此脚本"
        exit 1
    fi
    
    # 执行检查和部署
    check_docker
    check_ports
    check_env
    cleanup
    deploy
    wait_for_services
    sync_stock_data
    show_info
}

# 处理参数
case "${1:-}" in
    --help|-h)
        echo "MarketPulse 股票平台部署脚本"
        echo ""
        echo "用法: ./deploy.sh [选项]"
        echo ""
        echo "选项:"
        echo "  --help, -h     显示帮助信息"
        echo "  --simple       使用简化配置部署（低配服务器）"
        echo "  --stop         停止所有服务"
        echo "  --restart      重启所有服务"
        echo "  --logs         查看日志"
        echo ""
        exit 0
        ;;
    --simple)
        print_info "使用简化配置部署..."
        export COMPOSE_FILE=docker-compose.simple.yml
        check_docker
        check_ports
        docker-compose up -d --build
        show_info
        exit 0
        ;;
    --stop)
        print_info "停止服务..."
        docker-compose down
        print_success "服务已停止"
        exit 0
        ;;
    --restart)
        print_info "重启服务..."
        docker-compose restart
        print_success "服务已重启"
        exit 0
        ;;
    --logs)
        docker-compose logs -f
        exit 0
        ;;
    *)
        main
        ;;
esac
