#!/usr/bin/env bash
# 打开真实发邮件（注册验证码、教育邮箱认证、消息提醒）：在本机 Git Bash 里运行，
# 按提示输入发件邮箱和 SMTP 授权码，写到服务器的 /opt/jingda/mail.yml 并重启后端。
# 授权码只在你自己的终端里输入，输入时不显示，不会进代码库，也不会出现在命令行参数里。
#   用法：bash deploy/set-mail.sh
set -euo pipefail

SERVER=${SERVER:-root@47.111.123.178}
KEY=${KEY:-$HOME/.ssh/jingda_ecs}

read -rp "发件邮箱（QQ / 163 邮箱，或阿里云邮件推送的发信地址）: " EMAIL
read -rsp "SMTP 授权码或 SMTP 密码（输入时不显示）: " CODE
echo
if [[ "$EMAIL" != *@* || -z "$CODE" ]]; then
  echo "邮箱或授权码为空，没有做任何修改"
  exit 1
fi

case "${EMAIL##*@}" in
  qq.com|foxmail.com) HOST=smtp.qq.com ;;
  163.com) HOST=smtp.163.com ;;
  # 其他域名按阿里云邮件推送（华东1）处理：发信地址是自己域名下的，比如 noreply@mail.你的域名.com
  *) HOST=smtpdm.aliyun.com ;;
esac
echo "使用发信服务器 $HOST:465（SSL）"

# 内容从标准输入传过去，授权码不会出现在服务器的进程列表里
cat <<YML | ssh -i "$KEY" "$SERVER" 'cat > /opt/jingda/mail.yml && chown jingda:jingda /opt/jingda/mail.yml && chmod 600 /opt/jingda/mail.yml && systemctl restart jingda'
# 发邮件的配置，由 deploy/set-mail.sh 生成。只在服务器上，不进代码库
spring:
  mail:
    host: $HOST
    port: 465
    username: '$EMAIL'
    password: '$CODE'
    properties:
      mail.smtp.ssl.enable: true
      mail.smtp.connectiontimeout: 10000
      mail.smtp.timeout: 10000

app:
  mail:
    mode: smtp
    from: '$EMAIL'
YML

echo "已写入服务器并重启后端。等十几秒后，在网站注册页填你自己的邮箱试一下验证码。"
