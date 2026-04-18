#!/bin/bash

# ==========================================
# 零停机更新脚本
# 使用 Docker 滚动更新，不中断服务
# ==========================================

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

print_info() { echo -e "${BLUE}[INFO]${NC} $1"; }
print_success() { echo -e "${GREEN}[SUCCESS]${NC} $1"; }
print_warning() { echo -e "${YELLOW}[WARNING]${NC} $1"; }
print_error() { echo -e "${RED}[ERROR]${NC} $1"; }

COMPOSE_FILE="docker-compose.server.yml"

print_info "开始零停机更新..."

# 1. 拉取最新代码
print_info "拉取最新代码..."
git pull origin main

# 2. 构建新镜像（不停止旧容器）
print_info "构建新镜像..."
docker-compose -f "$COMPOSE_FILE" build

# 3. 逐个重启服务（保持服务可用）
print_info "更新后端服务..."
docker-compose -f "$COMPOSE_FILE" up -d --no-deps --build backend

print_info "等待后端就绪..."
sleep 15

print_info "更新前端服务..."
docker-compose -f "$COMPOSE_FILE" up -d --no-deps --build frontend

print_info "清理旧镜像..."
docker image prune -f

print_success "零停机更新完成！"
