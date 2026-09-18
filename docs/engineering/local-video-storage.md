# 本地视频上传与 Nginx 播放

## 配置
在工程根目录启动后端（local profile），并在 .env 中配置：

```properties
MEDIA_STORAGE_TYPE=local
MEDIA_ROOT=document/media
MEDIA_LOCAL_URL_PREFIX=http://localhost:8088/media
MEDIA_HTTP_PORT=8088
MEDIA_BIND_ADDRESS=127.0.0.1
```

也可直接修改 yingliu-admin/src/main/resources/application-local.yml 中的 media.storage-root 和 media.local-url-prefix。环境变量/.env 的对应值会覆盖 YAML 中的默认值。

MEDIA_ROOT 为磁盘目录。相对路径按后端工作目录解析，Docker Compose 按工程根目录解析，二者必须一致。Windows 绝对路径使用正斜线，例如 D:/videos；Java properties 中不要直接填写单个反斜线。URL 前缀必须对应 Nginx 的 /media/ 路径，不能填写文件系统路径。

## 启动 Nginx
需要 Docker Desktop 已运行，在 ticktok-api 根目录执行：

```powershell
New-Item -ItemType Directory -Path document/media -Force
docker compose -f compose.media.yml up -d
docker compose -f compose.media.yml exec -T media nginx -t
```

若 MEDIA_ROOT 使用其他目录，先创建该目录。仅运行此 Compose 文件不会启动或修改数据库、Redis、Java 后端。

之后重新启动 IDEA 的 YingliuApplication（JDK 17、local profile、工作目录为本工程根目录），或重新打包后运行。已运行的旧 JAR 不会自动加载 Java 修改。

## 上传与数据库
文件上传接口为 `POST /api/v1/admin/videos/upload`（Content-Type multipart/form-data）；仅以 JSON 引用已有媒体创建草稿为 `POST /api/v1/admin/videos`（application/json）；替换已有视频文件为 `PATCH /api/v1/admin/videos/{videoId}/file`。路径即语义，不再按 Content-Type 区分两个接口；管理端前端的上传与"替换视频"调用需指向上述新路径：

- 必填字段：file、authorId、title、categoryIds。
- 可选字段：description、sourceNote、tagIds、visibility。
- authorId/categoryIds 必须使用当前数据库里存在的作者、分类 ID。

上传文件按 SHA-256 生成相对路径，例如 ab/<sha256>.mp4，写入 MEDIA_ROOT/ab/<sha256>.mp4。
media_resource.relative_path 保存相对路径，media_resource.url 保存完整链接：
http://localhost:8088/media/ab/<sha256>.mp4。

管理端响应 data.originalMedia.url 返回同一地址；App 现有视频返回逻辑继续使用该媒体 URL。
上传仍创建草稿，C 端信息流展示仍遵循原有审核/发布规则。

重复上传同一内容时，如果当前本地目录没有文件，会重新落盘并更新已有媒体记录；若本地文件已存在，会更新当前访问 URL 并复用文件。仅切配置不会批量迁移历史视频或自动重写全部数据库 URL。

## 验证
从上传响应中复制 originalMedia.url：

```powershell
curl.exe -I "http://localhost:8088/media/ab/实际文件名.mp4"
curl.exe -s -D - -o NUL -H "Range: bytes=0-31" "http://localhost:8088/media/ab/实际文件名.mp4"
```

预期完整请求为 200、Content-Type 为 video/mp4；范围请求为 206 并包含 Content-Range。浏览器或前端播放器可直接使用 URL。自动化单测中的测试字节不是可解码视频；画面/声音验收请上传真实浏览器兼容的 MP4。

## 手机或其他电脑调试
localhost 指向观看设备自己。需要局域网访问时，将 MEDIA_LOCAL_URL_PREFIX 改为开发机的局域网地址（例如 http://192.168.1.20:8088/media），MEDIA_BIND_ADDRESS 改为 0.0.0.0，按需放行防火墙端口并重建媒体容器；重启后端后再上传。

此 Nginx 文件是本地调试的直接访问配置，拥有链接即可读文件。HTTPS 前端须配套 HTTPS 视频地址；生产访问鉴权、OSS 备份和转码不在本次修改范围内。

停止媒体服务：docker compose -f compose.media.yml down。宿主机视频目录不会被删除。
