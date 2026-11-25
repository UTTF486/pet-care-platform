# 智慧宠物服务上门平台（爪爪到家）

面向宠物上门服务场景的 **Java 全栈项目**：用户下单预约 → 商家接单派单 → 服务师上门 → 完成评价，配套商家数据看板与后台管理。

不追求堆技术，追求 **业务完整、逻辑自洽、能讲清楚**。

---

## 一、技术栈

| 层次 | 技术 |
| --- | --- |
| 后端 | Java 8、Spring Boot 2.7、Spring MVC、MyBatis、Maven |
| 存储 | MySQL 8.0、Redis（StringRedisTemplate） |
| 前端 | Vue 3（`<script setup>`）、Element Plus、Axios、Vue Router、ECharts |
| 构建 | 后端 Maven，前端 Vite |

**当前未引入**：Spring Security、RabbitMQ、Elasticsearch、微服务和 Docker。项目保持单体架构，相关生产演进方向写在架构文档中。

---

## 二、功能清单

**用户端**
- 注册 / 登录（Token 存 Redis，支持禁用后立即踢下线）
- 修改密码（成功后撤销该用户所有旧登录态）
- 服务浏览：品类筛选、关键字搜索、热门推荐（Redis 缓存）、分页
- 宠物档案：增删改查，备注栏给服务师看（怕生 / 肠胃敏感 / 需喂药）
- 预约下单：选宠物 + 日期 + 时段 + 地址，金额后端计算防篡改；requestId 幂等防双击
- 我的订单：状态筛选、取消、催单（5 分钟去重）、评价、进度时间线

**商家后台**
- 数据看板：今日订单/营收、累计营收、好评率、订单营收趋势（日/周/月）、服务排行
- 订单审核：接单并指派服务师、拒单、标记完成
- 服务管理：新增/编辑/上下架（下架即清缓存）
- 服务师管理：技能、等级、可接单状态
- 用户管理：启用/禁用（禁用即刻踢下线）
- 消息中心：催单与新订单通知

**系统能力**
- 订单超时自动取消（`@Scheduled` 每分钟扫描）
- 接口限流（Redis Lua 原子计数 + 自定义注解 + AOP）
- 订单状态流转日志（可追溯）
- 统一返回结构 + 全局异常处理

---

## 三、目录结构

```
pet-service-platform/
├── backend/                                # 后端工程
│   ├── pom.xml
│   └── src/main/
│   │   ├── java/com/petcare/
│   │   │   ├── PetCareApplication.java      # 启动类（@MapperScan + @EnableScheduling）
│   │   │   ├── annotation/RateLimit.java    # 限流注解
│   │   │   ├── aspect/RateLimitAspect.java  # 限流切面（Redis 计数）
│   │   │   ├── common/                      # Result / ResultCode / PageResult
│   │   │   │                                # BusinessException / GlobalExceptionHandler
│   │   │   │                                # LoginUser / UserContext / OrderStatus
│   │   │   ├── config/                      # WebConfig + 可信代理 IP 解析
│   │   │   ├── controller/                  # 10 个 Controller，按端划分
│   │   │   ├── dto/                         # 入参对象（含校验注解）
│   │   │   ├── entity/                      # 9 张表对应实体
│   │   │   ├── init/DataInitializer.java    # local profile 升级种子密码
│   │   │   ├── interceptor/AuthInterceptor.java  # 登录 + 角色校验
│   │   │   ├── mapper/                      # 9 个 Mapper 接口
│   │   │   ├── service/                     # 业务层（含独立超时事务）
│   │   │   ├── task/OrderTimeoutTask.java   # 超时取消定时任务
│   │   │   ├── util/                        # Token / 时间 / 订单号工具
│   │   │   └── vo/                          # 出参对象
│   │   └── resources/
│   │       ├── application.yml
│   │       └── mapper/*.xml                 # 9 个 MyBatis 映射文件
│   └── src/test/java/com/petcare/           # Mapper、校验、时间和幂等逻辑测试
│
├── config/                                 # Jar 外本地密码配置（git 忽略）
├── frontend/                               # 前端工程
│   ├── package.json / vite.config.js / index.html
│   └── src/
│       ├── main.js / App.vue
│       ├── api/request.js                   # Axios 封装（Token 注入 + 401 跳转）
│       ├── api/index.js                     # 全部接口集中管理
│       ├── layout/UserLayout.vue             # 用户端布局
│       ├── layout/AdminLayout.vue            # 后台布局（含消息抽屉）
│       ├── router/index.js                   # 路由 + 登录/角色守卫
│       ├── store/user.js                     # 极简登录态（localStorage）
│       ├── styles/index.css
│       └── views/
│           ├── Login.vue
│           ├── user/  Home / ServiceDetail / MyOrders / MyPets
│           └── admin/ Dashboard / OrderManage / ServiceManage / ServicerManage / UserManage
│
├── sql/pet_service.sql                     # 全新建库 + 种子数据
├── sql/migration-*.sql                     # 已有数据库增量迁移
└── docs/                                   # 架构 / 数据库 / 接口 / 启动手册
```

