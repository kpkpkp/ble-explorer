#pragma once

#include <Arduino.h>

// Embedded HTML dashboard — served from PROGMEM as raw text/html
static const char HTML_DASHBOARD[] PROGMEM = R"rawliteral(<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>ELK Bridge</title>
<style>
*{box-sizing:border-box;margin:0;padding:0}
body{font-family:-apple-system,system-ui,sans-serif;background:#1a1a2e;color:#e0e0e0;padding:16px;max-width:480px;margin:0 auto}
h1{font-size:1.3em;color:#fff;margin-bottom:12px;text-align:center}
h2{font-size:1em;color:#aaa;margin:16px 0 8px;border-bottom:1px solid #333;padding-bottom:4px}
.card{background:#16213e;border-radius:8px;padding:12px;margin-bottom:12px}
.status-row{display:flex;justify-content:space-between;align-items:center;padding:4px 0;font-size:0.85em}
.status-dot{width:8px;height:8px;border-radius:50%;display:inline-block;margin-right:6px}
.dot-ok{background:#4caf50}.dot-warn{background:#ff9800}.dot-off{background:#666}
.color-preview{width:100%;height:48px;border-radius:6px;margin:8px 0;border:1px solid #333}
label{display:block;font-size:0.8em;color:#999;margin-top:8px}
input[type=range]{width:100%;margin:4px 0;accent-color:#7c4dff}
.val{float:right;font-size:0.8em;color:#7c4dff}
.btn-row{display:flex;gap:8px;margin-top:8px}
button{flex:1;padding:10px;border:none;border-radius:6px;font-size:0.9em;cursor:pointer;font-weight:600;transition:opacity 0.2s}
button:active{opacity:0.7}
.btn-on{background:#4caf50;color:#fff}
.btn-off{background:#f44336;color:#fff}
.btn-scan{background:#2196f3;color:#fff}
.btn-stop{background:#ff9800;color:#fff}
.device{display:flex;justify-content:space-between;align-items:center;padding:6px 0;border-bottom:1px solid #222;font-size:0.85em}
.device button{flex:none;padding:6px 12px;font-size:0.8em}
.btn-connect{background:#4caf50;color:#fff}
.btn-disconnect{background:#f44336;color:#fff}
#log{font-family:monospace;font-size:0.75em;color:#666;margin-top:8px;max-height:80px;overflow-y:auto}
select{width:100%;padding:8px;background:#0f3460;color:#e0e0e0;border:1px solid #333;border-radius:6px;font-size:0.85em;margin-top:4px}
</style>
</head>
<body>
<h1>ELK Bridge</h1>

<div class="card" id="statusCard">
<div class="status-row"><span><span class="status-dot dot-off" id="wifiDot"></span>WiFi: <span id="wifiStatus">--</span></span><span id="wifiIP"></span></div>
<div class="status-row"><span><span class="status-dot dot-off" id="bleDot"></span>BLE: <span id="bleStatus">--</span></span><span id="bleCount"></span></div>
<div class="status-row"><span>Playback: <span id="playback">idle</span></span><span id="fwVersion"></span></div>
</div>

<div class="card">
<h2>Target</h2>
<select id="target"><option value="all">All strips</option></select>
</div>

<div class="card">
<h2>Color</h2>
<div class="color-preview" id="colorPreview"></div>
<label>R <span class="val" id="rVal">128</span></label>
<input type="range" id="rSlider" min="0" max="255" value="128">
<label>G <span class="val" id="gVal">0</span></label>
<input type="range" id="gSlider" min="0" max="255" value="0">
<label>B <span class="val" id="bVal">255</span></label>
<input type="range" id="bSlider" min="0" max="255" value="255">
</div>

<div class="card">
<h2>Brightness</h2>
<label>Level <span class="val" id="brVal">100</span>%</label>
<input type="range" id="brSlider" min="0" max="100" value="100">
</div>

<div class="card">
<h2>Power</h2>
<div class="btn-row">
<button class="btn-on" onclick="sendPower(true)">ON</button>
<button class="btn-off" onclick="sendPower(false)">OFF</button>
<button class="btn-stop" onclick="sendStop()">STOP</button>
</div>
</div>

<div class="card">
<h2>Devices</h2>
<div id="deviceList"></div>
<div class="btn-row" style="margin-top:8px">
<button class="btn-scan" onclick="doScan()">Scan for ELK-BLEDOM</button>
</div>
<div id="scanResults" style="margin-top:8px"></div>
</div>

<div id="log"></div>

<script>
const API='';
let sendTimer=null;

function log(m){const d=document.getElementById('log');d.textContent=m+'\n'+d.textContent;d.textContent=d.textContent.slice(0,500)}

function post(path,body){
 return fetch(API+path,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(body)})
  .then(r=>r.json()).then(j=>{if(!j.ok)log('ERR: '+j.error);return j}).catch(e=>log('ERR: '+e))
}

function get(path){
 return fetch(API+path).then(r=>r.json()).catch(e=>log('ERR: '+e))
}

function getTarget(){return document.getElementById('target').value}

function updatePreview(){
 const r=document.getElementById('rSlider').value;
 const g=document.getElementById('gSlider').value;
 const b=document.getElementById('bSlider').value;
 document.getElementById('colorPreview').style.background='rgb('+r+','+g+','+b+')';
 document.getElementById('rVal').textContent=r;
 document.getElementById('gVal').textContent=g;
 document.getElementById('bVal').textContent=b;
}

function sendColorDebounced(){
 clearTimeout(sendTimer);
 sendTimer=setTimeout(()=>{
  const r=+document.getElementById('rSlider').value;
  const g=+document.getElementById('gSlider').value;
  const b=+document.getElementById('bSlider').value;
  post('/api/color',{r,g,b,target:getTarget()});
 },150);
}

document.getElementById('rSlider').addEventListener('input',()=>{updatePreview();sendColorDebounced()});
document.getElementById('gSlider').addEventListener('input',()=>{updatePreview();sendColorDebounced()});
document.getElementById('bSlider').addEventListener('input',()=>{updatePreview();sendColorDebounced()});

let brTimer=null;
document.getElementById('brSlider').addEventListener('input',function(){
 document.getElementById('brVal').textContent=this.value;
 clearTimeout(brTimer);
 brTimer=setTimeout(()=>{post('/api/brightness',{level:+this.value,target:getTarget()})},150);
});

function sendPower(on){post('/api/power',{on,target:getTarget()})}
function sendStop(){post('/api/stop',{target:getTarget()})}

function doScan(){
 document.getElementById('scanResults').innerHTML='<div style="color:#999;font-size:0.85em">Scanning...</div>';
 get('/api/scan').then(j=>{
  if(!j||!j.data)return;
  const el=document.getElementById('scanResults');
  if(j.data.devices.length===0){el.innerHTML='<div style="color:#999;font-size:0.85em">No devices found</div>';return}
  el.innerHTML=j.data.devices.map(d=>
   '<div class="device"><span>'+d.name+' ('+d.rssi+'dBm)</span>'+
   '<button class="btn-connect" onclick="doConnect(\''+d.address+'\')">Connect</button></div>'
  ).join('');
 });
}

function doConnect(addr){post('/api/connect',{address:addr}).then(()=>refreshStatus())}
function doDisconnect(addr){post('/api/disconnect',{address:addr}).then(()=>refreshStatus())}

function refreshStatus(){
 get('/status').then(j=>{
  if(!j||!j.data)return;
  const d=j.data;
  const wDot=document.getElementById('wifiDot');
  const bDot=document.getElementById('bleDot');
  document.getElementById('wifiStatus').textContent=d.wifi.state;
  document.getElementById('wifiIP').textContent=d.wifi.ip||'';
  wDot.className='status-dot '+(d.wifi.state==='CONNECTED'?'dot-ok':d.wifi.state==='AP_MODE'?'dot-warn':'dot-off');
  document.getElementById('bleCount').textContent=d.ble.connected+'/'+d.ble.total+' strips';
  bDot.className='status-dot '+(d.ble.connected>0?'dot-ok':'dot-off');
  document.getElementById('bleStatus').textContent=d.ble.connected>0?'Connected':'Disconnected';
  document.getElementById('playback').textContent=d.pattern.state;
  document.getElementById('fwVersion').textContent='v'+d.firmware;

  // Update target dropdown
  const sel=document.getElementById('target');
  const cur=sel.value;
  sel.innerHTML='<option value="all">All strips</option>';
  if(d.ble.strips){
   d.ble.strips.forEach((s,i)=>{
    sel.innerHTML+='<option value="strip'+(i+1)+'">Strip '+(i+1)+' ('+s.address+')</option>';
   });
  }
  sel.value=cur;

  // Update device list
  const dl=document.getElementById('deviceList');
  if(d.ble.strips&&d.ble.strips.length>0){
   dl.innerHTML=d.ble.strips.map((s,i)=>
    '<div class="device"><span>Strip '+(i+1)+': '+s.address+' ['+s.state+']</span>'+
    '<button class="btn-disconnect" onclick="doDisconnect(\''+s.address+'\')">Disconnect</button></div>'
   ).join('');
  }else{
   dl.innerHTML='<div style="color:#666;font-size:0.85em">No strips connected</div>';
  }
 });
}

updatePreview();
refreshStatus();
setInterval(refreshStatus,5000);
</script>
</body>
</html>)rawliteral";
