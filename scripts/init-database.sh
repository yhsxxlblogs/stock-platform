#!/bin/bash

# ==========================================
# 数据库初始化脚本
# 在首次部署时自动同步 stock_basic 到 stocks
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

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# 配置
DB_CONTAINER="stock-mysql"
DB_NAME="stock_platform"
DB_USER="root"
DB_PASS="${MYSQL_ROOT_PASSWORD:-@Syh20050608}"

print_info "数据库初始化..."

# 等待数据库就绪
print_info "等待数据库就绪..."
for i in {1..30}; do
    if docker exec "$DB_CONTAINER" mysql -u"$DB_USER" -p"$DB_PASS" -e "SELECT 1;" > /dev/null 2>&1; then
        print_success "数据库已就绪"
        break
    fi
    echo -n "."
    sleep 2
    
    if [ $i -eq 30 ]; then
        print_error "数据库连接超时"
        exit 1
    fi
done

# 检查 stocks 表是否为空
STOCKS_COUNT=$(docker exec "$DB_CONTAINER" mysql -u"$DB_USER" -p"$DB_PASS" "$DB_NAME" -N -e "SELECT COUNT(*) FROM stocks;" 2>/dev/null || echo "0")

if [ "$STOCKS_COUNT" -eq "0" ]; then
    print_info "stocks 表为空，开始从 stock_basic 同步数据..."
    
    # 执行同步
    docker exec "$DB_CONTAINER" mysql -u"$DB_USER" -p"$DB_PASS" "$DB_NAME" -e "
        INSERT INTO stocks (symbol, name, exchange, industry, status, created_at, updated_at)
        SELECT 
            sb.symbol,
            sb.name,
            sb.exchange,
            sb.industry,
            1 as status,
            NOW() as created_at,
            NOW() as updated_at
        FROM stock_basic sb
        LEFT JOIN stocks s ON sb.symbol = s.symbol
        WHERE s.id IS NULL;
    " 2>/dev/null
    
    if [ $? -eq 0 ]; then
        NEW_COUNT=$(docker exec "$DB_CONTAINER" mysql -u"$DB_USER" -p"$DB_PASS" "$DB_NAME" -N -e "SELECT COUNT(*) FROM stocks;" 2>/dev/null)
        print_success "数据同步完成，stocks 表现在有 $NEW_COUNT 条记录"
    else
        print_error "数据同步失败"
    fi
else
    print_info "stocks 表已有 $STOCKS_COUNT 条记录，跳过同步"
fi

print_success "数据库初始化完成"
