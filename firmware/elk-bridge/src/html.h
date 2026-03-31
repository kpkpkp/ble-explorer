#pragma once

#include <Arduino.h>

// Embedded HTML dashboard — served from PROGMEM as raw text/html
static const char HTML_DASHBOARD[] PROGMEM = R"rawliteral(<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>ESP32 String Lights</title>
<style>
*{box-sizing:border-box;margin:0;padding:0}
body{font-family:-apple-system,system-ui,sans-serif;background:#1a1a2e;color:#e0e0e0;padding:12px;max-width:480px;margin:0 auto}
h1{font-size:1.2em;color:#fff;margin:0}
.hdr{display:flex;justify-content:space-between;align-items:center;margin-bottom:10px}
.hdr a{color:#777;font-size:.78em;cursor:pointer;text-decoration:none}
.hdr a:hover{color:#e94560}
.c{background:#16213e;border-radius:8px;padding:10px;margin-bottom:10px}
.c h2{font-size:.82em;color:#777;margin-bottom:8px;text-transform:uppercase;letter-spacing:.5px}
.sr{display:flex;justify-content:space-between;align-items:center;padding:3px 0;font-size:.8em}
.d{width:8px;height:8px;border-radius:50%;display:inline-block;margin-right:4px;vertical-align:middle}
.dok{background:#4caf50}.dwn{background:#ff9800}.dof{background:#555}
.chip{display:inline-block;background:#0f3460;border-radius:12px;padding:2px 8px;font-size:.72em;margin:2px}
.chip .d{width:6px;height:6px;margin-right:3px}
label{display:block;font-size:.78em;color:#777;margin-top:6px}
input[type=range]{width:100%;margin:3px 0;accent-color:#e94560;height:18px}
.v{float:right;color:#e94560;font-size:.78em}
.br{display:flex;gap:6px;flex-wrap:wrap}
button{padding:8px 12px;border:none;border-radius:6px;font-size:.8em;cursor:pointer;font-weight:600;transition:opacity .15s}
button:active{opacity:.7}
button:disabled{opacity:.35;cursor:not-allowed}
.bon{background:#4caf50;color:#fff}
.bof{background:#f44336;color:#fff}
.bac{background:#e94560;color:#fff}
.bsc{background:#2196f3;color:#fff}
.bst{background:#ff9800;color:#fff}
.bsm{background:#0f3460;color:#e0e0e0;border:1px solid #333}
.tog{position:relative;display:inline-block;width:50px;height:26px;flex-shrink:0}
.tog input{opacity:0;width:0;height:0}
.tsl{position:absolute;cursor:pointer;inset:0;background:#444;border-radius:26px;transition:.25s}
.tsl:before{content:'';position:absolute;height:20px;width:20px;left:3px;bottom:3px;background:#fff;border-radius:50%;transition:.25s}
.tog input:checked+.tsl{background:#4caf50}
.tog input:checked+.tsl:before{transform:translateX(24px)}
.tog input:disabled+.tsl{opacity:.35;cursor:not-allowed}
.tg{display:flex;gap:0}
.tg button{flex:1;border-radius:0;background:#0f3460;color:#777;border:1px solid #333;padding:8px 4px;font-size:.78em}
.tg button:first-child{border-radius:6px 0 0 6px}
.tg button:last-child{border-radius:0 6px 6px 0}
.tg button.act{background:#e94560;color:#fff;border-color:#e94560}
select{width:100%;padding:7px;background:#0f3460;color:#e0e0e0;border:1px solid #333;border-radius:6px;font-size:.8em}
.sw{display:grid;grid-template-columns:repeat(8,1fr);gap:4px;margin-bottom:8px}
.sw div{aspect-ratio:1;border-radius:4px;cursor:pointer;border:2px solid transparent;transition:border-color .12s}
.sw div:hover{border-color:rgba(255,255,255,.5)}
.sw div.sel{border-color:#e94560}
.cp{height:36px;border-radius:6px;margin:6px 0;border:1px solid #333}
.hx{display:flex;gap:6px;align-items:center;margin-top:4px}
.hx input{flex:1;padding:6px;background:#0f3460;color:#e0e0e0;border:1px solid #333;border-radius:6px;font-size:.8em;font-family:monospace}
.dev{display:flex;justify-content:space-between;align-items:center;padding:5px 0;border-bottom:1px solid #222;font-size:.8em}
.dev button{flex:none;padding:4px 10px;font-size:.75em}
.coll{cursor:pointer;display:flex;justify-content:space-between;align-items:center}
.coll:after{content:'\25BC';font-size:.6em;color:#555;transition:transform .2s}
.coll.open:after{transform:rotate(180deg)}
.fold{max-height:0;overflow:hidden;transition:max-height .25s ease}
.fold.open{max-height:400px}
.pr{display:flex;gap:6px;align-items:center;margin-top:4px}
.pr select{flex:1}
.pr button{flex:none;min-width:44px;min-height:38px;font-size:1.1em}
.pi{font-size:.72em;color:#666;margin-top:4px}
.er{display:flex;gap:6px;align-items:center}
.er select{flex:1}
.er button{flex:none;min-width:44px;min-height:38px;font-size:1.1em}
.stl{max-height:340px;overflow-y:auto;margin:6px 0;background:#0f1530;border-radius:6px;padding:4px}
.sh{display:flex;gap:4px;align-items:center;padding:2px 4px;font-size:.65em;color:#555;text-transform:uppercase;letter-spacing:.3px}
.sh span{text-align:center;overflow:hidden}
.sh .sn{width:18px}.sh .xh{flex:3;min-width:0}.sh .xn{flex:2;min-width:0}.sh .xt{width:28px}.sh .xa{width:68px;text-align:right}
.st{display:flex;gap:4px;align-items:center;padding:5px 4px;border-bottom:1px solid #1a1a2e;font-size:.8em}
.st.act{background:#1a2740}
.st input{padding:4px;background:#0a0e27;color:#e0e0e0;border:1px solid #333;border-radius:4px;font-family:monospace;font-size:.9em;width:100%;min-width:0}
.st .cc{width:0;flex:3;min-width:0;height:28px;padding:1px;border:1px solid #444;border-radius:4px;cursor:pointer;background:#0a0e27}
.st .ni{flex:2;min-width:0}
.st .ni.dim{opacity:.3;pointer-events:none}
.st button{padding:3px 6px;font-size:.9em;min-width:0;line-height:1.2}
.sn{color:#555;width:18px;flex-shrink:0;text-align:center}
.st .xt{width:28px;flex-shrink:0}
.bp{background:#0a0e27;border-radius:6px;padding:8px;margin:6px 0;display:none;flex-wrap:wrap;gap:8px;align-items:center;font-size:.78em}
.bp input{width:46px;padding:3px;background:#16213e;color:#e0e0e0;border:1px solid #333;border-radius:3px;font-size:1em}
.bp label{margin:0;color:#888;display:inline-flex;align-items:center;gap:3px}
.sce{background:#0f1530;border-radius:6px;padding:6px;margin:4px 0;font-size:.78em}
.sce .dg{display:flex;gap:2px;margin-bottom:4px}
.sce .dg span{width:20px;height:20px;border-radius:3px;text-align:center;line-height:20px;font-size:.7em;cursor:pointer;background:#1a1a2e;color:#555;user-select:none}
.sce .dg span.on{background:#e94560;color:#fff}
.sce .tr{display:flex;gap:6px;align-items:center;flex-wrap:wrap}
.sce .tr input[type=time]{padding:3px;background:#0a0e27;color:#e0e0e0;border:1px solid #333;border-radius:4px;font-size:.9em;font-family:monospace}
.sce .tr select{padding:3px;background:#0a0e27;color:#e0e0e0;border:1px solid #333;border-radius:4px;font-size:.9em;max-width:110px}
.sce .tr .sw-tog{padding:2px 6px;font-size:.8em;min-width:28px}
.scs{font-size:.72em;color:#aaa;padding:2px 0}
#abtM{display:none;position:fixed;inset:0;background:rgba(0,0,0,.6);z-index:999;align-items:center;justify-content:center}
#abtM.open{display:flex}
#abtP{background:#16213e;border-radius:8px;padding:20px;max-width:440px;width:90%;max-height:85vh;overflow-y:auto;position:relative}
#abtX{position:absolute;top:10px;right:12px;background:none;border:none;color:#777;font-size:18px;cursor:pointer}
#abtX:hover{color:#e94560}
</style>
</head>
<body>
<div class="hdr"><h1>ESP32 String Lights</h1><a onclick="showAbt()">About</a></div>

<div class="c">
<div class="sr"><span><span class="d dof" id="wD"></span>WiFi: <span id="wS">--</span></span><span id="wIP"></span></div>
<div class="sr"><span id="sC"></span><span id="fV"></span></div>
<div class="sr"><span><span id="clk" style="font-family:monospace"></span> | Uptime: <span id="ut">--</span></span><span id="hp"></span></div>
</div>

<div class="c">
<h2 class="coll" id="dvH" onclick="togDv()">Devices</h2>
<div class="fold" id="dvF">
<div id="dL" style="margin-top:6px"></div>
<div class="br" style="margin-top:8px">
<button class="bsc" style="flex:1" id="scBtn" onclick="doScan()">Scan for Devices</button>
</div>
<div id="scR"></div>
</div>
</div>

<div class="c">
<h2 class="coll" id="scH" onclick="togSc()">Schedule <span id="scSum" style="font-weight:normal;font-size:.72em;color:#888"></span></h2>
<div class="fold" id="scF">
<div id="scTbl"></div>
<div class="br" style="margin-top:6px">
<button class="bsm" style="flex:1" onclick="addScE()">+ Add Rule</button>
</div>
<div style="font-size:.72em;margin-top:6px"><label style="color:#888">Timezone: </label><select id="tzSel" onchange="setTz(this.value)" style="font-size:1em;background:#222;color:#eee;border:1px solid #444;border-radius:4px;padding:2px 4px"><option value="EST5EDT,M3.2.0,M11.1.0">Eastern (ET)</option><option value="CST6CDT,M3.2.0,M11.1.0">Central (CT)</option><option value="MST7MDT,M3.2.0,M11.1.0" selected>Mountain (MT)</option><option value="PST8PDT,M3.2.0,M11.1.0">Pacific (PT)</option><option value="AST9ADT,M3.2.0,M11.1.0">Alaska (AKT)</option><option value="HST10">Hawaii (HT)</option><option value="UTC0">UTC</option></select></div><div id="scTime" style="font-size:.68em;color:#555;margin-top:4px"></div>
</div>
</div>

<div class="c">
<div style="display:flex;align-items:center;gap:8px">
<label class="tog" id="pwTog"><input type="checkbox" id="pwCb" onchange="pw(this.checked)"><span class="tsl"></span></label>
<div class="tg" id="tg" style="flex:1">
<button class="act" onclick="tgt('all')">ALL</button>
<button onclick="tgt('strip1')">STRIP 1</button>
<button onclick="tgt('strip2')">STRIP 2</button>
</div>
</div>
</div>

<div class="c">
<h2>Patterns</h2>
<div class="pr">
<select id="pSel"><option value="">-- Select Preset --</option></select>
<button class="bac" onclick="playP()">&#9654;</button>
<button class="bst" onclick="stopP()">&#9632;</button>
<button class="bsm" onclick="saveP()" title="Save preset">&#128190;</button>
<button class="bof" id="delBtn" onclick="delP()" title="Delete preset" style="display:none">&#128465;</button>
</div>
<div class="pr">
<label style="margin:0;cursor:pointer"><input type="checkbox" id="lpCb" checked> Loop</label>
<span class="pi" id="pSt"></span>
</div>
<div class="stl" id="sL"></div>
<div class="br" style="margin-top:6px">
<button class="bsm" style="flex:1" onclick="addSt()">+ Add Step</button>
<button class="bsm" style="flex:1" id="bkBtn" onclick="togBk()">Bulk Edit</button>
</div>
<div class="bp" id="bkP">
<label>Brt:<input type="number" id="bkBr" min="0" max="100" placeholder="--"></label>
<label>Dur:<input type="number" id="bkDu" min="0.1" max="60" step="0.1" placeholder="--"></label>
<label>Tr:<button class="bsm" id="bkTr" style="padding:2px 8px;min-width:32px" onclick="togBkTr()">--</button></label>
<label>Fade:<input type="number" id="bkFd" min="0.1" max="10" step="0.1" placeholder="--"></label>
<button class="bac" style="padding:4px 12px" onclick="apBk()">Apply</button>
</div>
<label>Brightness <span class="v" id="brV">100</span>%</label>
<input type="range" id="brS" min="0" max="100" value="100">
</div>

<div class="c">
<h2>Color</h2>
<div class="sw" id="sG"></div>
<div class="cp" id="cP"></div>
<div class="hx"><span style="color:#777">#</span><input type="text" id="hI" maxlength="6" placeholder="FF00FF"><button class="bsm" onclick="aHex()">Set</button></div>
<label>R <span class="v" id="rV">0</span></label>
<input type="range" id="rS" min="0" max="255" value="0">
<label>G <span class="v" id="gV">0</span></label>
<input type="range" id="gS" min="0" max="255" value="0">
<label>B <span class="v" id="bV">0</span></label>
<input type="range" id="bS" min="0" max="255" value="0">
</div>

<div class="c">
<h2>Effects</h2>
<div class="er">
<select id="fxS"></select>
<button class="bac" onclick="sendFx()">&#9654;</button>
</div>
<label>Speed <span class="v" id="spV">3</span></label>
<input type="range" id="spS" min="1" max="10" value="3">
</div>


<script>
var SW=['FF0000','FF7F00','FFFF00','7FFF00','00FF00','00FF7F','00FFFF','007FFF',
'0000FF','7F00FF','FF00FF','FF007F','FFFFFF','CCCCCC','888888','000000'];

var FX=[['Static',128],['Jump RGB',129],['Jump RGBYCMW',130],['Crossfade RGB',131],
['Crossfade RGBYCMW',132],['Breathe RGB',133],['Breathe RGBYCMW',134],['Rainbow Fade',135],
['Rainbow Pulse',136],['Rainbow Strobe',137],['Color Cycle',138],['RGB Chase',139],
['Rainbow Chase',140],['Red Pulse',141],['Green Pulse',142],['Blue Pulse',143],
['Yellow Pulse',144],['Cyan Pulse',145],['Magenta Pulse',146],['White Pulse',147],
['RG Chase',148],['RB Chase',149],['GB Chase',150],['RG Breathe',151],
['RB Breathe',152],['GB Breathe',153],['RGB Breathe Chase',154]];

var P={
"St. Patrick's Day":['165B33','F8B229','FFFFFF'],
"Valentine's Day":['B31942','FF69B4','FFFFFF'],
"American Flag":['B31942','FFFFFF','0A3161'],
"Utah State":['071D49','FFFFFF','AA0200','FFB81D'],
"South Jordan":['005E2C','1A5276','FFFFFF','B87333'],
"4th of July":['C41F40','FFFFFF','1E2D60'],
"Pioneer Day":['071D49','FFFFFF','AA0200','FFB81D','8B5E3C','F5E6C8'],
"Christmas":['C30F16','165B33','F8B229','FFFFFF'],
"Halloween":['FF9A00','F75F1C','000000','881EE4','85E21F'],
"Mardi Gras":['880085','FFD700','00A651'],
"Pride Rainbow":['E40303','FF8C00','FFED00','008026','004DFF','750787'],
"Oscar Statue":[{hex:'D5BA6D',br:100,dur:5.0,tr:'fade',fade:1.0},{hex:'B69F66',br:100,dur:5.0,tr:'fade',fade:1.0},{hex:'EBAB47',br:100,dur:5.0,tr:'fade',fade:1.0}]};

var S={tgt:'all',r:0,g:0,b:0,br:100,fx:135,spd:3,lp:true,play:false,
pn:'',strips:[],wifi:{},fw:'',up:0,hp:0,pStep:0,pTot:0,
_dR:0,_dG:0,_dB:0,_dBr:0,_dSp:0,_dPr:0,_dFx:0,_dLp:0};

var $=function(id){return document.getElementById(id)};

function post(p,b){return fetch(p,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(b)}).then(function(r){return r.json()}).catch(function(){})}
function get(p){return fetch(p).then(function(r){return r.json()}).catch(function(){})}

function h2r(h){return[parseInt(h.slice(0,2),16),parseInt(h.slice(2,4),16),parseInt(h.slice(4,6),16)]}
function r2h(r,g,b){return[r,g,b].map(function(v){return v.toString(16).padStart(2,'0')}).join('').toUpperCase()}
function fUp(s){var h=Math.floor(s/3600),m=Math.floor(s%3600/60);return h+'h '+m+'m'}

// Build swatch grid
(function(){var g=$('sG');SW.forEach(function(c){var d=document.createElement('div');d.style.background='#'+c;d.dataset.c=c;d.onclick=function(){swC(c)};g.appendChild(d)})})();

// Build effect dropdown
(function(){var s=$('fxS');FX.forEach(function(e){var o=document.createElement('option');o.value=e[1];o.textContent=e[0];s.appendChild(o)})})();

// Build preset dropdown (initial — rebuilt after user presets load)
(function(){var s=$('pSel');Object.keys(P).forEach(function(n){var o=document.createElement('option');o.value=n;o.textContent=n;s.appendChild(o)})})();

var steps=[];
var UP={};

function rebuildPresetDropdown(){
var s=$('pSel');var cur=s.value;
s.innerHTML='<option value="">-- Select Preset --</option>';
Object.keys(UP).forEach(function(n){var o=document.createElement('option');o.value=n;o.textContent='\u2605 '+n;o.dataset.user='1';s.appendChild(o)});
Object.keys(P).forEach(function(n){var o=document.createElement('option');o.value=n;o.textContent=n;s.appendChild(o)});
s.value=cur;
}

function loadUserPresets(){
get('/api/presets').then(function(j){
if(!j||!j.data)return;
UP={};
j.data.forEach(function(p){
UP[p.name]=p.steps.map(function(s){
return{hex:r2h(s.r,s.g,s.b),br:s.br,dur:s.dur/1000,tr:s.tr,fade:s.fade/1000}});
});
rebuildPresetDropdown();
});
}

function saveP(){
if(!steps.length){alert('No steps to save');return}
var def=$('pSel').value||S.pn||'';
var name=prompt('Preset name:',def);
if(!name||!name.trim())return;
name=name.trim();
var api=steps.map(function(s){var rgb=h2r(s.hex);return{r:rgb[0],g:rgb[1],b:rgb[2],br:s.br,dur:Math.round(s.dur*1000),tr:s.tr,fade:Math.round(s.fade*1000)}});
post('/api/presets',{name:name,steps:api}).then(function(j){
if(j&&j.ok){UP[name]=steps.slice();rebuildPresetDropdown();$('pSel').value=name;updDelBtn()}
else alert(j&&j.error||'Save failed');
});
}

function delP(){
var n=$('pSel').value;
if(!n||!UP[n])return;
if(!confirm('Delete "'+n+'"?'))return;
fetch('/api/presets',{method:'DELETE',headers:{'Content-Type':'application/json'},body:JSON.stringify({name:n})}).then(function(r){return r.json()}).then(function(j){
if(j&&j.ok){delete UP[n];rebuildPresetDropdown();$('pSel').value='';updDelBtn()}
});
}

function updDelBtn(){
var n=$('pSel').value;
$('delBtn').style.display=(n&&UP[n])?'inline-block':'none';
}

loadUserPresets();

// Dropdown interaction gating + load preset into editor
$('pSel').addEventListener('focus',function(){S._dPr=1});
$('pSel').addEventListener('change',function(){
S._dPr=1;
var n=this.value;
updDelBtn();
var src=UP[n]||null;
if(!src&&P[n])src=P[n].map(function(h){return typeof h==='string'?{hex:h,br:S.br,dur:2.0,tr:'cut',fade:0.5}:{hex:h.hex,br:h.br,dur:h.dur,tr:h.tr,fade:h.fade}});
if(src){steps=src.map(function(s){return{hex:s.hex,br:s.br,dur:s.dur,tr:s.tr,fade:s.fade}});renSt();if(S.play)playP()}
});
$('fxS').addEventListener('focus',function(){S._dFx=1});
$('fxS').addEventListener('change',function(){S._dFx=1});

function togDv(){$('dvH').classList.toggle('open');$('dvF').classList.toggle('open')}

function tgt(t){
S.tgt=t;
var btns=$('tg').children;
var idx=t==='all'?0:t==='strip1'?1:2;
for(var i=0;i<btns.length;i++)btns[i].classList.toggle('act',i===idx);
var st=rStrip();
if(st){S.r=st.r;S.g=st.g;S.b=st.b;S.br=st.brightness;syncUI()}
}

function rStrip(){
if(!S.strips.length)return null;
if(S.tgt==='strip2'&&S.strips.length>1)return S.strips[1];
return S.strips[0];
}

var _favCvs=null,_favEl=null;
function updFav(){
var st=rStrip();
var on=st&&st.power&&(S.r||S.g||S.b);
if(!_favCvs){_favCvs=document.createElement('canvas');_favCvs.width=16;_favCvs.height=16}
if(!_favEl){_favEl=document.createElement('link');_favEl.rel='icon';document.head.appendChild(_favEl)}
var ctx=_favCvs.getContext('2d');
ctx.clearRect(0,0,16,16);
if(on){ctx.fillStyle='rgb('+S.r+','+S.g+','+S.b+')';ctx.fillRect(0,0,16,16)}
_favEl.href=_favCvs.toDataURL('image/png');
}

function pw(on){post('/api/power',{on:on,target:S.tgt})}
function stopP(){post('/api/stop',{target:S.tgt})}

function swC(c){
var rgb=h2r(c);S.r=rgb[0];S.g=rgb[1];S.b=rgb[2];
syncUI();
post('/api/color',{r:S.r,g:S.g,b:S.b,target:S.tgt});
}

function aHex(){
var v=$('hI').value.replace('#','');
if(v.length!==6)return;
var rgb=h2r(v);S.r=rgb[0];S.g=rgb[1];S.b=rgb[2];
syncUI();
post('/api/color',{r:S.r,g:S.g,b:S.b,target:S.tgt});
}

$('hI').addEventListener('keydown',function(e){if(e.key==='Enter')aHex()});
$('lpCb').addEventListener('change',function(){S._dLp=1});

function sendFx(){
S._dFx=0;
post('/api/effect',{code:+$('fxS').value,speed:+$('spS').value,target:S.tgt});
}

function playP(){
if(!steps.length)return;
S._dPr=0;S._dLp=0;
var name=$('pSel').value||'Custom';
var loop=$('lpCb').checked;
var api=steps.map(function(s){var rgb=h2r(s.hex);return{r:rgb[0],g:rgb[1],b:rgb[2],brightness:s.br,duration:Math.round(s.dur*1000),transition:s.tr,fadeTime:Math.round(s.fade*1000)}});
post('/api/pattern',{steps:api,loop:loop,target:S.tgt,name:name});
}

// --- Step Editor ---
function renSt(){
var el=$('sL');
if(!steps.length){el.innerHTML='<div style="color:#555;font-size:.78em;padding:8px;text-align:center">No steps \u2014 select a preset or add steps</div>';return}
var hdr='<div class="sh"><span class="sn">#</span><span class="xh">Color</span><span class="xn">Brt%</span><span class="xn">Dur(s)</span><span class="xt">Tr</span><span class="xn">Fade(s)</span><span class="xa"></span></div>';
el.innerHTML=hdr+steps.map(function(s,i){
var act=S.play&&i===S.pStep?' act':'';
var dm=s.tr==='cut'?' dim':'';
return '<div class="st'+act+'">'+'<span class="sn">'+(i+1)+'</span>'+
'<input class="cc" type="color" value="#'+s.hex+'" oninput="uSt('+i+',\'hex\',this.value.slice(1).toUpperCase())">'+
'<input class="ni" type="number" value="'+s.br+'" min="0" max="100" onchange="uSt('+i+',\'br\',+this.value)">'+
'<input class="ni" type="number" value="'+s.dur+'" min="0.1" max="60" step="0.1" onchange="uSt('+i+',\'dur\',+this.value)">'+
'<button class="bsm xt" onclick="togTr('+i+')" title="Cut/Fade">'+(s.tr==='fade'?'F':'C')+'</button>'+
'<input class="ni'+dm+'" type="number" value="'+s.fade+'" min="0.1" max="10" step="0.1" onchange="uSt('+i+',\'fade\',+this.value)">'+
'<button class="bsm" style="padding:2px 4px" onclick="mvSt('+i+',-1)" title="Move up">\u25B2</button>'+
'<button class="bsm" style="padding:2px 4px" onclick="mvSt('+i+',1)" title="Move down">\u25BC</button>'+
'<button class="bof" style="padding:2px 5px" onclick="rmSt('+i+')" title="Remove">\u00D7</button>'+
'</div>'}).join('')}

function addSt(){steps.push({hex:r2h(S.r,S.g,S.b),br:S.br,dur:2.0,tr:'cut',fade:0.5});renSt();if(S.play)playP()}
function rmSt(i){steps.splice(i,1);renSt();if(S.play&&steps.length)playP();else if(S.play)stopP()}
function mvSt(i,d){var j=i+d;if(j<0||j>=steps.length)return;var t=steps[i];steps[i]=steps[j];steps[j]=t;renSt();if(S.play)playP()}
function uSt(i,f,v){steps[i][f]=v;if(S.play)playP()}
function togTr(i){steps[i].tr=steps[i].tr==='cut'?'fade':'cut';renSt();if(S.play)playP()}

// Bulk edit
function togBk(){var p=$('bkP');p.style.display=p.style.display==='none'?'flex':'none'}
var bkTrV=null;
function togBkTr(){if(bkTrV===null)bkTrV='cut';else if(bkTrV==='cut')bkTrV='fade';else bkTrV=null;$('bkTr').textContent=bkTrV||'--'}
function apBk(){var br=$('bkBr').value,du=$('bkDu').value,fd=$('bkFd').value;
steps.forEach(function(s){if(br!=='')s.br=+br;if(du!=='')s.dur=+du;if(bkTrV!==null)s.tr=bkTrV;if(fd!=='')s.fade=+fd});renSt()}

renSt();

// Slider setup with interaction gating
function sSlider(id,vId,dk,cb){
var sl=$(id);
sl.addEventListener('input',function(){S[dk]=1;$(vId).textContent=sl.value;cb(+sl.value)});
sl.addEventListener('change',function(){cb(+sl.value);setTimeout(function(){S[dk]=0},200)});
}

var cTm=null;
function sCD(){
clearTimeout(cTm);
cTm=setTimeout(function(){post('/api/color',{r:S.r,g:S.g,b:S.b,target:S.tgt})},150);
}

sSlider('rS','rV','_dR',function(v){S.r=v;uPrev();sCD()});
sSlider('gS','gV','_dG',function(v){S.g=v;uPrev();sCD()});
sSlider('bS','bV','_dB',function(v){S.b=v;uPrev();sCD()});

var bTm=null;
sSlider('brS','brV','_dBr',function(v){
S.br=v;clearTimeout(bTm);
bTm=setTimeout(function(){post('/api/brightness',{level:v,target:S.tgt})},150);
});

var sTm=null;
sSlider('spS','spV','_dSp',function(v){
S.spd=v;clearTimeout(sTm);
sTm=setTimeout(function(){post('/api/speed',{speed:v,target:S.tgt})},150);
});

function uPrev(){
$('cP').style.background='rgb('+S.r+','+S.g+','+S.b+')';
$('hI').value=r2h(S.r,S.g,S.b);
var hex=r2h(S.r,S.g,S.b);
var els=$('sG').children;
for(var i=0;i<els.length;i++)els[i].classList.toggle('sel',els[i].dataset.c===hex);
}

function syncUI(){
var w=S.wifi;
$('wD').className='d '+(w.state==='CONNECTED'?'dok':w.state==='AP_MODE'?'dwn':'dof');
$('wS').textContent=w.state||'--';
$('wIP').textContent=w.ip||'';
$('fV').textContent='v'+(S.fw||'?');
$('ut').textContent=fUp(S.up);
$('hp').textContent=S.hp?Math.round(S.hp/1024)+'KB':'';

var ch='';
S.strips.forEach(function(s,i){
var ok=s.state==='ready';
var nm=s.name||('Strip '+(i+1));
ch+='<span class="chip"><span class="d '+(ok?'dok':'dof')+'"></span>'+nm+' '+s.rssi+'dBm</span>';
});
$('sC').innerHTML=ch;

if(!S._dR){$('rS').value=S.r;$('rV').textContent=S.r}
if(!S._dG){$('gS').value=S.g;$('gV').textContent=S.g}
if(!S._dB){$('bS').value=S.b;$('bV').textContent=S.b}
if(!S._dBr){$('brS').value=S.br;$('brV').textContent=S.br}
if(!S._dSp){$('spS').value=S.spd;$('spV').textContent=S.spd}

var st0=rStrip();
var pon=st0?st0.power:false;
$('pwCb').checked=pon;

uPrev();

var ps=S.play?'Step '+(S.pStep+1)+'/'+S.pTot:'Idle';
$('pSt').textContent=ps;
if(S.pn&&!S._dPr)$('pSel').value=S.pn;
if(!S._dLp)$('lpCb').checked=S.lp;

var stEls=$('sL').querySelectorAll('.st');
for(var si=0;si<stEls.length;si++)stEls[si].classList.toggle('act',S.play&&si===S.pStep);

var st=rStrip();
if(st&&st.mode==='effect'&&!S._dSp&&!S._dFx){
$('fxS').value=st.effectCode;
}

var dl=$('dL');
if(S.strips.length){
dl.innerHTML=S.strips.map(function(s,i){
var nm=s.name||('Strip '+(i+1));
var pw=s.power?'':'<span style="color:#f44336"> OFF</span>';
return '<div class="dev"><span>'+nm+': '+s.address+' ['+s.state+']'+pw+'</span>'+
'<button class="bof" onclick="doDis(\''+s.address+'\')">&#10005;</button></div>';
}).join('');
}else{
dl.innerHTML='<div style="color:#555;font-size:.78em">No strips connected</div>';
}
updFav();
guardPw();
}

function poll(){
get('/status').then(function(j){
if(!j||!j.data)return;
var d=j.data;
S.wifi=d.wifi;
S.strips=d.ble.strips||[];
S.fw=d.firmware;
S.up=d.uptime;
S.hp=d.freeHeap;
if(d.tz){var s=$('tzSel');for(var i=0;i<s.options.length;i++){if(s.options[i].value===d.tz){s.selectedIndex=i;break}}}
if(d.time){$('scTime').textContent='Bridge: '+d.time;seedClk(d.time)}
S.play=d.pattern.playing;
S.pn=d.pattern.presetName||'';
S.lp=d.pattern.loop;
S.pStep=d.pattern.currentStep;
S.pTot=d.pattern.totalSteps;

// Auto-populate editor from running preset on page load
if(S.play&&S.pn&&P[S.pn]&&!steps.length){
steps=P[S.pn].map(function(h){return typeof h==='string'?{hex:h,br:S.br,dur:2.0,tr:'cut',fade:0.5}:{hex:h.hex,br:h.br,dur:h.dur,tr:h.tr,fade:h.fade}});
renSt();
}

var st=rStrip();
if(st){
if(!S._dR&&!S._dG&&!S._dB){S.r=st.r;S.g=st.g;S.b=st.b}
if(!S._dBr)S.br=st.brightness;
if(st.mode==='effect'){
S.fx=st.effectCode;
if(!S._dSp)S.spd=st.effectSpeed;
}
}
syncUI();
});
}

var scPoll=null;
function doScan(){
$('scR').innerHTML='<div style="color:#888;font-size:.78em;padding:6px">Scanning\u2026</div>';
$('scBtn').disabled=true;
get('/api/scan').then(pScan);
}
function pScan(j){
if(!j||!j.data)return;
if(j.data.scanning){
scPoll=setTimeout(function(){get('/api/scan').then(pScan)},1000);
return;
}
clearTimeout(scPoll);
$('scBtn').disabled=false;
var devs=j.data.devices||[];
if(!devs.length){
$('scR').innerHTML='<div style="color:#888;font-size:.78em;padding:6px">No devices found</div>';
return;
}
$('scR').innerHTML=devs.map(function(d){
return '<div class="dev"><span>'+d.name+' ('+d.rssi+'dBm)</span>'+
'<button class="bon" onclick="doCon(\''+d.address+'\')">Connect</button></div>';
}).join('');
}

function doCon(a){post('/api/connect',{address:a})}
function doDis(a){post('/api/disconnect',{address:a})}

// --- Schedule ---
var SC=[];
var scWday=-1;
var DN=['S','M','T','W','T','F','S'];

function togSc(){$('scH').classList.toggle('open');$('scF').classList.toggle('open')}
function setTz(tz){post('/api/config',{tz:tz}).then(function(){setTimeout(loadSched,2000)})}

function loadSched(){
get('/api/schedule').then(function(j){
if(!j||!j.data)return;
SC=j.data.entries||[];
scWday=j.data.wday>=0?j.data.wday:-1;
if(j.data.time)$('scTime').textContent='Bridge: '+j.data.time;
if(j.data.tz){var s=$('tzSel');for(var i=0;i<s.options.length;i++){if(s.options[i].value===j.data.tz){s.selectedIndex=i;break}}}
renSc();
renScSum();
guardPw();
});
}

function renScSum(){
if(scWday<0||!SC.length){$('scSum').textContent='';return}
var today=SC.filter(function(e){return e.enabled&&(e.days&(1<<scWday))});
if(!today.length){$('scSum').textContent='(no rules today)';return}
$('scSum').textContent=today.map(function(e){
return pad2(e.startHour)+':'+pad2(e.startMin)+'-'+pad2(e.endHour)+':'+pad2(e.endMin)+' '+(e.pattern==='*'?'Timer':e.pattern)
}).join(', ');
}

function pad2(n){return n<10?'0'+n:''+n}

function scPatOpts(){
var opts='<option value="*">* (Timer)</option>';
Object.keys(UP).forEach(function(n){opts+='<option value="'+n+'">\u2605 '+n+'</option>'});
Object.keys(P).forEach(function(n){opts+='<option value="'+n+'">'+n+'</option>'});
return opts;
}

function renSc(){
var el=$('scTbl');
if(!SC.length){el.innerHTML='<div style="color:#555;font-size:.78em;padding:6px;text-align:center">No rules</div>';return}
el.innerHTML=SC.map(function(e,i){
var dBtns=DN.map(function(d,di){return '<span class="'+(e.days&(1<<di)?'on':'')+'" onclick="togScDay('+i+','+di+')">'+d+'</span>'}).join('');
return '<div class="sce">'+
'<div class="dg">'+dBtns+'</div>'+
'<div class="tr">'+
'<input type="time" value="'+pad2(e.startHour)+':'+pad2(e.startMin)+'" onchange="uScTime('+i+',\'s\',this.value)">'+
'<span style="color:#555">\u2192</span>'+
'<input type="time" value="'+pad2(e.endHour)+':'+pad2(e.endMin)+'" onchange="uScTime('+i+',\'e\',this.value)">'+
'<select onchange="uScPat('+i+',this.value)">'+scPatOpts().replace('value="'+e.pattern+'"','value="'+e.pattern+'" selected')+'</select>'+
'<button class="bsm sw-tog '+(e.enabled?'bon':'')+'" onclick="togScEn('+i+')">'+(e.enabled?'ON':'OFF')+'</button>'+
'<button class="bof" style="padding:2px 5px" onclick="delScE('+i+')">\u00D7</button>'+
'</div></div>'
}).join('');
}

function addScE(){
var e={enabled:true,days:127,startHour:18,startMin:0,endHour:23,endMin:0,pattern:'*'};
post('/api/schedule',e).then(function(j){if(j&&j.ok){SC.push(e);renSc();renScSum()}});
}

function delScE(i){
fetch('/api/schedule',{method:'DELETE',headers:{'Content-Type':'application/json'},body:JSON.stringify({index:i})}).then(function(r){return r.json()}).then(function(j){
if(j&&j.ok){SC.splice(i,1);renSc();renScSum()}
});
}

function togScDay(i,d){
SC[i].days^=(1<<d);
saveScE(i,true);
}

function togScEn(i){
SC[i].enabled=!SC[i].enabled;
saveScE(i,true);
}

function uScTime(i,which,v){
var p=v.split(':');
if(which==='s'){SC[i].startHour=+p[0];SC[i].startMin=+p[1]}
else{SC[i].endHour=+p[0];SC[i].endMin=+p[1]}
saveScE(i,false);
guardPw();
}

function uScPat(i,v){
SC[i].pattern=v;
saveScE(i,false);
guardPw();
}

function saveScE(i,rerender){
var e={enabled:SC[i].enabled,days:SC[i].days,startHour:SC[i].startHour,startMin:SC[i].startMin,endHour:SC[i].endHour,endMin:SC[i].endMin,pattern:SC[i].pattern,index:i};
post('/api/schedule',e).then(function(){
if(rerender)renSc();renScSum();
// Resync clock from bridge to prevent drift during schedule editing
get('/api/schedule').then(function(j){
if(j&&j.data){
if(j.data.time)seedClk(j.data.time);
if(j.data.wday>=0)scWday=j.data.wday;
guardPw();
}
});
});
}

// --- Live clock (seeded from bridge, ticked locally) ---
var _clkBase=null,_clkAt=0;
function seedClk(ts){
var p=ts.split(/[- :]/);
_clkBase=new Date(+p[0],+p[1]-1,+p[2],+p[3],+p[4],+p[5]||0);
_clkAt=Date.now();
}
function tickClk(){
if(!_clkBase)return;
var d=new Date(_clkBase.getTime()+(Date.now()-_clkAt));
$('clk').textContent=pad2(d.getHours())+':'+pad2(d.getMinutes())+':'+pad2(d.getSeconds());
}
setInterval(tickClk,1000);

// --- Schedule-aware power button guard ---
function guardPw(){
var active=false;
if(_clkBase&&scWday>=0){
var d=new Date(_clkBase.getTime()+(Date.now()-_clkAt));
var nm=d.getHours()*60+d.getMinutes();
active=SC.some(function(e){
if(!e.enabled)return false;
if(!(e.days&(1<<scWday)))return false;
var s=e.startHour*60+e.startMin,en=e.endHour*60+e.endMin;
if(s<=en)return nm>=s&&nm<en;
return nm>=s||nm<en;
});
}
$('pwCb').disabled=active;
$('pwTog').title=active?'Managed by schedule':'';
}

loadSched();

// --- About modal ---
function showAbt(){$('abVer').textContent='v'+(S.fw||'?');$('abtM').classList.add('open')}
function closeAbt(){$('abtM').classList.remove('open')}

poll();
setInterval(poll,1000);
</script>

<div id="abtM" onclick="if(event.target===this)closeAbt()">
<div id="abtP">
<button id="abtX" onclick="closeAbt()">&times;</button>
<h2 style="font-size:.82em;color:#777;text-transform:uppercase;letter-spacing:.5px;margin-bottom:8px">ESP32 String Lights <span id="abVer" style="color:#e94560;text-transform:none;letter-spacing:0"></span></h2>
<p style="font-size:.78em;color:#aaa;margin-bottom:10px">ESP32-C3 WiFi-to-BLE bridge for ELK-BLEDOM LED string lights. Connect from any browser on your local network — no app required.</p>
<h2 style="font-size:.82em;color:#777;text-transform:uppercase;letter-spacing:.5px;margin-bottom:6px">Features</h2>
<ul style="font-size:.78em;color:#aaa;padding-left:14px;margin-bottom:10px;line-height:1.7">
<li>Schedule engine — time-based on/off and pattern playback</li>
<li>Pattern presets — built-in and custom with fade/cut transitions</li>
<li>Color calibration — hardware correction for accurate colors</li>
<li>Live dashboard — real-time status with 1s polling</li>
<li>OTA firmware updates — flash over WiFi</li>
</ul>
<h2 style="font-size:.82em;color:#777;text-transform:uppercase;letter-spacing:.5px;margin-bottom:6px">How It Works</h2>
<p style="font-size:.78em;color:#aaa;margin-bottom:12px">The ESP32-C3 joins your WiFi network and bridges HTTP commands into BLE 5.0 frames sent to up to 3 LED strips simultaneously. Patterns and schedules run autonomously on the bridge even when no browser is open.</p>
<a href="https://github.com/kpkpkp/ble-explorer/issues/new" target="_blank" style="display:inline-block;background:#e94560;color:#fff;font-size:.78em;font-weight:600;padding:6px 14px;border-radius:6px;text-decoration:none">Report a Bug</a>
</div>
</div>

</body>
</html>)rawliteral";
