$ErrorActionPreference='Stop'
foreach($name in @('backend','frontend')){
 $f=Join-Path $PSScriptRoot "logs/$name.pid"
 if(Test-Path $f){
  $processId=[int](Get-Content $f)
  $p=Get-CimInstance Win32_Process -Filter "ProcessId=$processId" -ErrorAction SilentlyContinue
  $marker=if($name -eq 'backend'){'runtime/mouse-mes-1.0.0.jar'}else{'runtime/serve.mjs'}
  if($p -and $p.CommandLine.Contains($marker) -and $p.CreationDate -le (Get-Item $f).LastWriteTime.AddSeconds(3)){Stop-Process -Id $processId;Write-Host "已停止 $name"}
 }
}
