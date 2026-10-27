# 演示视频录制

用脚本驱动浏览器，按分镜操作本机测试环境上的网站，同时逐帧抓高清截图，合成 1080p 的 MP4。
画面里不带字幕，台词单独出一份 SRT：导入剪映后全选字幕，用"文本朗读"生成 AI 配音。

- `demo_kit.py`：录制工具。页面上画鼠标指针和点击圆圈、平滑移动和滚动、记字幕时间轴、抓帧合成 MP4。
- `serve_video.py`：录制用的后端，端口 18093，库 `team_match_video`，启动时重新导入演示数据。
- `record_sample.py`：约 1 分钟的样片。林晓登录 → 主页 → 匹配六边形和对比 → 推荐理由 → 数模冲刺队 → 申请加入。

## 怎么录

要求：Python 装好 `playwright`（再运行一次 `playwright install chromium`）、`opencv-python`、`pillow`；有一个能随便删库的 MySQL（和接口测试一样，用 `E2E_DB_*` 环境变量指定）。

1. 打包后端：在 `backend` 目录运行 `./mvnw package -DskipTests`。
2. 起后端：`python scripts/demo-video/serve_video.py`，等它打印 `ready`。
3. 起前端：用 `.claude/launch.json` 里的 `frontend-video`，或者 `npm --prefix frontend run dev -- --port 5176`，并把环境变量 `API_TARGET` 设成 `http://127.0.0.1:18093`。
4. 录：`python scripts/demo-video/record_sample.py`，输出到项目根目录的 `演示视频/样片/`（这个文件夹不进仓库）。

样片会真的发出一条入队申请，重录前把第 2 步的后端重启一次（会重新导入演示数据）。

## 输出

| 文件 | 用途 |
|---|---|
| `样片.mp4` | 不带字幕，导入剪映用 |
| `样片.srt` | 台词字幕，时间和画面对好了 |
| `样片_带字幕预览.mp4` | 字幕画在画面上，先看效果用 |
| `文案.md` | 每个镜头的时间、画面、台词 |
| `配音文稿.txt` | 只有台词，一句一行 |
| `frames/` | 抓到的原始帧，合成完可以删 |

## 改分镜

照 `record_sample.py` 写：`rec.say("台词", "画面说明")` 开始一句台词，接着用 `rec.click`、`rec.type`、`rec.move_to`、`rec.scroll_to` 操作页面。
每句台词按每秒 4 个字留时间，操作做完了台词还没念完，会自动等它念完再开始下一句。
