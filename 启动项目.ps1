$ErrorActionPreference='Stop'
Set-Location $PSScriptRoot
if(Test-Path './本机配置.ps1'){. './本机配置.ps1'}
if(-not(Get-Command java -ErrorAction SilentlyContinue)){throw '请安装 JDK 17 并配置 JAVA_HOME/PATH'}
if(-not(Get-Command node -ErrorAction SilentlyContinue)){throw '请安装 Node.js 22 并配置 PATH'}
New-Item -ItemType Directory -Force './logs' | Out-Null
foreach($port in @(18082,18084)){
 $c=[Net.Sockets.TcpClient]::new()
 try{$c.Connect('127.0.0.1',$port);throw "端口 $port 已被占用，请先停止相应程序"}catch [Net.Sockets.SocketException]{}finally{$c.Dispose()}
}
$p=Start-Process (Get-Command java).Source -ArgumentList @('-jar','runtime/mouse-mes-1.0.0.jar','--spring.profiles.active=team','--spring.config.additional-location=file:runtime/application-team.yml') -WindowStyle Hidden -PassThru -RedirectStandardOutput './logs/backend.out.log' -RedirectStandardError './logs/backend.err.log'
$p.Id | Set-Content './logs/backend.pid'
$p=Start-Process (Get-Command node).Source -ArgumentList 'runtime/serve.mjs' -WindowStyle Hidden -PassThru -RedirectStandardOutput './logs/frontend.out.log' -RedirectStandardError './logs/frontend.err.log'
$p.Id | Set-Content './logs/frontend.pid'
Write-Host '已启动。浏览器打开 http://127.0.0.1:18084 ，后端首次启动约需20秒。'
Write-Host '若登录失败，请检查数据库初始化、数据库密码和 Redis；错误记录在 logs 文件夹。'
