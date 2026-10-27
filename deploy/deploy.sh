#!/usr/bin/env bash
# 在本机（Git Bash）运行：把当前代码部署到服务器。
#   前端在本机打包；后端上传源码到服务器上打包（OCR 本地库按打包时的系统选，Windows 上打的包在 Linux 上用不了）。
#   第一次：bash deploy/deploy.sh --setup     以后更新代码：bash deploy/deploy.sh
#   换服务器：SERVER=root@新IP bash deploy/deploy.sh
set -euo pipefail

SERVER=${SERVER:-root@47.111.123.178}
IP=${SERVER#*@}
KEY=${KEY:-$HOME/.ssh/jingda_ecs}
SSH="ssh -i $KEY -o StrictHostKeyChecking=accept-new $SERVER"
cd "$(dirname "$0")/.."

if [ "${1:-}" = "--setup" ]; then
  echo "== 上传 SQL 和部署配置，在服务器上初始化"
  tar czf - sql deploy | $SSH "rm -rf /root/jingda-setup && mkdir -p /root/jingda-setup && tar xzf - -C /root/jingda-setup"
  $SSH "bash /root/jingda-setup/deploy/server-setup.sh $IP"
fi

echo "== 本机打包前端"
(cd frontend && ./node_modules/.bin/vite build)

echo "== 上传后端源码，在服务器上打包"
# 本机的 application-local.yml 里是本机数据库的配置，不能带上去
tar czf - --exclude=target --exclude=uploads --exclude=application-local.yml -C backend . \
  | $SSH "rm -rf /opt/jingda/build && mkdir -p /opt/jingda/build && tar xzf - -C /opt/jingda/build"
$SSH 'bash -s' <<'REMOTE'
set -euo pipefail
mkdir -p /root/.m2
[ -f /root/.m2/settings.xml ] || cat > /root/.m2/settings.xml <<'XML'
<settings>
  <mirrors>
    <mirror>
      <id>aliyun</id>
      <mirrorOf>central</mirrorOf>
      <url>https://maven.aliyun.com/repository/public</url>
    </mirror>
  </mirrors>
</settings>
XML
cd /opt/jingda/build
chmod +x mvnw
MVNW_REPOURL=https://maven.aliyun.com/repository/public ./mvnw -q -B -DskipTests package
JAR=$(ls target/*.jar | grep -v '\.original$' | head -1)
cp "$JAR" /opt/jingda/app.jar
chown jingda:jingda /opt/jingda/app.jar
REMOTE

echo "== 上传前端"
tar czf - -C frontend/dist . | $SSH "rm -rf /var/www/jingda.new && mkdir -p /var/www/jingda.new \
  && tar xzf - -C /var/www/jingda.new && rm -rf /var/www/jingda.old \
  && (mv /var/www/jingda /var/www/jingda.old 2>/dev/null || true) && mv /var/www/jingda.new /var/www/jingda"

echo "== 重启后端"
$SSH "systemctl restart jingda"
for _ in $(seq 1 60); do
  code=$(curl -s -o /dev/null -w '%{http_code}' "http://$IP/api/competitions" || true)
  if [ "$code" = 200 ] || [ "$code" = 401 ]; then
    echo "== 部署完成：http://$IP"
    exit 0
  fi
  sleep 3
done
echo "后端 3 分钟内没有起来，查看日志：ssh -i $KEY $SERVER journalctl -u jingda -n 100"
exit 1
