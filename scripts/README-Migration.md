# 数据库迁移指南

## 概述

本指南说明如何将 Windows 本机的 MySQL 数据库迁移到 Linux 服务器的 Docker MySQL 容器中。

## 文件说明

| 文件 | 用途 | 执行位置 |
|------|------|----------|
| `export-db-from-windows.bat` | 导出 Windows MySQL 数据库 | Windows 本机 |
| `import-db-to-docker.sh` | 导入到 Docker MySQL 容器 | Linux 服务器 |

## 迁移步骤

### 第一步：在 Windows 上导出数据库

1. 确保 MySQL 已安装并添加到环境变量
2. 双击运行 `export-db-from-windows.bat`
3. 脚本会生成 `stock_platform_for_linux.sql` 文件

### 第二步：上传 SQL 文件到服务器

在 Windows PowerShell 或 CMD 中执行：

```powershell
scp scripts\stock_platform_for_linux.sql root@你的服务器IP:/tmp/
```

### 第三步：在服务器上导入数据库

SSH 登录服务器后执行：

```bash
cd /www/wwwroot/marketpulse.supersyh.xyz/stock-platform
bash scripts/import-db-to-docker.sh
```

## 常见问题

### 1. mysqldump 未找到

确保 MySQL 的 bin 目录已添加到系统环境变量 PATH 中。

### 2. 编码错误

脚本已自动处理以下编码问题：
- UTF-8 BOM 头移除
- Windows CRLF 换行符转换为 Unix LF
- 确保文件以换行符结尾

### 3. 导入失败

检查：
- Docker 容器 `stock-mysql` 是否运行
- 数据库密码是否正确
- SQL 文件是否完整上传到 `/tmp/`

## 手动备份（可选）

如果脚本无法使用，可以手动执行：

### Windows 导出
```cmd
mysqldump -h localhost -P 3306 -u root -p@Syh20050608 --single-transaction --quick stock_platform > stock_platform_for_linux.sql
```

### Linux 导入
```bash
# 清空数据库
docker exec stock-mysql mysql -u root -p@Syh20050608 -e "DROP DATABASE IF EXISTS stock_platform; CREATE DATABASE stock_platform;"

# 导入
docker exec -i stock-mysql mysql -u root -p@Syh20050608 stock_platform < /tmp/stock_platform_for_linux.sql
```
