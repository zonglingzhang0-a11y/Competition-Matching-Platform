## 做了什么

<!-- 一两句话：做了什么功能、修了什么问题，为什么这么做 -->

## 怎么测的

- [ ] 后端 `./mvnw test`（本机没有 JDK 21 的话看下面 CI 的结果）
- [ ] 前端 `npm run type-check`、`npm run lint`、`npm test`、`npm run build-only`
- [ ] 接口测试 `python tests/e2e/run.py`（或者等 CI）
- [ ] 浏览器里把改到的页面点过一遍，手机宽度也看过

## 改了数据库吗

- [ ] 没改
- [ ] 改了：新加的迁移文件是 `db/migration/V……__…….sql`（没有改已经合并的迁移文件），跑过 `python tests/e2e/run.py migrate`

<!-- 详细的约定见 CONTRIBUTING.md -->
