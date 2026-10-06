# 课程思政教学资源管理系统

课题名称：基于SpringBoot的课程思政教学资源管理系统设计与实现。

当前完成第一至七阶段：认证权限、用户与基础数据管理、教师资源建设、提交审核、驳回重提与审核发布、教师/学生资源使用、基础统计，以及统一前端、三角色工作台/门户、课程导航、思政专题和本人历史展示。已在本地开发/测试环境验证既定核心业务闭环；不代表生产部署或真实教学应用效果。发布后修改、撤回、下架与版本管理不在当前实现范围内。

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

## 第三阶段：教师资源草稿

第三阶段建立教师菜单“教学资源管理 / 我的资源”。草稿允许0..N个元素；第四阶段已经落实提交时至少1个且全部有效的规则。管理员和学生不能维护教师资源，其他教师不可访问他人专属资源。

| 方法与路径 | 作用（均只允许 TEACHER） |
|---|---|
| `GET /api/teacher/resources` | 本人未删除四状态分页列表，keyword/courseId/categoryId/status筛选 |
| `GET /api/teacher/resources/upload-policy` | 允许扩展名和单文件大小上限，不返回目录 |
| `GET /api/teacher/resources/{id}` | 本人未删除四状态详情及审核历史 |
| `POST /api/teacher/resources` | multipart：metadata JSON + 必传file，需CSRF |
| `PUT /api/teacher/resources/{id}` | 本人DRAFT/REJECTED，multipart完整metadata + 可选file，需CSRF |
| `DELETE /api/teacher/resources/{id}` | 本人DRAFT/REJECTED软删除，需CSRF；保留关联、历史和当前文件 |

metadata仅包含 `title`、`description`、`courseId`、`categoryId`、`elementIds`；不得指定创建人、状态或服务器路径。编辑时提交完整元数据；不传file才保留原文件，传空file会被拒绝。

在backend工作目录启动时，默认受控目录为 `backend/uploads/resources`。用 `RESOURCE_STORAGE_DIR` 指定其他项目专属目录，保持备份和读写权限；不要映射为静态公开目录。单文件20 MiB，multipart请求21 MiB；`RESOURCE_MAX_FILE_SIZE` 同时覆盖服务层与Servlet的文件上限，调整时应相应设置 `RESOURCE_MAX_REQUEST_SIZE`。白名单为PDF、DOC/DOCX、PPT/PPTX、XLS/XLSX、JPG/JPEG、PNG，集中在 `app.resource-storage.allowed-extensions`。响应不含存储键或绝对路径。

扩展名、Content-Type、大小及基本签名/Office容器结构检查不是病毒扫描或完整文档解析。当前不支持宏OOXML、加密OOXML或视频；旧Office文档只检查复合文件头和相应流名。浏览器若不能识别类型，应正确导出文件，不要把不明二进制强行作为文档上传。

真实第三阶段记录见 [`docs/12-教学资源草稿与文件上传实现记录.md`](docs/12-教学资源草稿与文件上传实现记录.md)。运行 `./scripts/test-stage3.ps1` 做真实HTTP测试；`-KeepFixtures` 可保留启用基础数据供浏览器验证，资源测试条目仍会软删除。脚本经管理员接口创建第二教师 `dev_teacher_b`，本地测试密码 `TeacherBDev#2026`（不是dev-seed初始化账号）；仅用于本机权限测试，不用于正式部署。

后端默认单元测试不依赖MySQL；真实事务测试需已初始化本机开发库，在backend目录先设置进程环境变量 `STAGE3_MYSQL_TEST=true` 再运行 `mvn test`。事务测试受控注入写关联后的异常，核验真实数据库回滚和文件补偿；正常提交的测试条目按软删除策略保留。上传目录、tmp测试附件及本地连接配置不提交Git。

## 第四阶段：提交审核与发布