---

## 四、环境要求

| 软件 | 版本 | 说明 |
| --- | --- | --- |
| JDK | 8 或 11 或 17 | 项目按 Java 8 编译，17 也能跑 |
| Maven | 3.6+ | 建议配阿里云镜像 |
| MySQL | 8.0+ | 5.7 也能跑，注意时区参数 |
| Redis | 5.0+ | Windows 可用 Memurai 或 WSL/Docker |
| Node.js | 16+ | 前端构建 |

---

## 五、启动步骤

### 1. 初始化数据库

```bash
# 方式一：命令行（会先 DROP 再 CREATE，确保是空库再执行）
mysql -uroot -p < sql/pet_service.sql

# 方式二：Navicat / DataGrip 打开 sql/pet_service.sql，全选执行
```

> ⚠️ 脚本第一行是 `DROP DATABASE IF EXISTS pet_service;`，只在本地开发库执行。

### 2. 启动 Redis

```bash
redis-server            # Linux / macOS
# Windows：启动 Memurai，或 docker run -d -p 6379:6379 redis:7
redis-cli ping          # 返回 PONG 即正常
```

### 3. 配置密码并启动后端

**仓库里没有任何真实密码**，`application.yml` 全部走 `${环境变量:默认值}`。本地开发只需两步：

```powershell
# ① 在项目根目录复制外置模板（这个 .example 文件是入库的模板）
copy config\application-local.yml.example config\application-local.yml
```

```yaml
# ② 编辑 config\application-local.yml，填自己的密码
spring:
  datasource:
    username: root
    password: 你的MySQL密码      # ← 只改这里
  redis:
    host: localhost
    port: 6379
    password: 你的Redis密码       # 没有密码就留空
```

> `config/application-local.yml` 已在 `.gitignore` 中，**不会被提交，也不会被打进 Jar**。
> 如果不使用文件，也可以直接设置环境变量：`MYSQL_PASSWORD` / `REDIS_PASSWORD`（见 `application.yml` 顶部注释）。

启动：

```powershell
cd backend
$env:SPRING_PROFILES_ACTIVE = "local"
mvn clean install -DskipTests
mvn spring-boot:run
```

`application.yml` 默认使用 `prod` profile，不会自动加载本地密码；只有显式设置
`SPRING_PROFILES_ACTIVE=local` 时才会导入项目根目录的 `config/application-local.yml`
和本地 SQL 调试日志配置。若启动目录不同，可用 `PET_LOCAL_CONFIG` 指定绝对路径。

看到 `智慧宠物服务上门平台后端启动成功` 即成功。

> 全新数据库的种子账号密码已经是 BCrypt 密文，默认演示密码仍为 `123456`。
> 旧数据库若还保留明文密码，`DataInitializer` 只会在 `local` profile 下自动升级。
> 这类种子账号是**演示用弱口令**，真实环境应改成强密码并走注册流程。

### 4. 启动前端

```bash
cd frontend
npm install
npm run dev
```

浏览器打开 http://localhost:5173 （Vite 配了 `open: true`，一般会自动弹）。

### 5. 默认账号

| 角色 | 账号 | 密码 | 进入 |
| --- | --- | --- | --- |
| 商家管理员 | `admin` | `123456` | 登录后自动进 `/admin/dashboard` |
| 普通用户 | `demo` | `123456` | 登录后进 `/user/home` |

登录页有"演示账号"快捷填充，点一下即可。

---

## 六、前后端联调说明

