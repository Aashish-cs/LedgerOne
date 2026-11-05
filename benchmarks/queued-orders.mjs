import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { performance } from 'node:perf_hooks';
import { setTimeout as sleep } from 'node:timers/promises';

const origin = new URL(process.env.LEDGERONE_BENCH_URL ?? 'http://127.0.0.1:18080');
assert(['127.0.0.1', 'localhost', '[::1]'].includes(origin.hostname), 'Use an isolated local stack');
const accountCount = Number(process.env.ACCOUNTS ?? 4);
const ordersPerAccount = Number(process.env.ORDERS_PER_ACCOUNT ?? 25);
assert(Number.isInteger(accountCount) && accountCount >= 2 && accountCount <= 32);
assert(Number.isInteger(ordersPerAccount) && ordersPerAccount >= 1 && ordersPerAccount <= 200);
const runId = randomUUID();

async function request(path, { status = 200, token, body } = {}) {
  const response = await fetch(new URL(path, origin), {
    redirect: 'error',
    method: body ? 'POST' : 'GET',
    headers: {
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(body ? { 'Content-Type': 'application/json' } : {}),
    },
    body: body ? JSON.stringify(body) : undefined,
    signal: AbortSignal.timeout(12_000),
  });
  const payload = await response.json();
  assert.equal(response.status, status, `${path}: ${JSON.stringify(payload)}`);
  return payload.data;
}

async function register(index) {
  const { accessToken: token } = await request('/api/auth/register', {
    status: 201,
    body: {
      email: `bench-${runId}-${index}@example.com`,
      password: `Bench-${randomUUID()}`,
      fullName: 'Local Benchmark',
    },
  });
  const [portfolio] = await request('/api/portfolios', { token });
  assert.equal(portfolio.cashBalance, 100_000);
  return { token, portfolioId: portfolio.id };
}

function submit(account, clientOrderId, overrides = {}) {
  return request('/api/orders/queued', {
    status: 202,
    token: account.token,
    body: { portfolioId: account.portfolioId, symbol: 'AAPL', side: 'BUY',
      type: 'MARKET', quantity: 0.01, clientOrderId, ...overrides },
  });
}

function collection(account, name) {
  return request(`/api/portfolios/${account.portfolioId}/${name}?size=250`, { token: account.token });
}

async function waitForOrders(account, expected) {
  const deadline = performance.now() + 90_000;
  while (performance.now() < deadline) {
    const { content } = await collection(account, 'orders');
    if (content.length >= expected) return content;
    await sleep(50);
  }
  throw new Error('Orders did not reach PostgreSQL before the timeout');
}

const accounts = [];
for (let index = 0; index < accountCount; index++) accounts.push(await register(index));
const ackTimes = [];
const started = performance.now();
await Promise.all(accounts.map(async (account, index) => {
  for (let first = 0; first < ordersPerAccount; first += 8) {
    const batch = Array.from({ length: Math.min(8, ordersPerAccount - first) }, (_, offset) => first + offset);
    await Promise.all(batch.map(async (order) => {
      const id = `${runId}-${index}-${order}`;
      const submitted = performance.now();
      const receipt = await submit(account, id);
      ackTimes.push(performance.now() - submitted);
      assert.equal(receipt.clientOrderId, id);
    }));
  }
}));
const acknowledged = performance.now();
const persisted = await Promise.all(accounts.map(account => waitForOrders(account, ordersPerAccount)));
const observed = performance.now();

for (const [index, orders] of persisted.entries()) {
  assert.equal(orders.length, ordersPerAccount);
  const seenIds = new Set(orders.map(order => order.clientOrderId));
  for (let order = 0; order < ordersPerAccount; order++) assert(seenIds.has(`${runId}-${index}-${order}`));
  assert(orders.every(order => order.status === 'FILLED'));
  const { totalElements } = await collection(accounts[index], 'transactions');
  assert.equal(totalElements, ordersPerAccount);
  const { portfolioCash } = await request('/api/account/summary', { token: accounts[index].token });
  const spent = orders.reduce((sum, order) => sum + Math.round(order.executionPrice * order.quantity * 10_000)
    + Math.round(order.fees * 10_000), 0);
  assert.equal(Math.round(portfolioCash * 10_000), 100_000 * 10_000 - spent);
}

const first = accounts[0];
const initialCash = (await request('/api/account/summary', { token: first.token })).portfolioCash;
const duplicate = `${runId}-0-0`;
const marker = `${runId}-marker`;
await Promise.all([submit(first, duplicate), submit(first, duplicate)]);
// The marker shares the account's partition and follows both retry messages.
await submit(first, marker, { type: 'LIMIT', limitPrice: 1 });
const afterRetry = await waitForOrders(first, ordersPerAccount + 1);
assert.equal(afterRetry.length, ordersPerAccount + 1);
assert.equal(afterRetry.find(order => order.clientOrderId === marker)?.status, 'PENDING');
assert.equal((await collection(first, 'transactions')).totalElements, ordersPerAccount);
assert.equal((await request('/api/account/summary', { token: first.token })).portfolioCash, initialCash);
await request(`/api/orders/queued?clientOrderId=${duplicate}`, { token: accounts[1].token, status: 404 });

const quantity = Math.floor(0.7 * initialCash / persisted[0][0].executionPrice);
const competing = await Promise.all([1, 2].map(index => request('/api/orders', {
  token: first.token,
  body: { portfolioId: first.portfolioId, symbol: 'AAPL', side: 'BUY', type: 'MARKET',
    quantity, clientOrderId: `${runId}-competing-${index}` },
})));
assert.deepEqual(competing.map(order => order.status).sort(), ['FILLED', 'REJECTED']);
assert.equal((await collection(first, 'transactions')).totalElements, ordersPerAccount + 1);

const rate = (elapsedMs) => Number((ackTimes.length * 1000 / elapsedMs).toFixed(1));
ackTimes.sort((a, b) => a - b);
console.log(JSON.stringify({
  orders: ackTimes.length,
  accounts: accountCount,
  requestsInFlightPerAccount: 8,
  acknowledgedPerSecond: rate(acknowledged - started),
  fillsObservedPerSecond: rate(observed - started),
  p95AcknowledgementMs: Number(ackTimes[Math.ceil(ackTimes.length * 0.95) - 1].toFixed(1)),
  verified: 'unique fills, correct cash and ledger, duplicate replay, pending limit, scoped lookup, concurrent buying-power check',
}, null, 2));
