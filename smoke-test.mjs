/** End-to-end self check. Run: node smoke-test.mjs (server must be up). */
const BASE = process.env.BASE || 'http://localhost:8090';
let pass = 0;
let fail = 0;

async function call(path, body) {
  const init = body === undefined
    ? {}
    : { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) };
  const res = await fetch(BASE + path, init);
  if (!res.ok) throw new Error(path + ' -> HTTP ' + res.status);
  return res.json();
}

function check(name, ok, detail) {
  (ok ? pass++ : fail++);
  console.log((ok ? '[PASS] ' : '[FAIL] ') + name + ' :: ' + detail);
}

async function main() {
  const boot = await call('/api/dashboard');
  check('service up', boot.code === 0, 'balanced=' + boot.data.balanced);

  const reset = await call('/api/reset', {});
  check('reset environment', reset.code === 0, JSON.stringify(reset.data));

  const created = await call('/api/payments', {
    buyerNo: 'BUYER_001', merchantNo: 'MERCHANT_A',
    amountYuan: '100.00', feeRate: '0.0100', channel: 'WECHAT'
  });
  check('create order', created.code === 0 && created.data.status === 'INIT',
    created.data.orderNo + ' amount=' + created.data.amountCent);
  const orderNo = created.data.orderNo;

  const notified = await call('/api/payments/notify', { orderNo, result: 'SUCCESS', duplicate: 1 });
  check('notify -> SUCCESS', notified.code === 0 && notified.data.status === 'SUCCESS',
    'channelTxn=' + notified.data.channelTxnNo);

  let dash = await call('/api/dashboard');
  check('ledger balanced after payment',
    dash.data.balanced && dash.data.drCent === 10000 && dash.data.crCent === 10000,
    'DR=' + dash.data.drCent + ' CR=' + dash.data.crCent + ' entries=' + dash.data.entryCount);

  const entries = await call('/api/entries?limit=10');
  check('3 append-only entries', entries.data.length === 3,
    entries.data.map(e => e.direction + '/' + e.accountNo + '/' + e.amountCent).join(', '));

  const dup = await call('/api/payments/notify', { orderNo, result: 'SUCCESS', duplicate: 3 });
  dash = await call('/api/dashboard');
  check('duplicate notify is idempotent',
    dup.code === 0 && dash.data.entryCount === 3 && dash.data.voucherCount === 1,
    'entries=' + dash.data.entryCount + ' vouchers=' + dash.data.voucherCount);

  const refund = await call('/api/payments/refund', { orderNo, amountYuan: '10.00' });
  dash = await call('/api/dashboard');
  check('refund posts reversal voucher',
    refund.code === 0 && dash.data.voucherCount === 2 && dash.data.balanced,
    'reversal=' + refund.data.reversalVoucherNo + ' entries=' + dash.data.entryCount);

  const recon1 = await call('/api/recon/run', {});
  check('recon clean', recon1.code === 0 && recon1.data.diffFound === 0,
    'checked=' + recon1.data.channelChecked + ' diffs=' + recon1.data.diffFound);

  const settle = await call('/api/settlements/run', {});
  check('T+1 settlement', settle.code === 0 && settle.data.created >= 1,
    'created=' + settle.data.created + ' totalCent=' + settle.data.totalCent);

  const seeded = await call('/api/seed', { count: 5 });
  check('seed real data', seeded.code === 0 && seeded.data.created === 5,
    'created=' + seeded.data.created + ' in ' + seeded.data.elapsedMs + 'ms');

  await call('/api/anomaly', { type: 'BILL_AMOUNT_MISMATCH' });
  const recon2 = await call('/api/recon/run', {});
  check('anomaly detected by recon', recon2.code === 0 && recon2.data.diffFound >= 1,
    'diffs=' + recon2.data.diffFound);

  const diffs = await call('/api/diffs?limit=10');
  const open = diffs.data.find(d => d.status === 'OPEN');
  check('diff ticket created', !!open, open ? open.diffType + ' #' + open.id : 'none');
  if (open) {
    const resolved = await call('/api/recon/resolve', {
      id: open.id, handler: 'SMOKE_TEST', remark: 'auto closed by smoke test'
    });
    check('diff ticket resolved', resolved.code === 0 && resolved.data.status === 'RESOLVED',
      '#' + resolved.data.id);
  }

  const late = await call('/api/payments/notify', { orderNo, result: 'FAILED', duplicate: 1 });
  check('out-of-order FAILED callback rejected', late.code === 1, late.message);

  const rules = await call('/api/rules');
  check('risk rules loaded', rules.data.length === 3,
    rules.data.map(r => r.ruleCode).join(', '));

  const panelUrls = [
    '/api/accounts', '/api/orders?limit=50', '/api/entries?limit=50',
    '/api/vouchers?limit=50', '/api/bills?limit=50', '/api/channel-txns?limit=50',
    '/api/splits?limit=50', '/api/refunds?limit=50', '/api/settlements?limit=50',
    '/api/diffs?limit=50', '/api/rules', '/api/events?limit=50',
    '/api/events/after?afterId=0&limit=10'
  ];
  const panelResults = await Promise.all(panelUrls.map(u => call(u)));
  check('all console data panels respond',
    panelResults.every(r => r.code === 0 && Array.isArray(r.data)),
    panelUrls.length + ' endpoints OK');

  const page = await fetch(BASE + '/');
  const html = await page.text();
  check('console page served', page.status === 200 && html.includes('操作台'), 'HTTP ' + page.status);
  const js = await fetch(BASE + '/app.js');
  check('frontend assets served', js.status === 200, 'app.js HTTP ' + js.status);

  dash = await call('/api/dashboard');
  check('final ledger still balanced', dash.data.balanced,
    'DR=' + dash.data.drCent + ' CR=' + dash.data.crCent + ' entries=' + dash.data.entryCount);

  console.log('\n=== ' + pass + ' passed, ' + fail + ' failed ===');
  if (fail > 0) process.exitCode = 1;
}

main().catch(e => {
  console.error('[ERROR] ' + e.message);
  process.exitCode = 1;
});