**请求链路**

```
浏览器 :5173  →  Vite devServer 代理 /api  →  Spring Boot :8080/api  →  MySQL / Redis
```

`frontend/vite.config.js` 里的代理是关键：

```js
proxy: { '/api': { target: 'http://localhost:8080', changeOrigin: true } }
```

因为浏览器始终访问 5173 同源地址，**开发阶段根本不存在跨域**。后端 `WebConfig` 里的 CORS 配置是给"直接访问 8080 或用 Postman 调试"准备的。

**统一返回结构**

```json
{ "code": 200, "message": "操作成功", "data": { } }
```

- `code=200`：`request.js` 直接 `return res.data`，页面拿到的就是业务数据
- `code=401`：前端清除本地 Token 并跳转 `/login`；HTTP 状态码也是 401
- 其他：`ElMessage.error(message)` 兜底提示

**登录态**

1. 登录成功 → 后端生成 UUID Token，`login:token:{token} -> userId`、`login:user:{userId} -> 用户信息` 双 key 存 Redis，有效期 2 小时
2. 前端存 `localStorage.token` + `user`
3. 每次请求 `Authorization: Bearer {token}`
4. 拦截器解析 → 放入 `ThreadLocal` → 请求结束 `clear()`

---

## 七、验证清单（建议按顺序点一遍）

| 步骤 | 操作 | 预期 |
| --- | --- | --- |
| 1 | `admin` 登录 | 直接进入数据看板，折线图/柱状图有 7 天历史数据 |
| 2 | 看板切"按周""按月" | 图表重新聚合 |
| 3 | `demo` 登录 → 预约服务 → 选"猫咪上门喂养" → 下单 | 提示预约成功，跳到我的订单，状态"待接单" |
| 4 | 我的订单点"催单" | 提示已提醒；再点一次提示"已经催过啦"（Redis 去重） |
| 5 | 切到 `admin` → 订单审核 | 能看到刚才那单，点"接单"选服务师 → 状态变"待服务" |
| 6 | 再点"完成" | 状态变"已完成"，服务销量 +1，看板数据变化（60 秒缓存） |
| 7 | 切回 `demo` → 我的订单 → 去评价 | 打分提交，按钮变"已评价" |
| 8 | 后台服务管理 → 下架某个服务 | 用户端首页该服务消失（Redis 缓存已清） |
| 9 | 后台用户管理 → 禁用 `lily` | 该用户已登录的会话立刻失效 |
| 10 | 连续快速下单 6 次 | 第 6 次提示"下单太频繁了"（限流生效） |

**验证超时取消**：把 `application.yml` 里 `pet.order.timeout-minutes` 改成 `1`，下单后等 2 分钟（定时任务每分钟跑一次），订单会自动变"已取消"，消息里出现"超时未接单，系统自动取消"。

---

## 八、常见坑（提前踩掉）

| 现象 | 原因 | 解决 |
| --- | --- | --- |
| 启动报 `Access denied for user 'root'` | 数据库密码不对 | 改 `application.yml` 的 `password` |
| 启动报 `Unknown database 'pet_service'` | 没执行 SQL | 先跑 `sql/pet_service.sql` |
| 接口 500，日志 `Unable to connect to Redis` | Redis 没启动 | 启动 Redis，`redis-cli ping` 验证 |
| 时间差 8 小时 | 连接串缺时区 | URL 里保留 `serverTimezone=Asia/Shanghai` |
| 中文乱码 | 库/表编码不是 utf8mb4 | 脚本已指定；自建库记得设 |
| 登录提示"账号或密码错误" | 直接查库看到的是密文 | 密码仍是 `123456`，BCrypt 不可逆，属正常 |
| 前端 404 / 接口全 502 | 后端没起或端口不对 | 确认 8080 被占用情况，改端口要同步改 `vite.config.js` |
| `npm install` 卡住 | 网络 | `npm config set registry https://registry.npmmirror.com` |
| Maven 下载慢 | 未配镜像 | `settings.xml` 加阿里云 mirror |
| 后台图表空白 | 数据被清空 | 重新执行 SQL 种子数据；或自己下几单 |
| **IDEA 编译报大量"找不到符号"**（`Result.java` 的 `getCode()`、`TokenService` 的 `log`） | IDEA 用的 JDK 版本超出 Lombok 支持范围（如 JDK 25），注解处理器失效，`@Data/@Slf4j` 生成的代码全没了 | 已在 `pom.xml` 修复：Lombok 固定 `1.18.48` + 显式声明 `annotationProcessorPaths`。改完在 IDEA 点 Maven 面板的 **Reload All Maven Projects**，再 `Build → Rebuild Project` |
| 切换 JDK 后编译报 `Error reading old mojo status ... Input length = 1` | `target/` 里留着另一个 JDK 用不同编码写的增量编译缓存 | `mvn clean` 清一次即可 |
| IDEA 里 Language Level 显示 25，代码飘黄/飘红 | 项目 SDK 自动识别成了高版本 JDK | 推荐统一到 JDK 17：`File → Project Structure → Project → SDK` 选 17，`Language level` 选 8 或 17 |

