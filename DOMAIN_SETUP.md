# 域名访问配置指南

## 快速配置（HTTP）

### 1. 域名解析
在域名服务商添加 A 记录：
```
主机记录: @ 或 www
记录类型: A
记录值: 您的服务器IP地址
```

### 2. 修改 Nginx 配置
编辑 `nginx/nginx.conf`，修改 server_name:
```nginx
server {
    listen 80;
    server_name your-domain.com www.your-domain.com;  # 修改为您的域名
    ...
}
```

### 3. 重启服务
```bash
docker-compose -f docker-compose.domain.yml down
docker-compose -f docker-compose.domain.yml up -d
```

### 4. 访问
```
http://your-domain.com
```

---

## HTTPS 配置（SSL证书）

### 方式一：使用 Certbot（推荐）

#### 1. 安装 Certbot
```bash
# Ubuntu/Debian
sudo apt-get update
sudo apt-get install certbot python3-certbot-nginx

# CentOS
sudo yum install certbot python3-certbot-nginx
```

#### 2. 申请证书
```bash
sudo certbot --nginx -d your-domain.com -d www.your-domain.com
```

#### 3. 自动续期
Certbot 会自动配置定时任务，无需手动操作。

---

### 方式二：手动配置 SSL

#### 1. 准备证书文件
将证书文件放在 `nginx/ssl/` 目录：
```
nginx/ssl/
├── your-domain.com.crt    # 证书
├── your-domain.com.key    # 私钥
└── ca.crt                 # CA证书（可选）
```

#### 2. 修改 Nginx 配置
编辑 `nginx/nginx.conf`，添加 HTTPS 配置：
```nginx
server {
    listen 443 ssl http2;
    server_name your-domain.com www.your-domain.com;
    
    # SSL 证书配置
    ssl_certificate /etc/nginx/ssl/your-domain.com.crt;
    ssl_certificate_key /etc/nginx/ssl/your-domain.com.key;
    ssl_trusted_certificate /etc/nginx/ssl/ca.crt;
    
    # SSL 优化
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers ECDHE-ECDSA-AES128-GCM-SHA256:ECDHE-RSA-AES128-GCM-SHA256;
    ssl_prefer_server_ciphers off;
    ssl_session_cache shared:SSL:10m;
    ssl_session_timeout 1d;
    
    # 其他配置...
}

# HTTP 重定向到 HTTPS
server {
    listen 80;
    server_name your-domain.com www.your-domain.com;
    return 301 https://$server_name$request_uri;
}
```

#### 3. 重启服务
```bash
docker-compose -f docker-compose.domain.yml restart nginx
```

---

## 使用 Cloudflare CDN（推荐）

### 1. 添加域名到 Cloudflare
1. 注册 Cloudflare 账号
2. 添加您的域名
3. 修改域名服务器为 Cloudflare 提供的地址

### 2. 配置 DNS
在 Cloudflare 添加 A 记录：
```
Type: A
Name: @
IPv4 address: 您的服务器IP
Proxy status: Proxied（橙色云朵）
```

### 3. 开启 HTTPS
在 Cloudflare 控制台：
- SSL/TLS → Overview → 选择 **Full (strict)**
- 开启 **Always Use HTTPS**

### 4. 服务器配置
服务器只需监听 HTTP 80 端口，Cloudflare 会自动处理 HTTPS。

---

## 防火墙配置

确保服务器防火墙开放 80 和 443 端口：

```bash
# Ubuntu/Debian (UFW)
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw reload

# CentOS (FirewallD)
sudo firewall-cmd --permanent --add-service=http
sudo firewall-cmd --permanent --add-service=https
sudo firewall-cmd --reload

# 或者使用 iptables
sudo iptables -A INPUT -p tcp --dport 80 -j ACCEPT
sudo iptables -A INPUT -p tcp --dport 443 -j ACCEPT
```

---

## 常见问题

### Q: 域名解析后无法访问？
A: 检查以下几点：
1. 域名解析是否生效：`ping your-domain.com`
2. 服务器防火墙是否开放 80 端口
3. Nginx 配置中的 server_name 是否正确
4. Docker 容器是否正常运行

### Q: 如何配置多个域名？
A: 在 nginx.conf 中添加多个 server 块：
```nginx
server {
    listen 80;
    server_name domain1.com www.domain1.com;
    ...
}

server {
    listen 80;
    server_name domain2.com www.domain2.com;
    ...
}
```

### Q: 如何配置子域名？
A: 添加 A 记录指向同一 IP，然后在 nginx.conf 中配置：
```nginx
server {
    listen 80;
    server_name api.your-domain.com;
    ...
}
```

---

## 部署命令汇总

```bash
# 1. 克隆项目（如果还没克隆）
git clone https://github.com/yhsxxlblogs/stock-platform.git
cd stock-platform

# 2. 修改 nginx/nginx.conf 中的 server_name
vim nginx/nginx.conf

# 3. 启动服务
docker-compose -f docker-compose.domain.yml up -d

# 4. 查看日志
docker-compose -f docker-compose.domain.yml logs -f nginx

# 5. 申请 SSL 证书（可选）
sudo certbot --nginx -d your-domain.com

# 6. 更新项目
git pull
docker-compose -f docker-compose.domain.yml up -d --build
```
