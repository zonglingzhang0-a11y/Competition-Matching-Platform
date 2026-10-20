# 公网演示：启动后端、前端，再开一条 Cloudflare 临时隧道，打印出一个谁都能打开的网址。
# 思路来自队友的 start-demo.bat，改成了不写死路径的通用版本。双击项目根目录的 public-demo.bat 运行。
#
# 需要：JDK 21、Node、MySQL 已经启动、backend/src/main/resources/application-local.yml 填好数据库密码，
#       以及 cloudflared（自己下载，见下面的提示）。数据库不用手工建，后端启动时会自动建表、自动升级。
#
# 参数：-Rebuild  前端改过代码后重新打包（默认有打包结果就直接用）

param([switch]$Rebuild)

$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $PSScriptRoot
$BackendPort = 8090
$FrontendPort = 4173
$TunnelLog = Join-Path $env:TEMP 'jingda-tunnel.log'
# 后端的推送连接默认只认同域；隧道的域名和后端看到的 localhost 不一样，要额外放行
$PushOrigin = 'https://*.trycloudflare.com'

function Say($text, $color = 'Gray') { Write-Host $text -ForegroundColor $color }
function Fail($text) {
    Say "[X] $text" 'Red'
    Read-Host '按回车键退出'
    exit 1
}
function PortOpen($port) {
    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $wait = $client.BeginConnect('127.0.0.1', $port, $null, $null)
        return $wait.AsyncWaitHandle.WaitOne(500) -and $client.Connected
    } catch {
        return $false
    } finally {
        $client.Close()
    }
}
function WaitPort($port, $seconds) {
    for ($i = 0; $i -lt $seconds; $i++) {
        if (PortOpen $port) { return $true }
        Start-Sleep -Seconds 1
    }
    return $false
}
function FindTool($envName, $exe, $extra) {
    $fromEnv = [Environment]::GetEnvironmentVariable($envName)
    if ($fromEnv -and (Test-Path $fromEnv)) { return $fromEnv }
    foreach ($p in $extra) { if ($p -and (Test-Path $p)) { return $p } }
    $cmd = Get-Command $exe -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    return $null
}

Say '============================================' 'Cyan'
Say '  竞搭 · 公网演示' 'Cyan'
Say '============================================' 'Cyan'