| 方法与路径 | 行为与权限 |
|---|---|
| POST /api/teacher/resources/{id}/submit | TEACHER本人草稿/驳回，需CSRF；全量重新校验，成功后PENDING且轮次+1 |
| GET /api/admin/resource-reviews | ADMIN；默认PENDING，可筛选PENDING/REJECTED/APPROVED/ALL，关键词、课程、分类、教师及分页 |
| GET /api/admin/resource-reviews/{id} | ADMIN已提交三状态详情与历史；草稿404 |
| GET /api/admin/resource-reviews/{id}/attachment | ADMIN审核材料；PDF/图片可inline，Office或download=true为attachment；不记下载事件 |
| POST /api/admin/resource-reviews/{id}/approve | ADMIN+CSRF，JSON {submissionNo}，通过并发布 |
| POST /api/admin/resource-reviews/{id}/reject | ADMIN+CSRF，JSON {submissionNo,reason}，原因1—1000字符 |

仅DRAFT/REJECTED可编辑/提交；PENDING/APPROVED教师写操作409。审核FOR UPDATE锁定资源，并核对PENDING及页面轮次；竞争请求/旧轮次页面409。审核历史与状态在同一事务提交，APPROVED才有published_at。管理员可追溯已驳回/通过资源，但不能编辑正文或看从未提交草稿。附件目录不公开；PDF.js 4.10.38仅用于审核页显示授权材料，不更改安全响应头。

真实记录见 [`docs/13-教学资源审核与发布实现记录.md`](docs/13-教学资源审核与发布实现记录.md)。运行 `./scripts/test-stage4.ps1`；-KeepFixtures保留启用基础数据供浏览器验证，批准的测试资源因本轮没有下架接口而留存，草稿/驳回测试资源软删除。后端设置 `STAGE4_MYSQL_TEST=true` 可执行8项真实MySQL测试（包括双管理员HTTP竞争和事务故障注入）；同时设置STAGE3_MYSQL_TEST可回归上传补偿。

MySQL并发测试仅在本地库准备 `dev_admin_b`，复制dev_admin的BCrypt散列用于第二管理员独立Session（默认开发密码同AdminDev#2026）。不增加管理员创建业务API，不在Java源码硬编码正式密码；可用STAGE4_ADMIN_PASSWORD覆盖测试登录密码。所有固定开发账号、root连接和公开测试密码禁止用于正式部署。审核历史追溯结论，不保存每轮附件快照；已发布版本管理另行设计。

## 第五阶段：资源中心与使用记录

教师/学生的“资源中心”与教师“我的资源”生命周期管理分开。ADMIN仍使用独立审核入口，不能调用普通资源使用API。可见性统一为 `status='APPROVED' AND deleted_at IS NULL AND published_at IS NOT NULL`，包括我的收藏，不靠页面隐藏进行安全控制。

| 方法与路径 | 行为（仅TEACHER/STUDENT） |
|---|---|
| GET /api/resources | keyword匹配标题/简介，courseId/categoryId/elementId组合筛选；page/size分页；发布时间及ID降序 |
| GET /api/resources/{id} | 公开详情，每次成功GET记1条浏览事件，HEAD不记 |
| GET /api/resources/{id}/preview | 重新校验可见性与文件；PDF/PNG/JPEG预览，不计浏览/下载 |
| GET /api/resources/{id}/download | 授权校验文件后返回附件，准备响应时记下载请求事件，HEAD不记 |
| POST /api/resources/{id}/favorite | 需CSRF，幂等收藏；Session用户/资源唯一关系 |
| DELETE /api/resources/{id}/favorite | 需CSRF，幂等取消，保留active=FALSE关系 |
| GET /api/favorites | 本人当前有效且仍公开资源，基本分页 |

PDF使用本地PDF.js worker、图片授权Blob；Office只给下载提示，不引入第三方在线转换。下载链接始终指向受保护API，MIME/UTF-8中文名、安全头由后端返回。普通VO不含storage key或审核内部字段。浏览事件不是独立访问人数；下载事件不保证客户端完整接收。管理员审核附件不计普通使用事件。

