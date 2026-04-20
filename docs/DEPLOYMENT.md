# Stock Platform 详细部署文档

## 目录
1. [环境要求](#环境要求)
2. [服务器准备](#服务器准备)
3. [Docker安装](#docker安装)
4. [项目部署](#项目部署)
5. [宝塔面板配置](#宝塔面板配置)
6. [域名和HTTPS](#域名和https)
7. [监控和维护](#监控和维护)
8. [故障排查](#故障排查)
9. [备份和恢复](#备份和恢复)
10. [升级更新](#升级更新)

---

## 环境要求

### 最低配置
- **CPU**: 2核
- **内存**: 2GB
- **磁盘**: 20GB SSD
- **带宽**: 3Mbps
- **系统**: CentOS 7/8, Ubuntu 18.04/20.04/22.04

### 推荐配置
- **CPU**: 4核
- **内存**: 4GB
- **磁盘**: 50GB SSD
- **带宽**: 5Mbps+
- **系统**: Ubuntu 22.04 LTS

### 软件版本
- Docker: 20.10+
- Docker Compose: 2.0+
- MySQL: 8.0
- Redis: 7.x
- Nginx: 1.20+

---

## 服务器准备

### 1. 购买服务器
推荐阿里云、腾讯云、华为云等主流云服务商
- 选择地区：根据用户分布选择最近的地域
- 选择配置：最低2核2G，推荐4核4G
- 选择系统：Ubuntu 22.04 LTS 64位
- 配置安全组：开放22(SSH)、80(HTTP)、443(HTTPS)端口

### 2. 连接服务器
```bash
# Windows使用PowerShell或Git Bash
ssh root@你的服务器IP

# 或使用密钥连接
ssh -i ~/.ssh/your_key.pem root@你的服务器IP
```

### 3. 系统初始化
```bash
# 更新系统
apt update && apt upgrade -y

# 设置时区
timedatectl set-timezone Asia/Shanghai

# 安装常用工具
apt install -y vim wget curl net-tools htop

# 配置防火墙
ufw default deny incoming
ufw default allow outgoing
ufw allow 22/tcp
ufw allow 80/tcp
ufw allow 443/tcp
ufw enable
```

---

## Docker安装

### 1. 安装Docker
```bash
# 卸载旧版本
apt remove docker docker-engine docker.io containerd runc

# 安装依赖
apt install -y apt-transport-https ca-certificates curl gnupg lsb-release

# 添加Docker官方GPG密钥
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | gpg --dearmor -o /usr/share/keyrings/docker-archive-keyring.gpg

# 添加Docker软件源
echo "deb [arch=$(dpkg --print-architecture) signed-by=/usr/share/keyrings/docker-archive-keyring.gpg] https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable" | tee /etc/apt/sources.list.d/docker.list > /dev/null

# 安装Docker
apt update
apt install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin

# 启动Docker
systemctl start docker
systemctl enable docker

# 验证安装
docker --version
docker compose version
```

### 2. 配置Docker
```bash
# 创建Docker配置目录
mkdir -p /etc/docker

# 配置镜像加速（可选，国内服务器推荐）
cat > /etc/docker/daemon.json <<EOF
{
  "registry-mirrors": [
    "https://docker.mirrors.ustc.edu.cn",
    "https://hub-mirror.c.163.com"
  ],
  "log-driver": "json-file",
  "log-opts": {
    "max-size": "10m",
    "max-file": "3"
  }
}
EOF

# 重启Docker
systemctl restart docker
```

---

## 项目部署

### 1. 克隆项目
```bash
# 创建项目目录
mkdir -p /www/wwwroot
cd /www/wwwroot

# 克隆代码
git clone https://github.com/yhsxxlblogs/stock-platform.git
cd stock-platform

# 查看项目结构
ls -la
```

### 2. 配置环境变量
```bash
# 创建环境变量文件
cat > .env <<EOF
# MySQL配置
MYSQL_ROOT_PASSWORD=你的安全密码
MYSQL_DATABASE=stock_platform

# 时区设置
TZ=Asia/Shanghai

# JWT密钥（生产环境必须修改）
JWT_SECRET=$(openssl rand -base64 32)

# Token过期时间（毫秒）
JWT_EXPIRATION=86400000

# 服务器域名
SERVER_DOMAIN=你的域名.com
EOF

# 设置权限
chmod 600 .env
```

### 3. 启动服务
```bash
# 构建并启动（首次需要较长时间）
docker compose up -d --build

# 查看启动状态
docker compose ps

# 查看日志
docker compose logs -f
```

### 4. 验证部署
```bash
# 检查容器状态
docker ps

# 测试后端API
curl http://localhost:9090/api/stocks/public/list

# 测试前端
curl http://localhost:8080

# 查看MySQL
docker exec stock-mysql mysql -uroot -p你的密码 -e "SHOW DATABASES;"
```

---

## 宝塔面板配置

### 1. 安装宝塔面板
```bash
# 安装宝塔（Ubuntu/Debian）
wget -O install.sh https://download.bt.cn/install/install-ubuntu_6.0.sh && bash install.sh ed8484bec

# 安装完成后会显示面板地址和初始密码
# 访问: http://服务器IP:8888
```

### 2. 配置网站
```bash
# 登录宝塔面板
# 1. 点击"网站" -> "添加站点"
# 2. 填写域名（如果没有域名，填写服务器IP）
# 3. 根目录选择: /www/wwwroot/stock-platform
# 4. PHP版本选择: 纯静态
```

### 3. 配置反向代理
```nginx
# 在宝塔网站设置中，选择"反向代理"
# 添加以下配置:

# 前端代理
location / {
    proxy_pass http://127.0.0.1:8080;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
}

# 后端API代理
location /api/ {
    proxy_pass http://127.0.0.1:9090/api/;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
}

# WebSocket代理
location /api/ws {
    proxy_pass http://127.0.0.1:9090;
    proxy_http_version 1.1;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection "upgrade";
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_read_timeout 86400s;
}
```

### 4. 配置SSL证书
```bash
# 在宝塔面板中:
# 1. 选择网站 -> 设置 -> SSL
# 2. 选择"Let's Encrypt"免费证书
# 3. 勾选"强制HTTPS"
# 4. 点击"保存"

# 或者手动配置证书
# 将证书文件上传到 /www/server/panel/vhost/cert/你的域名/
```

---

## 域名和HTTPS

### 1. 域名解析
```bash
# 在域名服务商处添加A记录
# 主机记录: @ 或 www
# 记录值: 你的服务器IP
# TTL: 默认

# 等待DNS生效（通常5-30分钟）
dig your-domain.com
```

### 2. 手动配置HTTPS
```bash
# 安装Certbot
apt install -y certbot

# 申请证书
certbot certonly --standalone -d your-domain.com

# 证书位置
# /etc/letsencrypt/live/your-domain.com/fullchain.pem
# /etc/letsencrypt/live/your-domain.com/privkey.pem

# 自动续期
echo "0 0 1 * * certbot renew --quiet" | crontab -
```

### 3. Nginx HTTPS配置
```nginx
server {
    listen 80;
    server_name your-domain.com;
    return 301 https://$server_name$request_uri;
}

server {
    listen 443 ssl http2;
    server_name your-domain.com;

    ssl_certificate /etc/letsencrypt/live/your-domain.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/your-domain.com/privkey.pem;
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers HIGH:!aNULL:!MD5;

    # 前端代理
    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }

    # 后端API代理
    location /api/ {
        proxy_pass http://127.0.0.1:9090/api/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }

    # WebSocket代理
    location /api/ws {
        proxy_pass http://127.0.0.1:9090;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_read_timeout 86400s;
    }
}
```

---

## 监控和维护

### 1. 查看服务状态
```bash
# 查看所有容器
docker ps

# 查看资源使用
docker stats

# 查看日志
docker compose logs -f
docker compose logs -f backend
docker compose logs -f mysql

# 查看磁盘使用
df -h
docker system df
```

### 2. 性能监控
```bash
# 安装htop
apt install -y htop

# 实时监控
htop

# MySQL监控
docker exec stock-mysql mysql -uroot -p -e "SHOW PROCESSLIST;"

# Redis监控
docker exec stock-redis redis-cli info
```

### 3. 日志管理
```bash
# 查看日志大小
du -sh /var/lib/docker/containers/*

# 清理日志
docker system prune -f
docker volume prune -f

# 限制日志大小
# 在docker-compose.yml中添加:
logging:
  driver: "json-file"
  options:
    max-size: "10m"
    max-file: "3"
```

---

## 故障排查

### 1. 容器启动失败
```bash
# 查看错误日志
docker compose logs

# 检查端口占用
netstat -tlnp | grep 8080
netstat -tlnp | grep 9090

# 重启服务
docker compose restart

# 重新构建
docker compose down
docker compose up -d --build
```

### 2. MySQL连接失败
```bash
# 检查MySQL状态
docker ps | grep mysql

# 进入MySQL容器
docker exec -it stock-mysql mysql -uroot -p

# 检查用户权限
SELECT user, host FROM mysql.user;

# 重置密码（如果需要）
docker exec stock-mysql mysql -uroot -p -e "ALTER USER 'root'@'%' IDENTIFIED BY '新密码';"
```

### 3. WebSocket连接失败
```bash
# 检查Nginx配置
nginx -t

# 检查WebSocket端口
curl -i -N -H "Connection: Upgrade" -H "Upgrade: websocket" http://localhost:9090/api/ws/stock

# 查看后端日志
docker compose logs backend | grep -i websocket
```

### 4. 内存不足
```bash
# 查看内存使用
free -h

# 清理缓存
echo 3 > /proc/sys/vm/drop_caches

# 调整Docker内存限制
# 编辑docker-compose.yml，减少内存限制

# 重启Docker服务
systemctl restart docker
```

### 5. 数据库连接池耗尽
```bash
# 查看当前连接
docker exec stock-mysql mysql -uroot -p -e "SHOW STATUS LIKE 'Threads_connected';"

# 查看最大连接数
docker exec stock-mysql mysql -uroot -p -e "SHOW VARIABLES LIKE 'max_connections';"

# 重启MySQL
docker restart stock-mysql
```

---

## 备份和恢复

### 1. 数据库备份
```bash
#!/bin/bash
# 备份脚本 backup.sh

BACKUP_DIR="/www/backup"
DATE=$(date +%Y%m%d_%H%M%S)
DB_NAME="stock_platform"
DB_PASSWORD="你的密码"

# 创建备份目录
mkdir -p $BACKUP_DIR

# 备份数据库
docker exec stock-mysql mysqldump -uroot -p$DB_PASSWORD $DB_NAME > $BACKUP_DIR/stock_platform_$DATE.sql

# 压缩备份文件
gzip $BACKUP_DIR/stock_platform_$DATE.sql

# 删除7天前的备份
find $BACKUP_DIR -name "stock_platform_*.sql.gz" -mtime +7 -delete

echo "备份完成: $BACKUP_DIR/stock_platform_$DATE.sql.gz"
```

### 2. 设置定时备份
```bash
# 添加定时任务
crontab -e

# 每天凌晨2点备份
0 2 * * * /www/wwwroot/stock-platform/scripts/backup.sh >> /var/log/backup.log 2>&1
```

### 3. 数据恢复
```bash
# 恢复数据库
gunzip stock_platform_20240101_020000.sql.gz
docker exec -i stock-mysql mysql -uroot -p你的密码 stock_platform < stock_platform_20240101_020000.sql

# 恢复上传文件
tar -xzvf uploads_backup_20240101.tar.gz -C /www/wwwroot/stock-platform/
```

### 4. 完整系统备份
```bash
# 备份整个项目目录
tar -czvf stock-platform-backup-$(date +%Y%m%d).tar.gz /www/wwwroot/stock-platform/

# 备份Docker数据
docker run --rm -v stock-platform_mysql_data:/data -v /backup:/backup alpine tar czf /backup/mysql-data-$(date +%Y%m%d).tar.gz -C /data .
```

---

## 升级更新

### 1. 更新代码
```bash
cd /www/wwwroot/stock-platform

# 拉取最新代码
git pull origin main

# 如果有冲突，先备份再处理
cp -r . ../stock-platform-backup-$(date +%Y%m%d)
```

### 2. 更新部署
```bash
# 停止服务
docker compose down

# 重新构建并启动
docker compose up -d --build

# 查看状态
docker compose ps
```

### 3. 数据库迁移
```bash
# 如果有数据库变更，执行迁移
# 方法1: 自动迁移（如果有Flyway或Liquibase）

# 方法2: 手动执行SQL
docker exec -i stock-mysql mysql -uroot -p你的密码 stock_platform < migrations/v1.2.0.sql
```

### 4. 回滚操作
```bash
# 如果更新失败，回滚到上一个版本
git log --oneline -10
git reset --hard HEAD~1

# 重新部署
docker compose down
docker compose up -d --build
```

### 5. 零停机更新（高级）
```bash
# 使用蓝绿部署
# 1. 准备新环境
docker compose -f docker-compose.new.yml up -d

# 2. 健康检查
curl http://localhost:9091/api/stocks/public/list

# 3. 切换Nginx代理到新版本
# 修改Nginx配置，指向新端口

# 4. 停止旧版本
docker compose down
```

---

## 安全加固

### 1. 服务器安全
```bash
# 禁用root远程登录
sed -i 's/#PermitRootLogin yes/PermitRootLogin no/' /etc/ssh/sshd_config
systemctl restart sshd

# 创建普通用户
useradd -m -s /bin/bash deploy
usermod -aG sudo deploy

# 配置防火墙
ufw default deny incoming
ufw default allow outgoing
ufw allow 22/tcp
ufw allow 80/tcp
ufw allow 443/tcp
ufw enable
```

### 2. Docker安全
```bash
# 使用非root用户运行容器
# 在docker-compose.yml中添加:
user: "1000:1000"

# 限制容器资源
deploy:
  resources:
    limits:
      cpus: '0.5'
      memory: 512M

# 禁用特权模式
privileged: false
```

### 3. 应用安全
```bash
# 修改默认密码
# 定期更换JWT密钥
# 启用HTTPS
# 配置CORS白名单
```

---

## 性能优化

### 1. 数据库优化
```sql
-- 添加索引
CREATE INDEX idx_stock_symbol ON stock_basic(symbol);
CREATE INDEX idx_stock_name ON stock_basic(name);
CREATE INDEX idx_favorite_user ON favorites(user_id);

-- 优化查询
EXPLAIN SELECT * FROM stock_basic WHERE symbol = '600519';
```

### 2. 缓存优化
```bash
# Redis配置优化
docker exec stock-redis redis-cli CONFIG SET maxmemory 256mb
docker exec stock-redis redis-cli CONFIG SET maxmemory-policy allkeys-lru
```

### 3. JVM优化
```bash
# 在docker-compose.yml中设置JAVA_OPTS
environment:
  JAVA_OPTS: "-Xms256m -Xmx512m -XX:+UseG1GC -XX:MaxGCPauseMillis=200"
```

---

## 总结

### 部署 checklist
- [ ] 服务器购买和配置
- [ ] Docker和Docker Compose安装
- [ ] 项目代码克隆
- [ ] 环境变量配置
- [ ] 服务启动和验证
- [ ] 域名解析
- [ ] HTTPS证书配置
- [ ] 监控和日志配置
- [ ] 备份策略配置
- [ ] 安全加固

### 常用命令速查
```bash
# 启动
docker compose up -d

# 停止
docker compose down

# 重启
docker compose restart

# 查看日志
docker compose logs -f

# 查看状态
docker compose ps

# 进入容器
docker exec -it stock-backend sh

# 备份数据库
docker exec stock-mysql mysqldump -uroot -p stock_platform > backup.sql

# 更新部署
git pull && docker compose down && docker compose up -d --build
```

---

## 联系支持

- **GitHub Issues**: https://github.com/yhsxxlblogs/stock-platform/issues
- **文档更新**: 本文档随项目更新，请关注最新版本
