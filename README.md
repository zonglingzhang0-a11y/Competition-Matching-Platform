# 竞赛组队匹配平台

把"谁会什么"和"哪个队缺什么"结构化记录下来，由系统推荐队友并给出推荐理由。面向多所学校的大学生，比赛范围是能在综测里加分的正规竞赛。

设计文档在 [docs/](docs/)：[设计方案](docs/设计方案.md)、[问卷 v2](docs/问卷草案-v2.md)、[数据库设计](docs/数据库设计.md)。

## 目录

```
backend/    Spring Boot 4 + MyBatis-Plus + Sa-Token（Java 21）
frontend/   Vue 3 + Vite + TypeScript + Pinia + Element Plus
sql/        schema.sql 建表，seed.sql 初始数据
docs/       设计文档
```

## 环境要求

- JDK 21
- Node.js 24（`^22.18` 或 `>=24.12`）
- MySQL 8.0.16 以上

Maven 不用另外装，`backend` 里自带 Maven Wrapper。

## 第一次运行

**1. 建库**

```bash
mysql -uroot -p < sql/schema.sql
```

```bash
mysql -uroot -p < sql/seed.sql
```

**2. 启动后端**（端口 8090）

把 `backend/src/main/resources/application-local.example.yml` 复制一份，改名为 `application-local.yml`，填上 MySQL 密码。这个文件不会被提交。

想要管理员账号，就在同一个文件的 `app.admin-emails` 里写上你的邮箱：用这个邮箱注册的账号自动成为管理员；如果已经注册过了，重启后端后会自动升级。

```bash
cd backend && ./mvnw spring-boot:run
```

Windows 的 cmd 或 PowerShell 里用 `mvnw.cmd spring-boot:run`。接口文档：<http://localhost:8090/swagger-ui.html>

**3. 启动前端**（端口 5173，`/api` 自动转发到后端）

```bash
cd frontend && npm install && npm run dev
```

打开 <http://localhost:5173>。

## 开发时的验证码

默认不真的发邮件，验证码打印在**后端控制台**，像这样：

```
[开发模式] 发往 a@test.local 的注册验证码：582675（10 分钟内有效）
```

想真的发邮件，在 `application-local.yml` 里配置 SMTP，并把 `app.mail.mode` 改成 `smtp`（模板里有 QQ 邮箱的例子）。

**演示教育邮箱认证**：`seed.sql` 里有一所"示例大学"，邮箱后缀是 `stu.example.edu.cn`。随便填一个 `xxx@stu.example.edu.cn`，去后端控制台拿验证码，就能认证成功。填其他学校的 `.edu.cn` 邮箱，会进入"学校待管理员确认"状态。

## 测试

```bash
cd backend && ./mvnw test
```

```bash
cd frontend && npm run build
```

前端的 `build` 会先做类型检查。

## 分支

- `main`：稳定版本
- `dev`：日常开发

## 进度

| 阶段 | 内容 | 状态 |
|---|---|---|
| M1 | 项目骨架、注册登录、教育邮箱认证 | ✅ |
| M2 | 比赛库（列表、详情、关注，管理员录入接口）、个人档案、技能和履历 | ✅ |
| M3 | 队伍、招募需求、申请和邀请、站内信 | 进行中 |
| M4 | 问卷、匹配打分、推荐理由 | |
| M5 | 日历三图层、撞期检测、截止提醒 | |
| M6 | 管理后台、数据看板 | |
