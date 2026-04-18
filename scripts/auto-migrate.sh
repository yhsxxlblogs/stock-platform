#!/bin/bash

# ==========================================
# 自动数据库迁移脚本
# 在容器启动时检查并执行迁移
# ==========================================

set -e

DB_CONTAINER="stock-mysql"
DB_NAME="stock_platform"
DB_USER="root"
DB_PASS="${MYSQL_ROOT_PASSWORD:-@Syh20050608}"
MIGRATION_DIR="./migrations"

# 颜色定义
GREEN='\033[0;32m'
BLUE='\033[0;34m'
NC='\033[0m'

print_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

# 等待 MySQL 就绪
wait_for_mysql() {
    print_info "等待 MySQL 就绪..."
    for i in {1..30}; do
        if docker exec "$DB_CONTAINER" mysql -u"$DB_USER" -p"$DB_PASS" -e "SELECT 1;" > /dev/null 2>&1; then
            print_success "MySQL 已就绪"
            return 0
        fi
        sleep 2
    done
    echo "MySQL 连接超时"
    exit 1
}

# 获取已执行的迁移版本
get_applied_migrations() {
    docker exec "$DB_CONTAINER" mysql -u"$DB_USER" -p"$DB_PASS" "$DB_NAME" -N -e "
        SELECT version FROM schema_migrations ORDER BY version;
    " 2>/dev/null || echo ""
}

# 创建迁移记录表
create_migration_table() {
    docker exec "$DB_CONTAINER" mysql -u"$DB_USER" -p"$DB_PASS" "$DB_NAME" -e "
        CREATE TABLE IF NOT EXISTS schema_migrations (
            version VARCHAR(255) PRIMARY KEY,
            applied_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        );
    " 2>/dev/null
}

# 执行迁移
apply_migration() {
    local file=$1
    local version=$(basename "$file" .sql)
    
    print_info "执行迁移: $version"
    
    # 执行 SQL 文件
    docker exec -i "$DB_CONTAINER" mysql -u"$DB_USER" -p"$DB_PASS" "$DB_NAME" < "$file"
    
    # 记录迁移
    docker exec "$DB_CONTAINER" mysql -u"$DB_USER" -p"$DB_PASS" "$DB_NAME" -e "
        INSERT INTO schema_migrations (version) VALUES ('$version');
    " 2>/dev/null
    
    print_success "迁移完成: $version"
}

# 主流程
main() {
    print_info "检查数据库迁移..."
    
    # 等待 MySQL
    wait_for_mysql
    
    # 创建迁移记录表
    create_migration_table
    
    # 获取已执行的迁移
    local applied=$(get_applied_migrations)
    
    # 检查是否有新迁移文件
    if [ -d "$MIGRATION_DIR" ]; then
        for file in "$MIGRATION_DIR"/*.sql; do
            [ -e "$file" ] || continue
            
            local version=$(basename "$file" .sql)
            
            # 检查是否已执行
            if echo "$applied" | grep -q "^$version$"; then
                print_info "跳过已执行的迁移: $version"
            else
                apply_migration "$file"
            fi
        done
    fi
    
    print_success "数据库迁移检查完成"
}

main
