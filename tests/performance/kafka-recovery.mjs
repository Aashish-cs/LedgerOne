import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';
import {writeFileSync, mkdirSync} from 'node:fs';
import {execFile, spawn} from 'node:child_process';
import {promisify} from 'node:util';
import {setTimeout as sleep} from 'node:timers/promises';

// This disruptive test is deliberately restricted to the disposable Kind namespace.
const exec = promisify(execFile), kubectl = process.env.KUBECTL;
assert(kubectl, 'KUBECTL is required');
const base = new URL(process.env.BENCH_URL ?? 'http://127.0.0.1:18090');
assert(['localhost','127.0.0.1'].includes(base.hostname), 'Loopback only');
const args = ['--context','kind-ledgerone-audit','-n','ledgerone-local'];
const run = randomUUID(), evidence = {run,at:new Date().toISOString(),checks:[]};
let token;
async function k(...command) { return (await exec(kubectl,[...args,...command],{timeout:180000})).stdout; }
async function api(path, body) {
  const r = await fetch(new URL(path,base),{method:body?'POST':'GET',headers:{'Content-Type':'application/json',...(token?{Authorization:`Bearer ${token}`}:{})},body:body?JSON.stringify(body):undefined,signal:AbortSignal.timeout(20000)});
  return {status:r.status,...await r.json()};
}
async function publish(lines) {
  await new Promise((resolve,reject) => {
    const child = spawn(kubectl,[...args,'exec','-i','kafka-0','--','/opt/kafka/bin/kafka-console-producer.sh','--bootstrap-server','kafka:9092','--topic','ledgerone.orders.v1','--property','parse.key=true','--property','key.separator=|']);
    let err=''; child.stderr.on('data',x=>err+=x); child.on('error',reject);
    child.on('close',code=>code===0?resolve():reject(Error(err))); child.stdin.end(lines.join('\n')+'\n');
  });
}
try {
  const session = await api('/api/auth/register',{email:`recovery-${run}@example.com`,password:'Local-'+randomUUID(),fullName:'Kafka Recovery'});
  assert.equal(session.status,201); token=session.data.accessToken;
  const user=session.data.user.id, account=(await api('/api/portfolios')).data[0].id;
  const order={portfolioId:account,symbol:'AAPL',side:'BUY',type:'MARKET',quantity:1,clientOrderId:`recovery-${run}`};
  await k('scale','deployment/ledgerone-order-worker','--replicas=0');
  await k('wait','--for=delete','pod','-l','app=ledgerone-order-worker','--timeout=90s');
  const receipt=await api('/api/orders/queued',order);
  assert.equal(receipt.status,202); assert(Number.isInteger(receipt.data.offset));
  assert.equal((await api('/api/orders/queued?clientOrderId='+order.clientOrderId)).status,404);
  evidence.checks.push({scenario:'worker stopped',result:'broker acknowledged; order not recorded yet',receipt:receipt.data});
  await k('scale','deployment/ledgerone-order-worker','--replicas=2');
  await k('rollout','status','deployment/ledgerone-order-worker','--timeout=180s');
  let completed;
  for(let i=0;i<60;i++) {
    completed=await api('/api/orders/queued?clientOrderId='+order.clientOrderId);
    if(completed.status===200)break;
    await sleep(500);
  }
  assert.equal(completed.data?.status,'FILLED');
  const line=user+'|'+JSON.stringify({userId:user,order});
  await publish([line,line]);
  const poison=`invalid-${run}`;
  await publish([user+'|'+poison]);
  let dlt;
  try { dlt=await k('exec','kafka-0','--','/opt/kafka/bin/kafka-console-consumer.sh','--bootstrap-server','kafka:9092','--topic','ledgerone.orders.v1-dlt','--from-beginning','--timeout-ms','5000','--max-messages','1000'); }
  catch(error) { dlt=error.stdout ?? ''; }
  assert(dlt.includes(poison),'Expected this run\'s malformed record in the DLT');
  await sleep(3000);
  const orders=(await api(`/api/portfolios/${account}/orders?size=200`)).data.content;
  const ledger=(await api(`/api/portfolios/${account}/transactions?size=200`)).data.content;
  assert.equal(orders.length,1); assert.equal(ledger.length,1);
  const after=(await api('/api/portfolios')).data[0].cashBalance;
  const expected=100000-Number(completed.data.executionPrice)-Number(completed.data.fees);
  assert(Math.abs(Number(after)-expected)<0.001);
  evidence.checks.push({scenario:'worker recovery and duplicate Kafka replay',result:'one filled order, one ledger entry, one cash deduction',order:completed.data,remainingCash:after});
  evidence.checks.push({scenario:'malformed Kafka record',result:'dead-lettered without a trade',payload:dlt.trim()});
  evidence.consumerGroup=await k('exec','kafka-0','--','/opt/kafka/bin/kafka-consumer-groups.sh','--bootstrap-server','kafka:9092','--describe','--group','ledgerone-order-workers');
  evidence.passed=true;
} catch(error) { evidence.passed=false; evidence.error=String(error); process.exitCode=1; }
finally {
  try { await k('scale','deployment/ledgerone-order-worker','--replicas=2'); }
  catch(error) { evidence.cleanupError=String(error); process.exitCode=1; }
  mkdirSync('tests/performance/results',{recursive:true});
  writeFileSync(`tests/performance/results/kafka-recovery-${run}.json`,JSON.stringify(evidence,null,2)+'\n');
  console.log(JSON.stringify(evidence,null,2));
}
