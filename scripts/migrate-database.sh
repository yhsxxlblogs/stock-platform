#!/bin/bash

# ==========================================
# 数据库迁移脚本
# 将当前数据库数据迁移到用户新配置的数据库
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
SOURCE_CONTAINER="stock-mysql"
SOURCE_DB="stock_platform"
SOURCE_USER="root"
SOURCE_PASS="${MYSQL_ROOT_PASSWORD:-@Syh20050608}"

# 目标数据库配置（从环境变量读取）
TARGET_HOST="${TARGET_DB_HOST:-localhost}"
TARGET_PORT="${TARGET_DB_PORT:-3306}"
TARGET_DB="${TARGET_DB_NAME:-stock_platform}"
TARGET_USER="${TARGET_DB_USER:-root}"
TARGET_PASS="${TARGET_DB_PASSWORD:-}"

print_info "数据库迁移工具"
print_info "=============="
echo ""

# 检查源数据库
print_info "检查源数据库..."
if ! docker ps | grep -q "$SOURCE_CONTAINER"; then
    print_error "源数据库容器 $SOURCE_CONTAINER 未运行"
    exit 1
fi

# 获取源数据库统计
print_info "源数据库统计:"
docker exec "$SOURCE_CONTAINER" mysql -u"$SOURCE_USER" -p"$SOURCE_PASS" "$SOURCE_DB" -e "
SELECT 'Users' as table_name, COUNT(*) as count FROM users
UNION ALL
SELECT 'Stocks', COUNT(*) FROM stocks
UNION ALL
SELECT 'StockBasic', COUNT(*) FROM stock_basic
UNION ALL
SELECT 'UserFavorites', COUNT(*) FROM user_favorites;
" 2>/dev/null || true

echo ""

# 检查目标数据库配置
if [ -z "$TARGET_PASS" ]; then
    print_warning "目标数据库密码未设置"
    read -sp "请输入目标数据库密码: " TARGET_PASS
    echo ""
fi

print_info "目标数据库: $TARGET_HOST:$TARGET_PORT/$TARGET_DB"

# 测试目标数据库连接
print_info "测试目标数据库连接..."
if ! mysql -h"$TARGET_HOST" -P"$TARGET_PORT" -u"$TARGET_USER" -p"$TARGET_PASS" -e "SELECT 1;" > /dev/null 2>&1; then
    print_error "无法连接到目标数据库，请检查配置"
    exit 1
fi

print_success "目标数据库连接成功"

# 确认迁移
echo ""
print_warning "即将迁移数据到目标数据库"
read -p "是否继续? (y/n): " -n 1 -r
echo ""
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    print_info "已取消迁移"
    exit 0
fi

# 执行迁移
print_info "开始迁移数据..."

# 1. 导出数据
print_info "导出源数据库数据..."
MIGRATION_FILE="/tmp/migration_$(date +%s).sql"

docker exec "$SOURCE_CONTAINER" mysqldump -u"$SOURCE_USER" -p"$SOURCE_PASS" \
    --single-transaction \
    --quick \
    --lock-tables=false \
    --skip-lock-tables \
    --set-gtid-purged=OFF \
    "$SOURCE_DB" > "$MIGRATION_FILE"

if [ $? -ne 0 ]; then
    print_error "导出数据失败"
    exit 1
fi

print_success "数据导出完成"

# 2. 导入到目标数据库
print_info "导入到目标数据库..."
mysql -h"$TARGET_HOST" -P"$TARGET_PORT" -u"$TARGET_USER" -p"$TARGET_PASS" "$TARGET_DB" < "$MIGRATION_FILE"

if [ $? -ne 0 ]; then
    print_error "导入数据失败"
    rm -f "$MIGRATION_FILE"
    exit 1
fi

# 清理临时文件
rm -f "$MIGRATION_FILE"

print_success "数据迁移完成！"

# 验证迁移结果
print_info "验证迁移结果:"
mysql -h"$TARGET_HOST" -P"$TARGET_PORT" -u"$TARGET_USER" -p"$TARGET_PASS" "$TARGET_DB" -e "
SELECT 'Users' as table_name, COUNT(*) as count FROM users
UNION ALL
SELECT 'Stocks', COUNT(*) FROM stocks
UNION ALL
SELECT 'StockBasic', COUNT(*) FROM stock_basic
UNION ALL
SELECT 'UserFavorites', COUNT(*) FROM user_favorites;
"

echo ""
print_success "数据库迁移成功完成！"
print_info "请更新 docker-compose.yml 中的数据库配置"