真实记录见 [`docs/14-资源中心与使用记录实现记录.md`](docs/14-资源中心与使用记录实现记录.md)。运行前启动本机MySQL、后端及前端，在根目录执行 `./scripts/test-stage5.ps1`。脚本读取不跟踪的本地配置核对事件；开发账号可由STAGE5_ADMIN_PASSWORD、STAGE5_TEACHER_PASSWORD、STAGE5_STUDENT_PASSWORD及STAGE5_OTHER_TEACHER_PASSWORD覆盖（第二教师须先按第三阶段说明准备）。所有测试仅在本地开发库运行。

脚本默认结束时停用基础数据，草稿/驳回软删除，已通过/待审核夹具保留；`-KeepFixtures`保持基础数据启用以供浏览器操作。软删除隔离和缺文件场景只对带随机标签的测试夹具执行SQL/临时文件移动，finally恢复文件，不新增下架API。上传文件、真实落盘下载、构建产物和本地配置不提交Git。

后端需同时设置 `STAGE3_MYSQL_TEST=true`、`STAGE4_MYSQL_TEST=true`、`STAGE5_MYSQL_TEST=true`后执行 `mvn test`，才能跑全量172项（含20项真实MySQL）。不设置时真实数据库测试会跳过，不能当作全量通过。第五阶段沿用11张表，没有新字段或索引；现有索引和EXPLAIN的排序限制均在docs/14记录。

## 第六阶段：管理员统计与系统联调

ADMIN菜单“统计概览”对应 /statistics，调用 `GET /api/admin/statistics/overview`；教师/学生403，未登录401。统一Session认证和响应结构不变。展示用户角色/状态、三种基础数据总数/启用数、资源状态和独立软删除数、累计浏览/下载与当前公开收藏，以及已发布资源的课程/分类/元素分布。卡片和每页10项表格，无新增图表依赖。

当前资源排除软删除；已发布要求APPROVED、未删除、发布时间非空。浏览/下载保留软删除历史；收藏只计active=TRUE且资源仍已发布；分布包含停用基础数据历史关联与零资源项，多元素合计不等于资源总数。仅显示本地开发/测试库数据，不做UV、趋势、排行、推荐或学习效果评估。

最终测试在backend目录启用四组真实MySQL测试开关：

```powershell
$env:STAGE3_MYSQL_TEST = 'true'
$env:STAGE4_MYSQL_TEST = 'true'
$env:STAGE5_MYSQL_TEST = 'true'
$env:STAGE6_MYSQL_TEST = 'true'
mvn -B package
```

当前全量181项（157单元、24真实MySQL），未启用开关会跳过数据库测试，不能记作全量通过。测试仅用于初始化好的本地开发库；不用于生产库。统计事务夹具回滚，空库场景用连接级临时表，不删除真实业务数据；其他阶段按原有软删除/历史保留策略处理夹具。

启动MySQL、后端、前端后，在项目根目录运行 `./scripts/test-stage2.ps1` 至 `./scripts/test-stage6.ps1` 完成HTTP回归。stage6通过独立SQL核对统计并跑通两轮审核与资源使用；会保留已发布测试资源和历史，基础数据结束时停用。脚本仅供本机公开开发种子账号，不提交数据库凭据。执行 `./scripts/check-system-consistency.ps1` 做全库和受控文件目录只读对账，不自动清理。自定义目录可传 `-StorageDirectory` 或使用RESOURCE_STORAGE_DIR。

真实测试、浏览器三角色闭环、SQL口径、安全检查、已知限制详见 [`docs/15-基础统计与系统联调测试记录.md`](docs/15-基础统计与系统联调测试记录.md)。本轮不修改用户Word材料，不继续写论文。

## 第七阶段：统一前端与资源门户

新增 ECharts 5.6.0 和统一图标 @lucide/vue 1.52.0，其余技术版本与 Session/CSRF 认证不变。管理员使用真实概览、待审核事项、只读台账与统计图；教师使用本人四状态/驳回原因/资源表现；学生使用检索与资源卡片门户。支持侧边栏折叠、窄屏抽屉、Loading/Empty/Toast，保留全部既有写业务规则。

