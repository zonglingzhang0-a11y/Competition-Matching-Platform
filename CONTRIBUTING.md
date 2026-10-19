# 一起开发

给队友看的：怎么拉分支、提交前跑什么、改了数据库要做什么、PR 怎么提。

## 分支

- `main` 只放发布的版本（打了 `v2.x.x` 标签的），测评用它。
- `dev` 是日常开发的分支。
- 做新功能或修问题：从 `dev` 拉一个分支，名字说清楚在做什么，比如 `feature/user-profile`、`fix/calendar-allday`；做完往 `dev` 提 PR。

```bash
git checkout dev && git pull
```

```bash
git checkout -b feature/你的功能
```

## 本机环境

JDK 21、Node 22.18 以上（或 24）、MySQL 8.0。第一次运行按 [README](README.md) 来。本机只有 JDK 17 也没关系，提 PR 后 GitHub 会用 JDK 21 跑一遍后端测试。

## 提交前自己跑一遍

```bash
cd backend && ./mvnw test
```

```bash
cd frontend && npm run type-check && npm run lint && npm test && npm run build-only
```

接口测试（可选，CI 也会跑；会删掉重建 `team_match_e2e` 这个库，不碰平时用的 `team_match`，见 README 的"测试"）：

```bash
python tests/e2e/run.py
```

再在浏览器里把改到的页面点一遍，手机宽度也看一眼。

## 改了数据库

1. `sql/schema.sql` 里改好（新建库的人直接用它）。
2. 在 `sql/upgrade/` 里加一个升级脚本给已经建过库的人，文件名按 `年-月-做什么.sql`，比如 `2026-10-user-privacy.sql`。能做到的话：
   - 可以重复跑（`CREATE TABLE IF NOT EXISTS` 这种）；
   - 不跑也不影响启动（后端读不到新表时退回旧的做法，日志里提示跑脚本）。
3. README "第一次运行"里的升级脚本表加一行，写清楚做什么、能不能重复跑。
4. `seed.sql`、`demo.sql` 要跟着改的一起改；演示数据里的日期按导入那天往后排（用 `CURDATE()`），不要写死。

## 写代码的约定

- 注释、提示语、提交信息都用中文，说人话。后端抛的 `BizException` 消息会原样显示给用户，要写成用户看得懂的话。
- 权限一定在后端检查，前端隐藏按钮只是体验。涉及个人信息的（真实姓名、联系方式、证书链接这类），默认只给需要的人看。
- 新接口写接口测试：在 `tests/e2e/` 里加一个脚本，加到 `run.py` 的 `SUITES` 里。纯计算的逻辑写单元测试（后端 `src/test`，前端 `src/**/__tests__`）。
- 不要提交 `application-local.yml`（里面有数据库密码），也不要把任何密钥写进代码。测试账号用 `@test.local` 的邮箱。

## 提 PR

- 往 `dev` 提，PR 描述写做了什么、怎么测的、有没有改数据库（模板里有）。
- PR 页面下方能看到 CI 的结果：后端测试、接口测试、前端检查。接口测试没过时，在 Actions 页这次运行的最下面下载 `e2e-logs`，里面有每套测试的输出和后端日志。
- 评审意见改完直接推到同一个分支，PR 会自动更新。
