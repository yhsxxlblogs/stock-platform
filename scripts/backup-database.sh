#!/bin/bash

# ==========================================
# 数据库备份脚本
# 用于备份当前运行的数据库
# ==========================================

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# 配置
BACKUP_DIR="./backups"
DB_CONTAINER="stock-mysql"
DB_NAME="stock_platform"
DB_USER="root"
DB_PASS="${MYSQL_ROOT_PASSWORD:-@Syh20050608}"
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
BACKUP_FILE="${BACKUP_DIR}/stock_platform_backup_${TIMESTAMP}.sql"

# 打印信息
print_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# 创建备份目录
mkdir -p "$BACKUP_DIR"

print_info "开始备份数据库..."
print_info "备份文件: $BACKUP_FILE"

# 执行备份
docker exec "$DB_CONTAINER" mysqldump -u"$DB_USER" -p"$DB_PASS" \
    --single-transaction \
    --quick \
    --lock-tables=false \
    "$DB_NAME" > "$BACKUP_FILE"

if [ $? -eq 0 ]; then
    # 压缩备份文件
    gzip "$BACKUP_FILE"
    print_success "数据库备份成功: ${BACKUP_FILE}.gz"
    
    # 显示备份文件大小
    ls -lh "${BACKUP_FILE}.gz"
    
    # 清理旧备份（保留最近7天）
    print_info "清理旧备份文件..."
    find "$BACKUP_DIR" -name "stock_platform_backup_*.sql.gz" -mtime +7 -delete
    print_success "清理完成"
else
    print_error "数据库备份失败"
    exit 1
fi
