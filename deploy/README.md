# 映流前后端部署

本目录用于生成一份不包含生产密码的 Windows 发布包。发布包包括：

- `api/yingliu-api.jar`：Spring Boot 后端；
- `api/logs/yingliu-api.log`：应用运行日志，单文件最多 20 MB，滚动保留 14 天；
- 可选的 `api/tools/ffmpeg/`：显式传入 `-FfmpegDirectory` 时随包携带的 Windows FFmpeg；
- `frontend/admin/`：管理端静态文件；
- `frontend/h5/`：H5 静态文件；
- `nginx/yingliu.conf`：按发布参数渲染的 Nginx 配置；
- `api/start-yingliu-api.bat`：Java 17 启动和配置检查；
- `deploy-windows-service.bat` 与 `api/YingliuApiService.exe`：安装、更新和管理 Windows API 服务；
- `verify-deployment.ps1`：上线后的健康检查。
- `repair-video-transcodes.ps1`：安全补建、重试并验证视频转码任务。
- `auto-repair-video-transcodes.ps1`：API 健康后自动批量修复当前推荐流。

## 1. 构建发布包

本机需安装 Java 17、Node.js、npm，并能访问 GitHub 下载固定版本的 WinSW x64。构建脚本会校验 WinSW v2.12.0 的 SHA-256。三个仓库默认位于同一父目录：

```text
gitClaude/
├── ticktok-api/
├── yingliu-b/
└── yingliu-c/
```

在 `ticktok-api` 根目录执行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\deploy\build-release.ps1 -Version 2026.09.24
```

产物位于 `output/releases/yingliu-2026.09.24/` 和同名 ZIP。默认 FFmpeg 路径根据 `-DeployRoot` 生成为 `<部署目录>/ffmpeg/ffmpeg-master-latest-win64-gpl-shared/bin/ffmpeg.exe`，也可用 `-FfmpegPath` 指定服务器绝对路径；如需随包携带，则传入包含 `bin/ffmpeg.exe` 及 shared DLL 的完整 `-FfmpegDirectory`。若仓库不在默认位置，可传入 `-AdminRepository`、`-H5Repository`；域名、证书或安装目录不同，可传入 `-PublicHost`、`-DeployRoot`、`-ListenPort`、`-SslCertificate`、`-SslCertificateKey`。

已有可靠构建产物、只需重新打包时可以增加 `-SkipBuild`。

## 2. 安装到服务器

1. 把发布包解压到服务器上的独立版本目录，例如 `F:\videoshare\releases\yingliu-<版本>`。安装服务后不要移动或重命名该目录，因为服务会从这个路径加载 JAR 和配置。
2. 保留上一版本完整目录。更新时把上一版本的 `api\config\yingliu-env.properties` 复制到新版本对应目录；真实配置不要提交到 Git 或放进发布 ZIP。
3. 首次安装时，将 `api\config\yingliu-env.properties.example` 复制为 `api\config\yingliu-env.properties`，编辑并核对 MySQL、Redis、媒体目录和企业微信参数。
4. 将便携 Java 17 解压到 `api\runtime\jdk-17\`，或设置 `YINGLIU_JAVA` 为 Java 17 的 `java.exe` 完整路径。若用环境变量，安装 BAT 会解析并把绝对路径写入服务配置。
5. 按配置设置 FFmpeg 路径，并确认服务账户能读写配置、日志和媒体目录。
6. 在版本目录右键 `deploy-windows-service.bat`，选择“以管理员身份运行”；不带参数时默认执行 `deploy`，完成或失败后保留窗口，按任意键关闭。也可以打开管理员 CMD，在版本目录运行 `deploy-windows-service.bat deploy`；显式传入命令时不暂停，并返回退出码。脚本会检查 Java、运行配置、FFmpeg 和服务 XML，安装或更新 `YingliuApi` 服务，配置延迟自动启动与故障重启，并等待本机健康端点通过。
7. 把 `frontend\admin`、`frontend\h5` 及 `nginx\yingliu.conf` 发布到 Nginx 当前使用的稳定网站目录。先执行 `nginx.exe -t`，成功后执行 `nginx.exe -s reload`。

服务管理命令如下，除 `status` 和 `check` 外均需管理员身份运行：

若部署失败，查看窗口中的 `[FAILED]` 信息。发布 ZIP 不包含真实配置，更新时先复制上一版本的 `api\config\yingliu-env.properties`，首次安装则按第 3 步填写配置；不要覆盖原数据库或媒体目录。仅检查环境、不安装或启停服务时，在 CMD 中执行 `deploy-windows-service.bat check`。

```bat
deploy-windows-service.bat status
deploy-windows-service.bat restart
deploy-windows-service.bat stop
deploy-windows-service.bat start
deploy-windows-service.bat uninstall
```

`deploy` 会重装同名服务并切换到当前版本目录；回滚时从保留的旧版本目录运行 `deploy-windows-service.bat deploy`。应用日志以 UTF-8 写入该版本的 `api\logs\yingliu-api.log`，管理端“运行日志”页面只显示当前 JVM 启动后的记录；重启后页面不再展示之前的日志，磁盘中的旧版本目录及 WinSW 输出日志仍按原方式保留。若仍要以前台命令窗口方式启动，可继续使用 `api\start-yingliu-api.bat`；不要让前台进程和 Windows 服务同时占用同一 API 端口。

前台启动器同时兼容标准发布包和服务器现有的根目录平铺方式。Windows 服务模式由 `deploy-windows-service.bat` 管理；服务安装后应使用服务管理命令，不要直接启动第二个 Java 进程。

## 3. 上线验证

在能访问生产域名的机器上执行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\verify-deployment.ps1 -BaseUrl https://www.ysd-hsdy.com.cn
```

