#!/bin/bash

# ==========================================
# Linux 服务器数据库导入脚本
# 将 Windows 导出的 SQL 文件导入到 Docker MySQL 容器
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
DB_CONTAINER="stock-mysql"
DB_NAME="stock_platform"
DB_USER="root"
DB_PASS="@Syh20050608"
SQL_FILE="/tmp/stock_platform_for_linux.sql"

echo "=========================================="
echo "  Docker MySQL 导入工具"
echo "=========================================="
echo ""

# 检查 SQL 文件是否存在
if [ ! -f "$SQL_FILE" ]; then
    print_error "SQL 文件不存在: $SQL_FILE"
    echo ""
    print_info "请先从 Windows 上传 SQL 文件:"
    echo "  scp scripts\\stock_platform_for_linux.sql root@你的服务器IP:/tmp/"
    echo ""
    exit 1
fi

print_info "找到 SQL 文件: $SQL_FILE"

# 检查容器是否运行
print_info "检查 MySQL 容器状态..."
if ! docker ps | grep -q "$DB_CONTAINER"; then
    print_error "MySQL 容器 $DB_CONTAINER 未运行"
    exit 1
fi
print_success "MySQL 容器运行正常"

# 检查并修复文件编码
print_info "检查和修复文件编码..."

# 创建修复后的文件
FIXED_SQL_FILE="/tmp/stock_platform_fixed.sql"

# 检测并移除 BOM
if head -c 3 "$SQL_FILE" | od -An -tx1 | grep -q "ef bb bf"; then
    print_warning "检测到 UTF-8 BOM 头，正在移除..."
    tail -c +4 "$SQL_FILE" > "$FIXED_SQL_FILE"
else
    cp "$SQL_FILE" "$FIXED_SQL_FILE"
fi

# 转换 Windows 换行符为 Unix 换行符
print_info "转换换行符 (CRLF -> LF)..."
sed -i 's/\r$//' "$FIXED_SQL_FILE"

# 确保文件以换行符结尾
if [ -n "$(tail -c 1 "$FIXED_SQL_FILE")" ]; then
    echo >> "$FIXED_SQL_FILE"
fi

# 验证文件编码
print_info "验证文件编码..."
if file "$FIXED_SQL_FILE" | grep -q "UTF-8"; then
    print_success "文件编码正确 (UTF-8)"
else
    print_warning "文件编码可能不正确，尝试转换..."
    iconv -f GBK -t UTF-8 "$FIXED_SQL_FILE" > "${FIXED_SQL_FILE}.utf8" 2>/dev/null && mv "${FIXED_SQL_FILE}.utf8" "$FIXED_SQL_FILE" || true
fi

print_success "文件编码修复完成"

# 等待 MySQL 就绪
print_info "等待 MySQL 就绪..."
for i in {1..30}; do
    if docker exec "$DB_CONTAINER" mysql -u"$DB_USER" -p"$DB_PASS" -e "SELECT 1;" > /dev/null 2>&1; then
        print_success "MySQL 已就绪"
        break
    fi
    echo -n "."
    sleep 2
    
    if [ $i -eq 30 ]; then
        print_error "MySQL 连接超时"
        exit 1
    fi
done

# 显示当前数据库状态
print_info "当前数据库状态:"
docker exec "$DB_CONTAINER" mysql -u"$DB_USER" -p"$DB_PASS" -e "SHOW DATABASES;" 2>/dev/null | grep -E "Database|stock_platform" || true

# 确认导入
echo ""
print_warning "即将清空并重新导入数据库: $DB_NAME"
read -p "是否继续? (y/n): " -n 1 -r
echo ""
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    print_info "已取消导入"
    rm -f "$FIXED_SQL_FILE"
    exit 0
fi

# 清空数据库
print_info "清空数据库..."
docker exec "$DB_CONTAINER" mysql -u"$DB_USER" -p"$DB_PASS" -e "DROP DATABASE IF EXISTS $DB_NAME; CREATE DATABASE $DB_NAME CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;" 2>/dev/null
print_success "数据库已清空并重建"

# 复制 SQL 文件到容器
print_info "复制 SQL 文件到容器..."
docker cp "$FIXED_SQL_FILE" "$DB_CONTAINER:/tmp/import.sql"
print_success "文件复制完成"

# 执行导入（使用容器内的文件）
print_info "开始导入数据..."
print_info "这可能需要几分钟，请耐心等待..."
echo ""

if docker exec "$DB_CONTAINER" sh -c "mysql -u'$DB_USER' -p'$DB_PASS' '$DB_NAME' < /tmp/import.sql"; then
    print_success "数据导入成功！"
else
    print_error "数据导入失败"
    print_info "尝试使用替代方法导入..."
    
    # 替代方法：分块导入
    print_info "尝试分块导入..."
    docker exec "$DB_CONTAINER" mysql -u"$DB_USER" -p"$DB_PASS" "$DB_NAME" < "$FIXED_SQL_FILE" && print_success "分块导入成功！" || print_error "分块导入也失败了"
fi

# 验证导入结果
echo ""
print_info "验证导入结果:"
echo "----------------------------------------"
docker exec "$DB_CONTAINER" mysql -u"$DB_USER" -p"$DB_PASS" "$DB_NAME" -e "
SELECT 'users' as table_name, COUNT(*) as count FROM users
UNION ALL
SELECT 'stocks', COUNT(*) FROM stocks
UNION ALL
SELECT 'stock_basic', COUNT(*) FROM stock_basic
UNION ALL
SELECT 'user_favorites', COUNT(*) FROM user_favorites
UNION ALL
SELECT 'stock_daily_data', COUNT(*) FROM stock_daily_data;
" 2>/dev/null || true

echo "----------------------------------------"

# 清理临时文件
print_info "清理临时文件..."
docker exec "$DB_CONTAINER" rm -f /tmp/import.sql
rm -f "$SQL_FILE" "$FIXED_SQL_FILE"

echo ""
echo "=========================================="
print_success "数据库迁移完成！"
echo "=========================================="
echo ""