# ---- 0. 检查工具 ----
$java = FindTool 'JAVA_EXE' 'java.exe' @($(if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin\java.exe' }))
if (-not $java) { Fail '找不到 Java。装 JDK 21，或者设环境变量 JAVA_HOME。' }
$npm = FindTool 'NPM_CMD' 'npm.cmd' @()
if (-not $npm) { Fail '找不到 npm。装 Node.js（22.18 以上或 24）。' }
$cloudflared = FindTool 'CLOUDFLARED' 'cloudflared.exe' @((Join-Path $Root 'tools\cloudflared.exe'))
if (-not $cloudflared) {
    Fail ("找不到 cloudflared。到 https://github.com/cloudflare/cloudflared/releases 下载 cloudflared-windows-amd64.exe，`n" +
          "    改名为 cloudflared.exe 放到项目里的 tools 文件夹（已被 git 忽略），或者设环境变量 CLOUDFLARED 指向它。")
}
Say "[OK] Java、npm、cloudflared 都找到了" 'Green'

# ---- 1. MySQL ----
if (-not (PortOpen 3306)) {
    Fail 'MySQL（3306 端口）没在运行。在"服务"里启动 MySQL80，或者用管理员身份运行：net start MySQL80'
}
if (-not (Test-Path (Join-Path $Root 'backend\src\main\resources\application-local.yml'))) {
    Fail '还没有 backend\src\main\resources\application-local.yml：照着 application-local.example.yml 复制一份，填上 MySQL 密码。'
}
Say '[OK] MySQL 在运行' 'Green'

# ---- 2. 后端 ----
if (PortOpen $BackendPort) {
    Say "[OK] 后端已经在 $BackendPort 端口运行，直接用" 'Green'
    Say '     注意：如果它不是这个脚本启动的，隧道里的新消息推送会退回每分钟查一次（不影响别的功能）。' 'Yellow'
} else {
    Say '[..] 启动后端（第一次要下载依赖，可能要一两分钟；数据库会自动建表、升级）...'
    $arg = "-Dspring-boot.run.arguments=--app.push.allowed-origin-patterns[0]=$PushOrigin"
    Start-Process cmd -ArgumentList '/k', "title 竞搭 后端 && mvnw.cmd spring-boot:run `"$arg`"" -WorkingDirectory (Join-Path $Root 'backend')
    if (-not (WaitPort $BackendPort 180)) { Fail '后端 3 分钟内没起来，看"竞搭 后端"那个窗口里的报错（常见：端口被占、数据库密码不对）。' }
    Say '[OK] 后端好了' 'Green'
}

# ---- 3. 前端 ----
$frontend = Join-Path $Root 'frontend'
if (-not (Test-Path (Join-Path $frontend 'node_modules'))) {
    Say '[..] 第一次运行，安装前端依赖...'
    Push-Location $frontend; & $npm install; Pop-Location
}
if ($Rebuild -or -not (Test-Path (Join-Path $frontend 'dist\index.html'))) {
    Say '[..] 打包前端，十几秒...'
    Push-Location $frontend; & $npm run build-only; Pop-Location
    if (-not (Test-Path (Join-Path $frontend 'dist\index.html'))) { Fail '前端打包失败，往上翻看 npm 的报错。' }
} else {
    Say '[OK] 用已有的前端打包结果（改过前端代码就加 -Rebuild 重新打包）' 'Green'
}
if (PortOpen $FrontendPort) {
    Say "[OK] 前端已经在 $FrontendPort 端口运行" 'Green'
} else {
    # vite preview 沿用开发时的 /api 转发（连同推送的 WebSocket）
    Start-Process cmd -ArgumentList '/k', "title 竞搭 前端 && npm run preview -- --port $FrontendPort --strictPort" -WorkingDirectory $frontend
    if (-not (WaitPort $FrontendPort 60)) { Fail '前端没起来，看"竞搭 前端"那个窗口。' }
    Say '[OK] 前端好了' 'Green'
}

# ---- 4. 隧道 ----
Say '[..] 开公网隧道，十几秒...'
Remove-Item $TunnelLog -ErrorAction SilentlyContinue
# --http-host-header localhost：Vite 只认 localhost 这个主机名，不加的话会回"Blocked request"
$tunnelCmd = "title 竞搭 隧道 && `"$cloudflared`" tunnel --url http://localhost:$FrontendPort --http-host-header localhost --no-autoupdate > `"$TunnelLog`" 2>&1"
Start-Process cmd -ArgumentList '/c', $tunnelCmd -WindowStyle Minimized
$url = $null
for ($i = 0; $i -lt 90 -and -not $url; $i++) {
    Start-Sleep -Seconds 1
    $log = if (Test-Path $TunnelLog) { Get-Content $TunnelLog -Raw -ErrorAction SilentlyContinue } else { $null }
    if ($log) {
        $m = [regex]::Match($log, 'https://[a-z0-9-]+\.trycloudflare\.com')
        if ($m.Success) { $url = $m.Value }
    }
}
if (-not $url) { Fail "隧道没开起来，日志在 $TunnelLog，用记事本打开找 ERR 开头的行。" }

Say ''
Say '============================================' 'Cyan'
Say '  演示网址（复制发给别人）' 'Cyan'
Say '============================================' 'Cyan'
Say ''
Say "  $url" 'Green'
Say ''
Say '  对方只要有浏览器就行。演示账号和统一密码写在 sql\demo.sql 开头的注释里'
Say '  （还没导入演示数据的，先导入一次，写法见 README 的"演示数据"）。'
Say ''
Say '  注意：' 'Yellow'
Say '   1. 这个窗口和"竞搭 后端 / 前端 / 隧道"三个窗口都别关，电脑别休眠；关了大家都打不开。' 'Yellow'
Say '   2. 网址是临时的，每次运行都不一样。' 'Yellow'
Say '   3. 拿到网址的人都能登录演示账号、改数据，只发给信得过的人，别发到公开的群里。' 'Yellow'
Say '   4. 现在是开发模式，验证码只打印在后端窗口里、不会发邮件，别人没法自己注册，请用演示账号。' 'Yellow'
Say ''
Read-Host '演示结束后关掉那几个窗口就行。按回车键关闭这个窗口'
