import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';
import {mkdirSync,writeFileSync} from 'node:fs';
const {chromium}=await import(process.env.PLAYWRIGHT_MODULE ?? 'playwright');
const base=process.env.BROWSER_URL ?? 'http://127.0.0.1:18090';
assert(['localhost','127.0.0.1'].includes(new URL(base).hostname),'Local test stack only');
const browser=await chromium.launch({headless:true});
const page=await browser.newPage({viewport:{width:1440,height:1000}});
page.setDefaultTimeout(20000);
const result={at:new Date().toISOString(),checks:[],errors:[]};
const run=randomUUID();
page.on('pageerror',e=>result.errors.push(e.message));
const dir='tests/browser/results'; mkdirSync(dir,{recursive:true});
async function api(path){
  const token=await page.evaluate(()=>localStorage.getItem('ledgerone.accessToken'));
  const r=await page.request.get(base+path,{headers:{Authorization:'Bearer '+token}});
  assert.equal(r.status(),200); return (await r.json()).data;
}
async function trade(side){
  await page.locator('input[name="quantity"]').fill('1');
  if(side==='SELL')await page.locator('label').filter({has:page.locator('input[name="side"][value="SELL"]')}).click();
  const response=page.waitForResponse(r=>r.url().includes('/api/orders/queued')&&r.request().method()==='POST');
  await page.getByRole('button',{name:'Submit order',exact:true}).click();
  assert.equal((await response).status(),202);
  await page.getByText('Order filled for AAPL',{exact:true}).waitFor();
  // Wait until the mutation is settled, not merely an old success notice.
  await page.getByRole('button',{name:'Submit order',exact:true}).waitFor();
}
try {
  await page.goto(base+'/login');
  await page.getByRole('button',{name:'Register',exact:true}).click();
  await page.locator('input[name="fullName"]').fill('Browser Test');
  await page.locator('input[name="email"]').fill('browser-'+randomUUID()+'@example.com');
  await page.locator('input[name="password"]').fill('Local-'+randomUUID());
  await page.getByRole('button',{name:'Create account',exact:true}).click();
  await page.waitForURL(base+'/');
  await page.getByText('Account Value This Session',{exact:true}).waitFor();
  assert.equal((await api('/api/portfolios'))[0].cashBalance,100000);
  result.checks.push('UI registration creates one $100K paper account');
  await page.getByRole('link',{name:'Trading',exact:true}).click();
  await trade('BUY');
  const bought=(await api('/api/portfolios'))[0];
  assert(bought.cashBalance<100000); assert.equal(bought.holdings[0].quantity,1);
  // Observe the refreshed amount in the UI, not just the direct API response.
  const amount=new Intl.NumberFormat('en-US',{style:'currency',currency:'USD',maximumFractionDigits:2}).format(bought.cashBalance);
  await page.getByText(amount,{exact:true}).first().waitFor();
  await page.evaluate(()=>window.scrollTo(0,0));
  await page.screenshot({path:dir+'/trading-desktop.png',fullPage:true});
  result.checks.push('Queued UI buy fills and refreshes visible buying power/holdings');
  await page.getByRole('button',{name:'Submit order',exact:true}).waitFor();
  await trade('SELL');
  const sold=(await api('/api/portfolios'))[0];
  assert.equal(sold.cashBalance,99998);
  assert.equal(sold.holdings.filter(h=>h.symbol==='AAPL').reduce((sum,h)=>sum+h.quantity,0),0);
  result.checks.push('UI sell restores cash less two $1 fees and leaves zero shares');
  await page.getByRole('link',{name:'Watchlist',exact:true}).click();
  await page.locator('input[name="symbol"]').fill('AAPL');
  await page.getByRole('button',{name:'Add',exact:true}).click();
  await page.getByRole('button',{name:'Remove AAPL',exact:true}).waitFor();
  await page.getByRole('button',{name:'Remove AAPL',exact:true}).click();
  await page.getByText('AAPL removed from the watchlist',{exact:true}).waitFor();
  result.checks.push('Watchlist add and remove persist through real API');
  await page.setViewportSize({width:390,height:844});
  await page.goto(base+'/trading');
  await page.getByRole('button',{name:'Submit order',exact:true}).waitFor();
  await page.waitForFunction(()=>document.querySelector('input[name="portfolioId"]')?.value);
  await page.locator('table tbody tr').first().waitFor();
  await page.screenshot({path:dir+'/trading-mobile.png',fullPage:true});
  const overflow=await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth+1);
  assert.equal(overflow,false,'Page must not overflow the mobile viewport');
  assert.equal(result.errors.length,0);
  result.checks.push('390px mobile rendering has no page overflow or uncaught JS errors');
  result.passed=true;
} catch(error) { result.passed=false; result.error=String(error); process.exitCode=1;
  await page.screenshot({path:dir+'/failure.png',fullPage:true}).catch(()=>{});
} finally {await browser.close();writeFileSync(dir+'/smoke-'+run+'.json',JSON.stringify(result,null,2)+'\n');console.log(JSON.stringify(result,null,2));}
