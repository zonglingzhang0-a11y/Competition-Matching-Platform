#!/usr/bin/env bash
# 备份数据库，留最近 7 天。由 jingda-backup.timer 每天凌晨 4 点触发，装在服务器的 /usr/local/sbin/jingda-backup。
#   马上备份一次：systemctl start jingda-backup
#   看有哪些备份：ls -lh /var/backups/jingda
#   恢复（会覆盖现在的数据，先停后端）：systemctl stop jingda && gunzip -c 备份文件 | mysql team_match && systemctl start jingda
set -euo pipefail

DIR=/var/backups/jingda
KEEP_DAYS=7

# 备份里有密码摘要和登录凭证，只给 root 看
umask 077
mkdir -p "$DIR"
chmod 700 "$DIR"

FILE="$DIR/team_match-$(date +%F-%H%M).sql.gz"
trap 'rm -f "$FILE.tmp"' EXIT
# --single-transaction：InnoDB 的表不锁，备份时网站照常用
mysqldump --single-transaction --routines --triggers --default-character-set=utf8mb4 team_match | gzip > "$FILE.tmp"
mv "$FILE.tmp" "$FILE"

find "$DIR" -name 'team_match-*.sql.gz' -mtime +$((KEEP_DAYS - 1)) -delete
echo "备份完成：$FILE（$(du -h "$FILE" | cut -f1)），现有 $(ls "$DIR"/team_match-*.sql.gz | wc -l) 份"