> 关于 JDK：本项目已在 **JDK 8 / 17 / 25** 下验证 `mvn clean test` 通过。为避免“IDEA 一个 JDK、命令行另一个 JDK”带来的缓存编码问题，建议全程统一用一个版本（推荐 17）。

---

## 九、自检命令

```bash
# 后端编译
cd backend && mvn -q -DskipTests compile

# Mapper XML 自检：校验 9 个 XML 能解析、接口方法都有对应 SQL（不需要数据库）
cd backend && mvn test -Dtest=MapperXmlParseTest

# 前端构建
cd frontend && npm run build
```

---

## 十、后续可扩展方向

1. **支付**：接入模拟支付，订单增加"待支付"状态与支付超时关单
2. **分布式调度**：定时任务换 XXL-JOB，避免多实例重复执行
3. **消息推送**：WebSocket / SSE 替代 30 秒轮询
4. **缓存治理**：热点 key 加本地缓存（Caffeine）做二级缓存，读多写少场景进一步降低 Redis 压力
5. **限流升级**：固定窗口换滑动窗口或 Sentinel，适配更高并发和更精细的流控策略
6. **读写分离**：报表查询走从库，主库专注交易

详细设计见 `docs/` 目录。

---

## 十一、安全与配置管理（这个仓库为什么不含密码）

开源项目把数据库密码写进 `application.yml` 是典型事故，本仓库做了多层处理：

**1）配置与代码分离**

`application.yml` 只写占位符，不含任何真实值：

```yaml
spring:
  datasource:
    password: ${MYSQL_PASSWORD:}
  redis:
    password: ${REDIS_PASSWORD:}
```

本地真实密码放在项目根目录的 `config/application-local.yml`（已在 `.gitignore` 中），
`config/application-local.yml.example` 作为模板入库。该目录在 `backend/src/main/resources`
之外，Maven 不会把它复制进 Jar。

**2）`.gitignore` 兜底**

```gitignore
config/application-local.yml
application-local.yml
application-*.local.yml
*.local.yml
.env
.env.*
.idea/          # IDEA 的 dataSources.xml 会存数据库连接信息
target/ node_modules/ dist/
```

**3）可信代理与登录保护**

限流默认使用 `getRemoteAddr()`。部署在 Nginx 后，需要把网关地址配置到
`TRUSTED_PROXY_ADDRESSES`，例如 `127.0.0.1/32` 或 `172.18.0.0/16`；只有直连地址在可信 CIDR 中时才读取
`X-Forwarded-For`。登录密码连续失败 5 次后，账号会锁定 15 分钟。
公开服务浏览在限流 Redis 故障时允许降级访问；登录、注册和改密则保持 fail-closed。

**4）密码本身不明文存业务库**

`t_user.password` 存 BCrypt 密文（自带随机盐，同一密码每次结果都不同），登录用 `passwordEncoder.matches()` 校验。全量 SQL 中的种子密码已直接写为 BCrypt；`123456` 只是演示用弱口令。

**万一误提交了怎么办**：改密码 → 用 `git filter-repo` 或 BFG 清理历史 → 强制推送。注意：**仅删除文件不够**，Git 历史里仍能翻出来。养成习惯：提交前 `git status` 看一眼，敏感文件永远先加 `.gitignore`。

**生产环境建议**：密码走环境变量或配置中心（Nacos / Apollo），配合 KMS 加密；不要把 `.env` 上传到服务器镜像里。
