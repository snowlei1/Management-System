# 课程思政教学资源管理系统

课题：基于SpringBoot的课程思政教学资源管理系统设计与实现。

当前仓库处于“系统分析与设计基线 + 可启动骨架”阶段。业务功能尚未实现，具体范围与模型见 [`docs/`](docs/01-需求分析.md)。

## 环境

- JDK 21、Maven 3.9.9
- Node.js 22、pnpm 11
- MySQL 8.0

## 本地运行

先在 MySQL 中创建项目专用数据库 `civics_resources`，并为项目账号授予该数据库所需权限。数据库凭据不提交仓库。PowerShell 示例：

```powershell
$env:DB_URL = 'jdbc:mysql://127.0.0.1:3306/civics_resources?useUnicode=true&characterEncoding=utf8'
$env:DB_USER = '你的项目账号'
$env:DB_PASSWORD = '你的项目密码'
cd backend
mvn spring-boot:run
```

另开终端启动前端：

```powershell
cd frontend
pnpm install --frozen-lockfile
pnpm dev
```

访问 `http://127.0.0.1:5173/`。页面通过 Vite 代理请求 `GET /api/system/ping`，后端执行 `SELECT 1` 验证 MySQL 连接。统一响应结构为 `{code,message,data}`。此接口只用于骨架连通性检查。

## 目录

- `docs/`：需求、权限、用例、流程、领域模型、数据库、架构及一致性检查。
- `database/schema.sql`：与 11 张逻辑表对应的 MySQL 建表脚本；当前骨架接口本身不依赖业务表。
- `backend/`：Spring Boot 最小后端、统一响应、统一异常和连接检查接口。
- `frontend/`：Vue + Vite 最小前端及接口通信。
