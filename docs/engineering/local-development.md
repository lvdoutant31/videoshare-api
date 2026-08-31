# 本地开发

## 前置条件

- JDK 17
- Node.js 20
- Docker Desktop（用于 MySQL、Redis 与完整容器验证）

当前机器可能让 Maven 使用 JDK 21。执行后端命令前仅对当前 shell 固定 JDK 17：

```bash
export JAVA_HOME="D:/PrivateSoftware/Java/jdk17"
export PATH="$JAVA_HOME/bin:$PATH"
./mvnw -B verify
```

## 前端

pnpm 由 Corepack 管理，不需要全局安装：

```bash
corepack enable
corepack pnpm install --frozen-lockfile
corepack pnpm dev:h5
corepack pnpm dev:admin
```

开发服务器通过 Vite 代理访问 `http://localhost:8080`。

## 容器

```bash
cp .env.example .env
docker compose up --build
```

访问：H5 `http://localhost:8088/h5/`，后台 `http://localhost:8088/admin/`。

`.env` 仅用于本地，不能提交。默认凭据仅是开发占位值，部署前必须替换为受控密钥。
