
const TEAMS = ["ARI","ATL","BAL","BUF","CAR","CHI","CIN","CLE","DAL","DEN","DET","GB","HOU","IND","JAX","KC","LAC","LA","LV","MIA","MIN","NE","NO","NYG","NYJ","PHI","PIT","SEA","SF","TB","TEN","WAS"];
const VERSION = "0.1";
const KEY = "nfl_totals_lab_v01";

const $ = id => document.getElementById(id);
const clamp=(x,a,b)=>Math.max(a,Math.min(b,x));
const mean=a=>a.reduce((s,x)=>s+x,0)/Math.max(1,a.length);
const quantile=(arr,q)=>{ const a=[...arr].sort((x,y)=>x-y), i=(a.length-1)*q, lo=Math.floor(i), hi=Math.ceil(i); return a[lo]+(a[hi]-a[lo])*(i-lo); };
const pct=x=>(100*x).toFixed(1)+"%";
const num=(id,d=0)=>Number($(id).value)||d;

function initTeams(){
  for(const id of ["awayTeam","homeTeam"]){
    $(id).innerHTML = TEAMS.map(t=>`<option>${t}</option>`).join("");
  }
  $("awayTeam").value="NE"; $("homeTeam").value="SEA";
}

function loadVault(){
  try{
    return JSON.parse(localStorage.getItem(KEY)) || {version:VERSION, ranking:[], census:[]};
  }catch(e){ return {version:VERSION, ranking:[], census:[]}; }
}
let vault=loadVault(), lastResult=null;

function persist(){
  localStorage.setItem(KEY,JSON.stringify(vault));
  $("saveStatus").textContent="Guardado";
  setTimeout(()=>$("saveStatus").textContent="Vault listo",900);
  renderAll();
}

