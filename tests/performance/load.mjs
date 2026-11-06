import { performance } from 'node:perf_hooks';
import { randomUUID } from 'node:crypto';
import { setTimeout as sleep } from 'node:timers/promises';
import { writeFileSync, mkdirSync } from 'node:fs';
import { execFile } from 'node:child_process';
import { promisify } from 'node:util';

const base = new URL(process.env.BENCH_URL ?? 'http://127.0.0.1:18090');
if (!['localhost', '127.0.0.1', '[::1]'].includes(base.hostname)) throw Error('Only an isolated loopback endpoint is allowed');
const rate = Number(process.env.RATE ?? 100), seconds = Number(process.env.SECONDS ?? 5);
const accounts = Number(process.env.ACCOUNTS ?? 8), cap = Number(process.env.CONCURRENCY ?? 64);
if (![rate, seconds, accounts, cap].every(Number.isInteger) || rate < 1 || rate > 20000 || seconds < 1 || seconds > 60 || accounts < 1 || accounts > 32 || cap < 1 || cap > 256) throw Error('Invalid bounded load settings');
const run = randomUUID(), outcomes = [], telemetry = [], errors = {}, submitted = new Map(), sampling = [];
process.on('unhandledRejection', (error) => {
  mkdirSync('tests/performance/results',{recursive:true});
  writeFileSync(`tests/performance/results/load-failure-${run}.json`,JSON.stringify({
    run,targetRate:rate,durationSeconds:seconds,error:String(error),phase:submitted.size?'load-or-drain':'fixture',
    observedOutcomes:outcomes,telemetry,
  },null,2)+'\n');
  console.error(error);
  process.exitCode=1;
});
const exec = promisify(execFile);
async function sample() {
  if (!process.env.KUBECTL) return;
  const args = ['--context', 'kind-ledgerone-audit', '-n', 'ledgerone-local'];
  for (const command of [['top', 'pods'], ['exec', 'kafka-0', '--', '/opt/kafka/bin/kafka-consumer-groups.sh', '--bootstrap-server', 'kafka:9092', '--describe', '--group', 'ledgerone-order-workers']]) {
    try { const result = await exec(process.env.KUBECTL, [...args, ...command], {timeout:15000}); telemetry.push({at:new Date().toISOString(),command:command.join(' '),output:result.stdout}); }
    catch (e) { telemetry.push({at:new Date().toISOString(),command:command.join(' '),error:String(e.stderr ?? e.message)}); }
  }
}
async function api(path, token, body, timeout = 12000) {
  const response = await fetch(new URL(path,base), {method:body?'POST':'GET',headers:{'Content-Type':'application/json',...(token?{Authorization:'Bearer '+token}:{})},body:body?JSON.stringify(body):undefined,signal:AbortSignal.timeout(timeout)});
  const payload = await response.json();
  return {status:response.status,payload};
}
const users = [];
for (let i=0;i<accounts;i++) {
  const registered = await api('/api/auth/register',null,{email:`load-${run}-${i}@example.com`,password:'Local-'+randomUUID(),fullName:'Load Test'},60000);
  if (registered.status !== 201) throw Error('Fixture registration failed: '+JSON.stringify(registered));
  const token=registered.payload.data.accessToken;
  const portfolios=await api('/api/portfolios',token);
  users.push({token,portfolio:portfolios.payload.data[0].id});
}
await sample();
let inFlight=0,sent=0,dropped=0;
const pending = new Set();
const start=performance.now();
const clockStart=Date.now();
let nextSample=2000;
async function send(index) {
  const user=users[index%users.length],id=`load-${run}-${index}`,at=performance.now();
  submitted.set(id,at);
  inFlight++; sent++;
  try {
    const result=await api('/api/orders/queued',user.token,{portfolioId:user.portfolio,symbol:'AAPL',side:'BUY',type:'MARKET',quantity:0.01,clientOrderId:id});
    outcomes.push({id,status:result.status,ms:performance.now()-at,ack:result.status===202 && Number.isInteger(result.payload.data?.offset)});
    if(result.status!==202) errors[result.status]=(errors[result.status]??0)+1;
  } catch(e) { outcomes.push({id,status:0,ms:performance.now()-at,ack:false}); errors.network=(errors.network??0)+1; }
  finally { inFlight--; }
}
let index=0;
while(performance.now()-start < seconds*1000) {
  const elapsed=performance.now()-start;
  const due=Math.min(rate*seconds,Math.floor(elapsed*rate/1000));
  while(index<due) {
    if(inFlight>=cap) {dropped++;index++;continue;}
    const promise=send(index++); pending.add(promise);promise.finally(()=>pending.delete(promise));
  }
  if(elapsed>=nextSample) { sampling.push(sample());nextSample+=5000; }
  await sleep(5);
}
await Promise.all(pending);
const ackFinished=performance.now(), observed=new Map(), persistedStatus={};
const drainDeadline=performance.now()+120000;
while(performance.now()<drainDeadline) {
  for(const user of users) {
    for(let page=0;page<100;page++) {
      const result=await api(`/api/portfolios/${user.portfolio}/orders?size=200&page=${page}`,user.token);
      if(result.status!==200) throw Error('Persistence verification failed');
      const data=result.payload.data;
      for(const order of data.content) if(submitted.has(order.clientOrderId) && !observed.has(order.clientOrderId)) {
        observed.set(order.clientOrderId,performance.now()-submitted.get(order.clientOrderId));
        persistedStatus[order.status]=(persistedStatus[order.status]??0)+1;
      }
      if(data.last)break;
    }
  }
  if(observed.size>=outcomes.filter(x=>x.ack).length)break;
  await sleep(250);
}
const finished=performance.now();
await sample();
await Promise.all(sampling);
const percentile=(values,p)=>{const sorted=[...values].sort((a,b)=>a-b);return sorted.length?+sorted[Math.ceil(sorted.length*p)-1].toFixed(2):null;};
const latency=outcomes.map(x=>x.ms),acks=outcomes.filter(x=>x.ack).length;
const result={
  run,at:new Date(clockStart).toISOString(),environment:{base:base.origin,targetRate:rate,durationSeconds:seconds,accounts,concurrencyCap:cap,node:process.version},
  sent,droppedSchedulingSlots:dropped,httpSentPerSecond:sent/seconds,accepted:acks,acceptedPerSubmissionSecond:acks/seconds,
  brokerAcknowledgements:acks,brokerAcknowledgementsPerElapsedSecond:acks/((ackFinished-start)/1000),
  persistedOrders:observed.size,persistedStatus,verifiedFillsPerElapsedSecond:(persistedStatus.FILLED??0)/((finished-start)/1000),
  httpLatencyMs:{p50:percentile(latency,.5),p95:percentile(latency,.95),p99:percentile(latency,.99)},
  persistenceObservationLatencyMs:{p50:percentile(observed.values(),.5),p95:percentile(observed.values(),.95),p99:percentile(observed.values(),.99)},
  errorRate:sent?(sent-acks)/sent:0,errors,elapsedIncludingDrainSeconds:(finished-start)/1000,
  limitations:['Broker acknowledgement is confirmed by API metadata, not an independent broker latency clock.','Persistence latency includes polling and starts before HTTP send; it is not pure SQL latency.','Bounded concurrency drops scheduling slots rather than creating unbounded promises.','No live price provider is used in the controlled local stack.'],telemetry,outcomes,
};
mkdirSync('tests/performance/results',{recursive:true});
writeFileSync(`tests/performance/results/load-${rate}-${run}.json`,JSON.stringify(result,null,2)+'\n');
console.log(JSON.stringify({...result,telemetry:`${telemetry.length} samples`,outcomes:`${outcomes.length} requests`},null,2));