脚本依次验证后端健康端点、H5 首页和管理端首页；任一失败都会以非零状态退出。

还应人工验证：管理端登录、H5 登录、视频列表、视频播放、上传和退出登录。

## 4. 修复并验证视频转码

脚本默认使用 `api\config\yingliu-env.properties`，检查 FFmpeg、MySQL 和当前推荐流，但不会修改数据库。先执行预检：

```powershell
.\repair-video-transcodes.ps1 -BaseUrl https://www.ysd-hsdy.com.cn:11443
```

确认目标视频和当前任务状态后，加 `-Apply` 补建缺失任务、重置失败/取消/超时任务，并等待接口切换到 720P 转码文件：

```powershell
.\repair-video-transcodes.ps1 -BaseUrl https://www.ysd-hsdy.com.cn:11443 -Apply
```

也可以只处理指定视频：

```powershell
.\repair-video-transcodes.ps1 -VideoId 2104853417371922433 -Apply
```

若 `mysql.exe` 不在系统 PATH，传入完整路径：

```powershell
.\repair-video-transcodes.ps1 -MysqlExe 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe' -Apply
```

脚本不会覆盖生产配置、重启 Java 或修改 Nginx；数据库密码只通过当前进程的 `MYSQL_PWD` 临时传递，并在命令完成后恢复。

前台启动器 `api/start-yingliu-api.bat` 会在健康检查通过后自动以 `-Apply` 模式运行转码修复，并处理当时的整个推荐流。Windows 服务直接托管 Java，不会执行这个前台启动器的自动修复逻辑；需要修复时可在发布目录手动运行 `auto-repair-video-transcodes.ps1`。若要临时改用其他公网地址，可在启动前设置 `YINGLIU_PUBLIC_BASE_URL`；若 `mysql.exe` 不在 PATH 或 MySQL 8.0/8.4 的默认目录，可设置 `YINGLIU_MYSQL_EXE` 为其完整路径。

## 5. 回滚

1. 停止当前 API 窗口或服务。
2. 把 Nginx 配置中的部署目录切回上一版本并执行 `nginx.exe -t`。
3. 启动上一版本 API，再执行 `nginx.exe -s reload`。
4. 运行 `verify-deployment.ps1` 确认三个入口恢复。

Flyway 数据库迁移默认启用。若新版本已执行不可逆迁移，不能只回滚 JAR；应在上线前确认迁移向后兼容并完成数据库备份。
