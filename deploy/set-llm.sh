#!/usr/bin/env bash
# 打开 AI 功能（比赛页 AI 帮我整理、AI 能力分析 / 招募建议、爬虫补抽）：在本机 Git Bash 里运行，
# 按提示输入阿里云百炼的业务空间 ID、模型名和 API Key，先验证密钥能用，再写到服务器的 /opt/jingda/llm.yml 并重启后端。
# API Key 只在你自己的终端里输入，输入时不显示，不会进代码库，也不会出现在命令行参数里。
#   用法：bash deploy/set-llm.sh          关掉 AI：bash deploy/set-llm.sh --off
set -euo pipefail

SERVER=${SERVER:-root@47.111.123.178}
KEY=${KEY:-$HOME/.ssh/jingda_ecs}
SSH="ssh -i $KEY $SERVER"

if [ "${1:-}" = "--off" ]; then
  $SSH 'rm -f /opt/jingda/llm.yml && systemctl restart jingda'
  echo "已关掉 AI（删除 llm.yml 并重启后端），各功能退回本地规则。"
  exit 0
fi

echo "百炼的免费额度只在华北2（北京）地域，API Key 也要在北京地域创建。"
read -rp "业务空间 ID（百炼控制台里能看到；不知道就直接回车，用通用地址）: " WS
read -rp "模型名（直接回车用 qwen3.7-plus）: " MODEL
MODEL=${MODEL:-qwen3.7-plus}
read -rsp "API Key（sk- 开头，输入时不显示）: " APIKEY
echo
if [ -z "$APIKEY" ]; then
  echo "API Key 为空，没有做任何修改"
  exit 1
fi
if [ -n "$WS" ]; then
  BASE="https://$WS.cn-beijing.maas.aliyuncs.com/compatible-mode/v1"
else
  BASE="https://dashscope.aliyuncs.com/compatible-mode/v1"
fi

echo "== 验证密钥（只查模型列表，不花 token）：$BASE"
# 请求头从标准输入传给 curl，密钥不会出现在服务器的进程列表里
CODE=$(printf 'header = "Authorization: Bearer %s"\n' "$APIKEY" \
  | $SSH "curl -s -o /dev/null -w '%{http_code}' --max-time 15 --config - '$BASE/models'" || true)
if [ "$CODE" != "200" ]; then
  echo "验证没通过（HTTP $CODE），服务器上的配置没动。"
  echo "常见原因：密钥复制不全；密钥不是在华北2（北京）地域创建的；业务空间 ID 填错。"
  exit 1
fi
echo "   密钥可以用"

cat <<YML | $SSH 'umask 077; cat > /opt/jingda/llm.yml && chown jingda:jingda /opt/jingda/llm.yml && systemctl restart jingda'
# 大模型配置，由 deploy/set-llm.sh 生成。只在服务器上，不进代码库
app:
  llm:
    enabled: true
    base-url: $BASE
    model: $MODEL
    api-key: '$APIKEY'
    timeout-ms: 60000
  ai:
    enabled: true
YML

echo "已写入服务器并重启后端（模型 $MODEL）。十几秒后可以去比赛页点「AI 帮我整理」试一下。"
