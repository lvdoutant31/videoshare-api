# videoshare Docker 发布包

本包面向 Linux Docker 服务器，包含 API 与 Nginx 镜像、管理端和 H5 静态文件。MySQL、Redis 与媒体文件不会打包：数据库连接信息在服务器 `.env` 中配置，媒体文件保存在本目录 `media/`。

## 部署

1. 将 ZIP 上传到服务器并解压到一个独立版本目录。
2. 进入解压目录，复制并编辑配置：

   ```sh
   cp .env.example .env
   vi .env
   ```

   设置现有 MySQL 和 Redis 的地址、库名、账号和密码。若服务运行在 Docker 主机上，可使用 `host.docker.internal`；若位于其他主机，填入服务器可访问的 DNS 名称或 IP。账号需有目标数据库的读写权限及 Flyway 所需的建表／迁移权限。

3. 启动服务：

   ```sh
   chmod +x deploy.sh
   ./deploy.sh
   ```

   脚本会导入归档内的 API 与 Nginx 镜像、应用数据库迁移，并启动容器。默认监听 `8092`，可在 `.env` 修改 `WEB_PORT`。让已有 HTTPS 网关将流量转发到此 HTTP 端口。

4. 验证：

   ```sh
   docker compose -f compose.yml ps
   curl -fsS http://127.0.0.1:${WEB_PORT:-8092}/videoshare/health
   curl -fsS http://127.0.0.1:${WEB_PORT:-8092}/videoshare/h5/
   ```

## 运维

- 发布前备份目标 MySQL 数据库。应用首次启动会自动运行随 JAR 打包的 Flyway 迁移。
- `media/` 保存用户上传与生成的媒体文件，发布和回滚时都要保留此目录。
- 查看日志：`docker compose -f compose.yml logs -f app` 或 `... logs -f web`。
- 停止服务：`docker compose -f compose.yml down`。该命令不会删除数据库或 `media/`。
- ZIP 不含密钥、生产数据库数据、TLS 证书或用户上传文件。