function randn(){
  let u=0,v=0; while(!u)u=Math.random(); while(!v)v=Math.random();
  return Math.sqrt(-2*Math.log(u))*Math.cos(2*Math.PI*v);
}
function gammaSample(shape, scale=1){
  if(shape<1) return gammaSample(shape+1,scale)*Math.pow(Math.random(),1/shape);
  const d=shape-1/3, c=1/Math.sqrt(9*d);
  while(true){
    let x=randn(), v=1+c*x; if(v<=0)continue; v=v*v*v;
    let u=Math.random();
    if(u<1-0.0331*x*x*x*x) return d*v*scale;
    if(Math.log(u)<0.5*x*x+d*(1-v+Math.log(v))) return d*v*scale;
  }
}
function poisson(lambda){
  if(lambda<30){
    const L=Math.exp(-lambda); let p=1,k=0;
    do{k++;p*=Math.random()}while(p>L);
    return k-1;
  }
  return Math.max(0,Math.round(lambda+Math.sqrt(lambda)*randn()));
}
function negBin(meanPoints, dispersion){
  // Gamma-Poisson mixture. Lower dispersion => more variance.
  const shape=Math.max(.25,dispersion);
  const lambda=gammaSample(shape,meanPoints/shape);
  return poisson(lambda);
}
function driveScore(td,fg,safety){
  const r=Math.random()*100;
  if(r<td) return Math.random()<0.94 ? 7 : 6;
  if(r<td+fg) return 3;
  if(r<td+fg+safety) return 2;
  return 0;
}
function drawDrives(mu){
  return clamp(Math.round(mu + randn()*1.15), 7, 16);
}
function simulateDriveTeam(params){
  const n=drawDrives(params.drives);
  let s=0;
  for(let i=0;i<n;i++) s+=driveScore(params.td,params.fg,params.safety);
  return s;
}
function bayesPPD(ppd){
  const league=2.05, priorWeight=3.0, sampleWeight=7.0;
  return (league*priorWeight+ppd*sampleWeight)/(priorWeight+sampleWeight);
}
function params(){
  return {
    away:{drives:num("awayDrives",10.7),ppd:num("awayPPD",2.05),disp:num("awayDisp",2.2),
          td:num("awayTD",21),fg:num("awayFG",15),safety:num("awaySafety",.3)},
    home:{drives:num("homeDrives",10.9),ppd:num("homePPD",2.25),disp:num("homeDisp",2.2),
          td:num("homeTD",23),fg:num("homeFG",15.5),safety:num("homeSafety",.3)}
  }
}
function fastNormalSamples(n, mu, sd){
  const out=new Array(n);
  for(let i=0;i<n;i++) out[i]=Math.max(0,Math.round(mu+sd*randn()));
  return out;
}
function engineMarkov(n,p){
  const out=new Array(n);
  for(let i=0;i<n;i++) out[i]=simulateDriveTeam(p.away)+simulateDriveTeam(p.home);
  return out;
}
function engineNegBin(n,p){
  const out=new Array(n), ma=p.away.drives*p.away.ppd, mh=p.home.drives*p.home.ppd;
  for(let i=0;i<n;i++) out[i]=negBin(ma,p.away.disp)+negBin(mh,p.home.disp);
  return out;
}
function engineDriveMC(n,p){
  // PPD-informed drive MC: adjust scoring event probability to reconcile with entered PPD.
  const tune=t=>{
    const raw=t.td*.07+t.fg*.03+t.safety*.02;
    const ratio=raw>0 ? t.ppd/raw : 1;
    return {...t,td:clamp(t.td*ratio,5,45),fg:clamp(t.fg*Math.sqrt(ratio),5,35)}
  };
  const q={away:tune(p.away),home:tune(p.home)}, out=new Array(n);
  for(let i=0;i<n;i++) out[i]=simulateDriveTeam(q.away)+simulateDriveTeam(q.home);
  return out;
}
function engineBayes(n,p){
  const ma=p.away.drives*bayesPPD(p.away.ppd), mh=p.home.drives*bayesPPD(p.home.ppd);
  return fastNormalSamples(n,ma+mh,12.4);
}
function enginePoisson(n,p){
  const mu=p.away.drives*p.away.ppd+p.home.drives*p.home.ppd;
  const out=new Array(n); for(let i=0;i<n;i++)out[i]=poisson(mu); return out;
}
function summarize(samples,line){
  const over=samples.filter(x=>x>line).length/samples.length;
  const under=samples.filter(x=>x<line).length/samples.length;
  return {mean:mean(samples),over,under,q10:quantile(samples,.10),q50:quantile(samples,.50),q90:quantile(samples,.90)};
}
async function run(){
  $("runBtn").disabled=true; $("runBtn").textContent="Simulando…";
  await new Promise(r=>setTimeout(r,25));
  const n=num("simCount",200000), p=params(), line=num("marketLine",47.5);
  // Split simulations across engines so mobile remains responsive enough.
  const per=Math.max(10000,Math.floor(n/5));
  const engines = {
    "Markov Drive": summarize(engineMarkov(per,p),line),
    "NegBin": summarize(engineNegBin(per,p),line),
    "Drive MC": summarize(engineDriveMC(per,p),line),
    "Bayesian": summarize(engineBayes(per,p),line),
    "Shadow Poisson": summarize(enginePoisson(per,p),line)
  };
  const weights={"Markov Drive":.30,"NegBin":.25,"Drive MC":.20,"Bayesian":.15,"Shadow Poisson":.10};
  let proj=0,po=0,pu=0;
  for(const [k,v] of Object.entries(engines)){proj+=v.mean*weights[k];po+=v.over*weights[k];pu+=v.under*weights[k];}
  const best = po>=pu ? "OVER" : "UNDER", prob=Math.max(po,pu), edge=proj-line;
  const cls = prob>=.66?"JUGABLE ★":prob>=.60?"LEAN": "PASS";
  const qs = {
    q10:Object.entries(engines).reduce((s,[k,v])=>s+v.q10*weights[k],0),
    q50:Object.entries(engines).reduce((s,[k,v])=>s+v.q50*weights[k],0),
    q90:Object.entries(engines).reduce((s,[k,v])=>s+v.q90*weights[k],0)
  };
  lastResult={
    id:Date.now(), ts:new Date().toISOString(), version:VERSION,
    away:$("awayTeam").value, home:$("homeTeam").value,line,
    params:p, engines, weights, projection:proj,pOver:po,pUnder:pu,best,prob,edge,classification:cls,quantiles:qs
  };
  renderResult(lastResult);
  $("runBtn").disabled=false; $("runBtn").textContent="Ejecutar ensemble";
}
function renderResult(r){
  $("result").classList.remove("hidden");
  $("bestPick").textContent=`${r.best} ${r.line}`;
  $("bestProb").textContent=pct(r.prob);
  $("classification").textContent=r.classification;
  $("classification").className="badge "+(r.classification.includes("JUGABLE")?"positive":r.classification==="LEAN"?"warning":"negative");
  $("projTotal").textContent=r.projection.toFixed(1);
  $("pOver").textContent=pct(r.pOver); $("pUnder").textContent=pct(r.pUnder);
  $("edge").textContent=(r.edge>=0?"+":"")+r.edge.toFixed(1);
  $("engines").innerHTML=Object.entries(r.engines).map(([k,v])=>{
    const pick=v.over>=v.under?"O":"U", pr=Math.max(v.over,v.under);
    return `<div class="engine"><span>${k}</span><strong>${pick} ${pct(pr)}</strong><span>μ ${v.mean.toFixed(1)}</span></div>`;
  }).join("");
  $("q10").textContent="P10 "+r.quantiles.q10.toFixed(1);
  $("median").textContent="P50 "+r.quantiles.q50.toFixed(1);
  $("q90").textContent="P90 "+r.quantiles.q90.toFixed(1);
}
function saveAnalysis(){
  if(!lastResult)return;
  const exists=vault.census.some(x=>x.id===lastResult.id);
  if(!exists)vault.census.push(lastResult);
  vault.ranking=vault.ranking.filter(x=>!(x.away===lastResult.away&&x.home===lastResult.home));
  vault.ranking.push(lastResult);
  vault.ranking.sort((a,b)=>b.prob-a.prob);
  persist();
}
function renderAll(){
  $("rankingList").innerHTML = vault.ranking.length ? vault.ranking.map(itemHTML).join("") : '<div class="hint">Sin picks guardados.</div>';
  $("censusList").innerHTML = vault.census.length ? [...vault.census].reverse().map(itemHTML).join("") : '<div class="hint">Censo vacío.</div>';
  $("censusCount").textContent=`${vault.census.length} análisis`;
  $("vaultPreview").value=JSON.stringify(vault,null,2);
}
function itemHTML(r){
  return `<div class="item">
    <div><div class="main">${r.away} @ ${r.home}</div><div class="sub">${new Date(r.ts).toLocaleString()} · μ ${r.projection.toFixed(1)}</div></div>
    <div><div class="main">${r.best} ${r.line}</div><div class="sub">${r.classification}</div></div>
    <div class="right">${pct(r.prob)}</div>
  </div>`;
}
function exportVault(){
  const blob=new Blob([JSON.stringify(vault,null,2)],{type:"application/json"});
  const a=document.createElement("a");a.href=URL.createObjectURL(blob);
  a.download=`NFL_DataVault_V${VERSION}_${new Date().toISOString().slice(0,10)}.json`;a.click();URL.revokeObjectURL(a.href);
}
function importVault(file){
  const reader=new FileReader();
  reader.onload=()=>{
    try{
      const x=JSON.parse(reader.result);
      if(!Array.isArray(x.ranking)||!Array.isArray(x.census)) throw new Error("estructura");
      vault=x; vault.version=VERSION; persist(); alert("Data Vault importado correctamente.");
    }catch(e){alert("JSON inválido o incompatible.");}
  };
  reader.readAsText(file);
}
document.querySelectorAll(".tab").forEach(b=>b.onclick=()=>{
  document.querySelectorAll(".tab").forEach(x=>x.classList.remove("active"));
  document.querySelectorAll(".tabpanel").forEach(x=>x.classList.remove("active"));
  b.classList.add("active"); $("tab-"+b.dataset.tab).classList.add("active");
});
$("runBtn").onclick=run; $("saveAnalysisBtn").onclick=saveAnalysis;
$("exportBtn").onclick=exportVault;
$("importFile").onchange=e=>{if(e.target.files[0])importVault(e.target.files[0]);};
$("clearRankingBtn").onclick=()=>{ if(confirm("¿Vaciar ranking? El censo se conserva.")){vault.ranking=[];persist();} };

initTeams(); renderAll();
