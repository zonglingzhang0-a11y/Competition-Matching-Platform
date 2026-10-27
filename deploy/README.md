# 部署到阿里云服务器

一台 Ubuntu 22.04 服务器（2 核 4G），Nginx 对外提供前端页面并把 `/api/` 转给后端，后端和 MySQL 只在本机监听。
脚本都在本机的 Git Bash 里运行，通过 SSH 操作服务器；服务器只认密钥登录。

| 脚本 | 什么时候用 |
|---|---|
| `deploy.sh` | 更新代码：本机打包前端，后端源码传到服务器上打包（OCR 本地库按 Linux 选），然后重启后端。第一次用加 `--setup` |
| `server-setup.sh` | 由 `deploy.sh --setup` 在服务器上跑：时区、swap、JDK 21、MySQL、Nginx、后端服务、每日备份。可以重复跑 |
| `set-mail.sh` | 配置发验证码用的邮箱（SMTP 授权码在自己终端里输入，不进代码库） |
| `set-llm.sh` | 配置 AI 功能用的大模型（API Key 同上） |

```bash
bash deploy/deploy.sh                                  # 默认用 ~/.ssh/jingda_ecs 这把密钥
KEY=~/.ssh/你的私钥 bash deploy/deploy.sh              # 用自己的密钥
SERVER=root@新IP bash deploy/deploy.sh --setup         # 换一台新服务器
```

## 服务器上的东西在哪

| 内容 | 位置 |
|---|---|
| 后端 | systemd 服务 `jingda`，以 `jingda` 用户运行 `/opt/jingda/app.jar`，只监听 `127.0.0.1:8090` |
| 后端配置 | `/opt/jingda/application-local.yml`（数据库账号等），另外读同目录的 `mail.yml`、`llm.yml`；不要用本机的配置覆盖 |
| 前端 | `/var/www/jingda`（Nginx 的 root），配置在 `/etc/nginx/sites-available/jingda` |
| 截图认证的图片 | `/opt/jingda/uploads`，审核完就删 |
| 数据库 | MySQL 8.0，库名 `team_match`；结构由 Flyway 在后端启动时自动升级 |
| 备份 | 每天凌晨 4 点备份到 `/var/backups/jingda`，留 7 天；`systemctl start jingda-backup` 马上备份一次 |

常用命令：`systemctl restart jingda`、`systemctl status jingda`、`journalctl -u jingda -f`。

验证码按 IP 限量靠 Nginx 的 `proxy_set_header X-Real-IP $remote_addr;`（`nginx-jingda.conf` 里 `/api/` 那段），改 Nginx 配置时别删掉，不然所有人都会被算成同一个 IP。
