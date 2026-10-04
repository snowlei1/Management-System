# 课程思政教学资源管理系统

课题名称：基于SpringBoot的课程思政教学资源管理系统设计与实现。

当前完成第一、二阶段：认证权限、管理员用户管理、基础前端框架，以及课程、课程思政元素和资源分类管理。教学资源、上传、审核、收藏、下载与统计业务尚未开发，不能将本阶段页面视为完整系统。

## 技术环境

JDK 21.0.11、Maven 3.9.9、Spring Boot 3.5.7、Spring Security 6.5.6、Spring JDBC、MySQL Server 8.0.39、Vue 3.5.13、Vue Router 4.5.1、Vite 6.3.5、Node.js 22.14.0、pnpm 11.25.0。后端 MySQL Connector/J 由 Spring Boot 管理，当前解析版本为 9.4.0。

认证采用 Spring Security 服务端会话、HttpOnly Session Cookie 和 CSRF 令牌；密码采用 BCrypt(12) 散列。没有使用 JWT、Redis、OAuth2 或额外的令牌表。角色固定为 `ADMIN`、`TEACHER`、`STUDENT`，每个用户只关联一个角色。

## 初始化数据库

本机项目库为 `management-system`。先使用 MySQL Workbench 或客户端执行 [`database/schema.sql`](database/schema.sql)，再仅在**本地开发环境**执行 [`database/dev-seed.sql`](database/dev-seed.sql)。前者建立既有设计的 11 张表；后者加入三种固定角色和三个开发测试账号，不会覆盖同名已有用户。

已有第一阶段数据库只需执行 [`002-resource-category-description.sql`](database/migrations/002-resource-category-description.sql)，为资源分类增加可空说明字段；该迁移可重复执行，不重建表或删除数据。更新后的 `schema.sql` 用于新库初始化，`CREATE TABLE IF NOT EXISTS` 不会自动升级旧表。中文路径可能导致 MySQL 命令行 `SOURCE` 无法打开文件，可在 Workbench 打开并执行该脚本。

`dev-seed.sql` 仅供毕业设计本地测试，**不得在公开或正式环境运行**。如果将系统部署到其他环境，应创建独立账号并使用新密码；不要沿用下表的测试密码。

| 角色 | 本地测试账号 | 本地测试密码 |
|---|---|---|
| 管理员 | `dev_admin` | `AdminDev#2026` |
| 教师 | `dev_teacher` | `TeacherDev#2026` |
| 学生 | `dev_student` | `StudentDev#2026` |

数据库连接写在 `backend/config/application-local.yml`，可从 [`application-local.example.yml`](backend/config/application-local.example.yml) 复制并修改。实际配置文件已加入 `.gitignore`，**不要提交数据库账号密码**。也可使用 `DB_URL`、`DB_USER`、`DB_PASSWORD` 环境变量覆盖连接信息。建议本地使用仅获项目库权限的账号；本阶段本机验证暂使用已有连接。

## 运行

在 `backend/` 执行：

```powershell
mvn spring-boot:run
```

另开终端，在 `frontend/` 执行：

```powershell
pnpm install --frozen-lockfile
pnpm dev
```

浏览器访问 `http://127.0.0.1:5173/`。Vite 将 `/api` 代理至本机 8080 端口。前端不保存密码或令牌；后端负责最终角色校验，前端菜单和路由守卫只是界面层控制。

## 本轮接口

| 方法与路径 | 作用 | 权限 |
|---|---|---|
| `GET /api/auth/csrf` | 获取当前会话的 CSRF 令牌 | 公开 |
| `POST /api/auth/login` | 账号密码登录 | 公开，需 CSRF |
| `GET /api/auth/me` | 当前用户信息 | 已登录 |
| `POST /api/auth/logout` | 退出并失效会话 | 已登录，需 CSRF |
| `GET /api/users` | 分页、关键词、角色、状态查询 | 管理员 |
| `POST /api/users` | 新增教师或学生 | 管理员 |
| `PUT /api/users/{id}` | 修改账号、姓名及允许的角色 | 管理员 |
| `PATCH /api/users/{id}/status` | 启用或禁用非管理员账号 | 管理员 |
| `GET /api/system/ping` | 基础数据库连通检查 | 已登录 |

响应统一为 `{code,message,data}`。管理员账号不能通过用户管理接口新增或停用；已有资源创建者不能随意改变角色；禁用用户的既有会话在下一次请求时失效。

第二阶段基础数据接口（将 `{type}` 替换为 `courses`、`ideological-elements` 或 `resource-categories`）：

| 方法与路径 | 作用 | 权限 |
|---|---|---|
| `GET /api/{type}` | 分页、关键词查询、状态筛选 | ADMIN |
| `POST /api/{type}` | 新增，默认 ACTIVE | ADMIN，需 CSRF |
| `PUT /api/{type}/{id}` | 修改编号/名称及说明，不改变状态 | ADMIN，需 CSRF |
| `PATCH /api/{type}/{id}/status` | 启用/停用 | ADMIN，需 CSRF |
| `GET /api/options/{type}` | 仅启用数据，返回 id/name 及课程编号 | 三类已登录角色 |

基础数据状态仅为 `ACTIVE`/`INACTIVE`，不是用户状态 `ACTIVE`/`DISABLED`。不提供 DELETE。名称/课程编号去除前后空白后校验；停用不释放唯一值，不删除任何历史关联。管理员菜单“基础数据管理”包含三个对应管理页面；教师、学生没有维护入口。

## 验证与设计文档

后端单元测试：在 `backend/` 执行 `mvn test`。前端构建：在 `frontend/` 执行 `pnpm build`。本机真实数据库、HTTP 与浏览器操作结果见 [`docs/10-认证权限与用户管理实现记录.md`](docs/10-认证权限与用户管理实现记录.md)。原设计基线保存在 [`docs/`](docs/01-需求分析.md)，本轮没有改动任务书和开题报告。

第二阶段真实结果见 [`docs/11-课程思政元素与资源分类实现记录.md`](docs/11-课程思政元素与资源分类实现记录.md)。本机前后端运行且初始化开发账号后，在项目根目录执行 `./scripts/test-stage2.ps1`；脚本默认走 5173 的前端代理，亦可通过 `-BaseUrl http://127.0.0.1:8080` 直接验证后端。测试只创建带随机标识的本地条目，结束或失败时在 `finally` 中停用保留，不物理删除。测试账号密码可由 `STAGE2_ADMIN_PASSWORD`、`STAGE2_TEACHER_PASSWORD`、`STAGE2_STUDENT_PASSWORD` 提供；默认值仅适用于本地开发种子账号。