新增课程导航 `/course-resources` 及详情、思政专题 `/ideological-topics` 及详情、本人 `/history/browse`、`/history/downloads`，仅教师/学生；新增管理员只读 `/published-resources`。课程/专题仅组织既有资源，不扩展教学管理。详情“同课程/相同思政元素”是固定关联，不是推荐；历史页仅显示当前仍已发布资源，不删除原始事件或改变管理员累计口径。

新增10个GET映射：`/api/portal/courses`及`/{id}`、`/api/portal/ideological-topics`及`/{id}`、`/api/portal/resources/{id}`、`/api/history/{kind}`（browse/downloads）、`/api/teacher/resource-dashboard`、`/api/teacher/resource-presentations?ids=...`、`/api/admin/published-resources`、`/api/admin/review-overview`。各自角色限制与原接口一致，不新增表或写接口。

最新全量后端验收需同时设置 STAGE3/4/5/6/7_MYSQL_TEST=true 再运行 `mvn -B package`；本阶段实际214项通过、零跳过（182单元/服务/文件，32真实MySQL）。上文181项等数字为对应阶段历史记录。前端运行 `pnpm build`；已在当前环境构建成功。第七阶段HTTP回归执行 `./scripts/test-stage7.ps1`，需先准备 README 所述本地开发账号及第三阶段第二教师；会保留批准的测试资源与历史，finally软删除草稿并停用基础数据，不用于生产库。缺少PDF夹具时仅生成本地测试文件。

真实测试、完整接口表、菜单、20个1440×900截图清单、浏览器三角色及1366/窄屏检查、已知限制见 [`docs/16-前端信息架构与视觉交互优化记录.md`](docs/16-前端信息架构与视觉交互优化记录.md)。截图/原始日志只保留在本机 tmp/stage7 等忽略目录，不上传用户材料、密码或附件。

## 第八阶段：课程思政品牌视觉重构

采用暖白侧栏、浅红选中态、深红品牌重点及暖金细节。管理员使用治理工作台，教师使用资源建设工作台，学生使用教学资源门户；课程导航与思政专题采用不同布局，资源详情以预览为主，审核历史移至材料/操作区下方。仅调整前端呈现，不新增依赖、数据库表或后端接口，不改变 Session、CSRF、权限、资源状态和统计口径。

本阶段最终 `pnpm build` 成功，第七阶段关键 HTTP 回归实际通过175项断言（99次检查请求、4次夹具收尾请求），三角色浏览器关键流程及窄屏检查已执行。16张1440×900重点截图保存在本机 `tmp/stage8/`，不提交Git。真实执行结果、截图清单、视觉取舍和限制见 [`docs/17-红色主题与去模板化视觉重构记录.md`](docs/17-红色主题与去模板化视觉重构记录.md)。这些页面使用本地开发/测试数据，不代表真实教学使用效果；本阶段未重新执行全量后端214项测试。

## 第八阶段：课程思政品牌视觉重构

采用暖白侧栏、浅红选中态、深红品牌重点及暖金细节。管理员使用治理工作台，教师使用资源建设工作台，学生使用教学资源门户；课程导航与思政专题采用不同布局，资源详情以预览为主，审核历史移至材料/操作区下方。仅调整前端呈现，不新增依赖、数据库表或后端接口，不改变 Session、CSRF、权限、资源状态和统计口径。

本阶段最终 `pnpm build` 成功，第七阶段关键 HTTP 回归实际通过175项断言（99次检查请求、4次夹具收尾请求），三角色浏览器关键流程及窄屏检查已执行。16张1440×900重点截图保存在本机 `tmp/stage8/`，不提交Git。真实执行结果、截图清单、视觉取舍和限制见 [`docs/17-红色主题与去模板化视觉重构记录.md`](docs/17-红色主题与去模板化视觉重构记录.md)。这些页面使用本地开发/测试数据，不代表真实教学使用效果；本阶段未重新执行全量后端214项测试。
