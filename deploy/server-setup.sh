#!/usr/bin/env bash
# 第一次部署时，在服务器上用 root 跑一次：时区、swap、装软件、建库、配 Nginx 和后端服务。
# 可以重复跑：已经做过的步骤会跳过，数据库里已经有表就不会重新导入。
#   用法：bash server-setup.sh <公网IP> [--no-demo]
set -euo pipefail

PUBLIC_IP=${1:?请传入服务器公网 IP}
IMPORT_DEMO=yes
[ "${2:-}" = "--no-demo" ] && IMPORT_DEMO=no
# 上传上来的目录：sql/ 和 deploy/ 并排放着
HERE=$(cd "$(dirname "$0")/.." && pwd)
APP=/opt/jingda

echo "== 1. 时区和 2G swap"
timedatectl set-timezone Asia/Shanghai
if ! swapon --show | grep -q /swapfile; then
  fallocate -l 2G /swapfile
  chmod 600 /swapfile
  mkswap /swapfile >/dev/null
  swapon /swapfile
  grep -q '^/swapfile' /etc/fstab || echo '/swapfile none swap sw 0 0' >> /etc/fstab
fi

echo "== 2. 安装 JDK 21、MySQL、Nginx"
export DEBIAN_FRONTEND=noninteractive
apt-get update -q
# libgomp1 是 OCR（onnxruntime）要用的
apt-get install -y -q openjdk-21-jdk-headless mysql-server nginx unzip libgomp1

echo "== 3. MySQL：小内存配置、数据库和专用账号（只能从本机连）"
cat > /etc/mysql/mysql.conf.d/zz-jingda.cnf <<'CNF'
[mysqld]
bind-address = 127.0.0.1
innodb_buffer_pool_size = 256M
performance_schema = OFF
max_connections = 60
CNF
systemctl restart mysql

mkdir -p "$APP" "$APP/uploads"
if [ ! -f "$APP/.db-password" ]; then
  openssl rand -hex 16 > "$APP/.db-password"
  chmod 600 "$APP/.db-password"
fi
DB_PASS=$(cat "$APP/.db-password")
mysql <<SQL
CREATE DATABASE IF NOT EXISTS team_match DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS 'jingda'@'localhost' IDENTIFIED BY '$DB_PASS';
ALTER USER 'jingda'@'localhost' IDENTIFIED BY '$DB_PASS';
GRANT ALL PRIVILEGES ON team_match.* TO 'jingda'@'localhost';
FLUSH PRIVILEGES;
SQL

TABLES=$(mysql -N -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'team_match'")
if [ "$TABLES" = "0" ]; then
  echo "   导入 schema.sql、seed.sql"
  mysql --default-character-set=utf8mb4 < "$HERE/sql/schema.sql"
  mysql --default-character-set=utf8mb4 < "$HERE/sql/seed.sql"
  if [ "$IMPORT_DEMO" = yes ]; then
    echo "   导入 demo.sql（演示账号和数据）"
    mysql --default-character-set=utf8mb4 < "$HERE/sql/demo.sql"
  fi
else
  echo "   数据库里已经有 $TABLES 张表，跳过导入"
fi

echo "== 4. 后端运行账号和配置"
id jingda >/dev/null 2>&1 || useradd --system --home-dir "$APP" --shell /usr/sbin/nologin jingda
if [ ! -f "$APP/application-local.yml" ]; then
  cat > "$APP/application-local.yml" <<YML
# 服务器上的配置，只在这台机器上，不进代码库
server:
  address: 127.0.0.1        # 只让 Nginx 从本机访问后端，8090 不对外

spring:
  config:
    # 发邮件、大模型的配置单独放在 mail.yml、llm.yml，分别用 deploy/set-mail.sh、set-llm.sh 生成；没有就用默认（验证码打印在日志里、AI 关着）
    import: optional:file:$APP/mail.yml,optional:file:$APP/llm.yml
  datasource:
    username: jingda
    password: $DB_PASS

springdoc:
  api-docs:
    enabled: false
  swagger-ui:
    enabled: false

app:
  screenshot:
    upload-dir: $APP/uploads
  notify-mail:
    site-url: http://$PUBLIC_IP
YML
fi
chown -R jingda:jingda "$APP"
chmod 600 "$APP/application-local.yml"

echo "== 5. Nginx"
mkdir -p /var/www/jingda
cp "$HERE/deploy/nginx-jingda.conf" /etc/nginx/sites-available/jingda
ln -sf /etc/nginx/sites-available/jingda /etc/nginx/sites-enabled/jingda
rm -f /etc/nginx/sites-enabled/default
nginx -t
systemctl reload nginx

echo "== 6. 后端服务"
cp "$HERE/deploy/jingda.service" /etc/systemd/system/jingda.service
systemctl daemon-reload
systemctl enable jingda >/dev/null

echo "== 7. 每天凌晨 4 点备份数据库，留 7 天（/var/backups/jingda）"
install -m 700 "$HERE/deploy/jingda-backup.sh" /usr/local/sbin/jingda-backup
cp "$HERE/deploy/jingda-backup.service" "$HERE/deploy/jingda-backup.timer" /etc/systemd/system/
systemctl daemon-reload
systemctl enable --now jingda-backup.timer >/dev/null

echo "== 完成。接下来用 deploy/deploy.sh 打包并上传代码"
