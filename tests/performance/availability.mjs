import {writeFileSync,mkdirSync} from 'node:fs';
import {performance} from 'node:perf_hooks';
import {setTimeout as sleep} from 'node:timers/promises';
import {execFile} from 'node:child_process';
import {promisify} from 'node:util';
import {randomUUID} from 'node:crypto';
const exec=promisify(execFile),base=new URL(process.env.BENCH_URL??'http://127.0.0.1:18090');
if(!['localhost','127.0.0.1','[::1]'].includes(base.hostname))throw Error('Loopback only');
const seconds=Number(process.env.SECONDS??30),action=process.env.ACTION??'normal';
if(!['normal','restart-api','restart-worker','rollout','scale'].includes(action)||seconds<15||seconds>120)throw Error('Invalid scenario');
const samples=[],events=[],start=performance.now();let triggered=false,actionPromise;
async function disrupt(){
  if(action==='normal')return;
  if(!process.env.KUBECTL)throw Error('KUBECTL required');
  const args=['--context','kind-ledgerone-audit','-n','ledgerone-local'];
  const cmd=action==='restart-api'?['delete','pod','-l','app=ledgerone-api','--wait=false']:
    action==='restart-worker'?['delete','pod','-l','app=ledgerone-order-worker','--wait=false']:
    action==='rollout'?['rollout','restart','deployment/ledgerone-api']:
    ['scale','deployment/ledgerone-api','--replicas=3'];
  try{const r=await exec(process.env.KUBECTL,[...args,...cmd],{timeout:15000});events.push({atMs:performance.now()-start,command:cmd,output:r.stdout});}
  catch(e){events.push({atMs:performance.now()-start,error:String(e)});}
}
while(performance.now()-start<seconds*1000){
  const at=performance.now()-start;
  if(!triggered&&at>=5000){triggered=true;actionPromise=disrupt();}
  try{const r=await fetch(new URL('/api/system/status',base),{signal:AbortSignal.timeout(800)});samples.push({atMs:at,status:r.status,latencyMs:performance.now()-start-at});}
  catch(e){samples.push({atMs:at,status:0,latencyMs:performance.now()-start-at});}
  await sleep(100);
}
await actionPromise;
let failedMs=0,longest=0,current=0;
for(let i=0;i<samples.length;i++){
  const width=(samples[i+1]?.atMs??seconds*1000)-samples[i].atMs;
  if(samples[i].status!==200){failedMs+=width;current+=width;longest=Math.max(longest,current);}else current=0;
}
const good=samples.filter(s=>s.status===200).length;
const result={at:new Date().toISOString(),action,durationSeconds:seconds,endpoint:'/api/system/status',attempts:samples.length,successful:good,failed:samples.length-good,requestSuccessPercent:100*good/samples.length,
  sampledUnavailableMs:failedMs,longestObservedOutageMs:longest,estimatedSampledTimeAvailabilityPercent:100*(1-failedMs/(seconds*1000)),
  limitations:['A public status read is not a successful trade.','Polling gives an approximate outage duration, not a continuous-time measurement.','A short local test cannot substantiate 99.99% long-term uptime.'],events,samples};
mkdirSync('tests/performance/results',{recursive:true});
writeFileSync(`tests/performance/results/availability-${action}-${randomUUID()}.json`,JSON.stringify(result,null,2)+'\n');
console.log(JSON.stringify({...result,samples:`${samples.length} observations`},null,2));
