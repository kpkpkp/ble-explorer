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
body{font-family:-apple-system,system-ui,sans-serif;background:#1a1a2e;color:#e0e0e0;padding:12px;max-width:480px;margin:0 auto}
h1{font-size:1.2em;color:#fff;margin-bottom:10px;text-align:center}
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
.bon{background:#4caf50;color:#fff}
.bof{background:#f44336;color:#fff}
.bac{background:#e94560;color:#fff}
.bsc{background:#2196f3;color:#fff}
.bst{background:#ff9800;color:#fff}
.bsm{background:#0f3460;color:#e0e0e0;border:1px solid #333}
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
.sh .sn{width:18px}.sh .xc{width:22px}.sh .xh{flex:3;min-width:0}.sh .xn{flex:2;min-width:0}.sh .xt{width:28px}.sh .xa{width:68px;text-align:right}
.st{display:flex;gap:4px;align-items:center;padding:5px 4px;border-bottom:1px solid #1a1a2e;font-size:.8em}
.st.act{background:#1a2740}
.st input{padding:4px;background:#0a0e27;color:#e0e0e0;border:1px solid #333;border-radius:4px;font-family:monospace;font-size:.9em;width:100%;min-width:0}
.st .sc{width:22px;height:22px;border-radius:4px;border:1px solid #444;cursor:pointer;flex-shrink:0}
.st .hi{flex:3;min-width:0}.st .ni{flex:2;min-width:0}
.st .ni.dim{opacity:.3;pointer-events:none}
.st button{padding:3px 6px;font-size:.9em;min-width:0;line-height:1.2}
.sn{color:#555;width:18px;flex-shrink:0;text-align:center}
.st .xt{width:28px;flex-shrink:0}
.bp{background:#0a0e27;border-radius:6px;padding:8px;margin:6px 0;display:none;flex-wrap:wrap;gap:8px;align-items:center;font-size:.78em}
.bp input{width:46px;padding:3px;background:#16213e;color:#e0e0e0;border:1px solid #333;border-radius:3px;font-size:1em}
.bp label{margin:0;color:#888;display:inline-flex;align-items:center;gap:3px}
</style>
</head>
<body>
<h1>ELK Bridge</h1>

<div class="c">
<div class="sr"><span><span class="d dof" id="wD"></span>WiFi: <span id="wS">--</span></span><span id="wIP"></span></div>
<div class="sr"><span id="sC"></span><span id="fV"></span></div>
<div class="sr"><span>Uptime: <span id="ut">--</span></span><span id="hp"></span></div>
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
<div class="tg" id="tg">
<button class="act" onclick="tgt('all')">ALL</button>
<button onclick="tgt('strip1')">STRIP 1</button>
<button onclick="tgt('strip2')">STRIP 2</button>
</div>
<div class="br" style="margin-top:8px">
<button class="bon" style="flex:1" onclick="pw(true)">ON</button>
<button class="bof" style="flex:1" onclick="pw(false)">OFF</button>
</div>
</div>

<div class="c">
<h2>Patterns</h2>
<div class="pr">
<select id="pSel"><option value="">-- Select Preset --</option></select>
<button class="bac" onclick="playP()">&#9654;</button>
<button class="bst" onclick="stopP()">&#9632;</button>
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
"Pride Rainbow":['E40303','FF8C00','FFED00','008026','004DFF','750787']};

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

// Build preset dropdown
(function(){var s=$('pSel');Object.keys(P).forEach(function(n){var o=document.createElement('option');o.value=n;o.textContent=n;s.appendChild(o)})})();

var steps=[];

// Dropdown interaction gating + load preset into editor
$('pSel').addEventListener('focus',function(){S._dPr=1});
$('pSel').addEventListener('change',function(){
S._dPr=1;
var n=this.value;
if(n&&P[n]){steps=P[n].map(function(h){return{hex:h,br:S.br,dur:2.0,tr:'cut',fade:0.5}});renSt()}
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
var hdr='<div class="sh"><span class="sn">#</span><span class="xc"></span><span class="xh">Color</span><span class="xn">Brt%</span><span class="xn">Dur(s)</span><span class="xt">Tr</span><span class="xn">Fade(s)</span><span class="xa"></span></div>';
el.innerHTML=hdr+steps.map(function(s,i){
var act=S.play&&i===S.pStep?' act':'';
var rgb=h2r(s.hex);var bg='rgb('+rgb[0]+','+rgb[1]+','+rgb[2]+')';
var dm=s.tr==='cut'?' dim':'';
return '<div class="st'+act+'">'+'<span class="sn">'+(i+1)+'</span>'+
'<div class="sc" style="background:'+bg+'" onclick="stClr('+i+')"></div>'+
'<input class="hi" type="text" value="'+s.hex+'" maxlength="6" onchange="uSt('+i+',\'hex\',this.value.toUpperCase())">'+
'<input class="ni" type="number" value="'+s.br+'" min="0" max="100" onchange="uSt('+i+',\'br\',+this.value)">'+
'<input class="ni" type="number" value="'+s.dur+'" min="0.1" max="60" step="0.1" onchange="uSt('+i+',\'dur\',+this.value)">'+
'<button class="bsm xt" onclick="togTr('+i+')" title="Cut/Fade">'+(s.tr==='fade'?'F':'C')+'</button>'+
'<input class="ni'+dm+'" type="number" value="'+s.fade+'" min="0.1" max="10" step="0.1" onchange="uSt('+i+',\'fade\',+this.value)">'+
'<button class="bsm" style="padding:2px 4px" onclick="mvSt('+i+',-1)" title="Move up">\u25B2</button>'+
'<button class="bsm" style="padding:2px 4px" onclick="mvSt('+i+',1)" title="Move down">\u25BC</button>'+
'<button class="bof" style="padding:2px 5px" onclick="rmSt('+i+')" title="Remove">\u00D7</button>'+
'</div>'}).join('')}

function addSt(){steps.push({hex:r2h(S.r,S.g,S.b),br:S.br,dur:2.0,tr:'cut',fade:0.5});renSt()}
function rmSt(i){steps.splice(i,1);renSt()}
function mvSt(i,d){var j=i+d;if(j<0||j>=steps.length)return;var t=steps[i];steps[i]=steps[j];steps[j]=t;renSt()}
function uSt(i,f,v){steps[i][f]=v;if(f==='hex'){var el=$('sL').children[i];if(el){var sw=el.querySelector('.sc');if(sw){var rgb=h2r(v);sw.style.background='rgb('+rgb[0]+','+rgb[1]+','+rgb[2]+')'}}}}
function togTr(i){steps[i].tr=steps[i].tr==='cut'?'fade':'cut';renSt()}
function stClr(i){steps[i].hex=r2h(S.r,S.g,S.b);renSt()}

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

uPrev();

var ps=S.play?'Step '+(S.pStep+1)+'/'+S.pTot+(S.pn?' \u2014 '+S.pn:''):'Idle';
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
S.play=d.pattern.playing;
S.pn=d.pattern.presetName||'';
S.lp=d.pattern.loop;
S.pStep=d.pattern.currentStep;
S.pTot=d.pattern.totalSteps;

// Auto-populate editor from running preset on page load
if(S.play&&S.pn&&P[S.pn]&&!steps.length){
steps=P[S.pn].map(function(h){return{hex:h,br:S.br,dur:2.0,tr:'cut',fade:0.5}});
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

poll();
setInterval(poll,1000);
</script>
</body>
</html>)rawliteral";
