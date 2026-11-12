(function () {
  'use strict';

  const $ = (id) => document.getElementById(id);
  const v = (id) => ($(id) ? $(id).value.trim() : '');
  const state = { lastEventId: 0, activeTab: 'accounts', reconDone: false, dash: {}, flowFlags: {} };

  function esc(s) {
    return String(s == null ? '' : s).replace(/[&<>"']/g, (c) => ({
      '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
    }[c]));
  }

  function yuan(cent) {
    return (Number(cent || 0) / 100).toLocaleString('zh-CN', {
      minimumFractionDigits: 2, maximumFractionDigits: 2
    });
  }

  function shortTime(s) {
    return s ? String(s).slice(5, 19) : '-';
  }

  function pill(x) {
    const s = String(x == null ? '' : x);
    let cls = '';
    if (/SUCCESS|OK|SETTLED|RESOLVED|POSTED|ACTIVE/.test(s)) cls = 'g';
    else if (/FAILED|REJECTED|OPEN|CLOSED|UNBALANCED|MISMATCH|SHORT|LONG/.test(s)) cls = 'r';
    else if (/INIT|PENDING|PART_/.test(s)) cls = 'a';
    else if (/IDEMPOTENT|SKIPPED|REFUND/.test(s)) cls = 'p';
    return '<span class="pill ' + cls + '">' + esc(s) + '</span>';
  }

  async function api(path, body, method) {
    const opt = { method: method || (body === undefined ? 'GET' : 'POST'), headers: { 'Content-Type': 'application/json' } };
    if (body !== undefined) opt.body = JSON.stringify(body);
    const res = await fetch(path, opt);
    if (!res.ok) throw new Error('HTTP ' + res.status);
    return res.json();
  }

  const get = (p) => api(p);
  const post = (p, b) => api(p, b || {}, 'POST');
  const put = (p, b) => api(p, b || {}, 'PUT');

  function toast(msg, isError) {
    const t = $('toast');
    t.textContent = (isError ? '✗ ' : '✓ ') + msg;
    t.className = 'toast show' + (isError ? ' error' : '');
    clearTimeout(t._timer);
    t._timer = setTimeout(() => { t.className = 'toast'; }, 7000);
  }

  const COL = {
    text: (r, k) => esc(r[k]),
    cent: (r, k) => '<span class="num">' + yuan(r[k]) + '</span>',
    pill: (r, k) => pill(r[k]),
    time: (r, k) => '<span style="color:var(--muted)">' + esc(shortTime(r[k])) + '</span>',
    wrap: (r, k) => '<span title="' + esc(r[k]) + '">' + esc(r[k]) + '</span>'
  };

  const TABS = [
    {
      key: 'accounts', label: '账户余额', url: '/api/accounts',
      cols: [['账户', 'accountNo', 'text'], ['名称', 'accountName', 'text'],
        ['科目性质', 'accountType', 'text'], ['期初余额(元)', 'openingBalanceCent', 'cent'],
        ['当前余额(元)', 'balanceCent', 'cent'], ['版本', 'version', 'text'], ['状态', 'status', 'pill']]
    },
    {
      key: 'orders', label: '支付订单', url: '/api/orders?limit=50',
      cols: [['订单号', 'orderNo', 'text'], ['商户', 'merchantNo', 'text'], ['买家', 'buyerNo', 'text'],
        ['金额(元)', 'amountCent', 'cent'], ['费率', 'feeRate', 'text'],
        ['商户应收', 'merchantCent', 'cent'], ['佣金', 'commissionCent', 'cent'],
        ['已退', 'refundedCent', 'cent'], ['状态', 'status', 'pill'], ['渠道', 'channel', 'text'],
        ['时间', 'createdAt', 'time']]
    },
    {
      key: 'entries', label: '分录流水', url: '/api/entries?limit=50',
      cols: [['#', 'id', 'text'], ['凭证', 'voucherNo', 'text'], ['账户', 'accountNo', 'text'],
        ['方向', 'direction', 'pill'], ['金额(元)', 'amountCent', 'cent'],
        ['变动前', 'beforeBalanceCent', 'cent'], ['变动后', 'afterBalanceCent', 'cent'],
        ['业务单号', 'bizNo', 'wrap'], ['时间', 'createdAt', 'time']]
    },
    {
      key: 'vouchers', label: '记账凭证', url: '/api/vouchers?limit=50',
      cols: [['凭证号', 'voucherNo', 'custom', (r) => '<span class="link" data-voucher="' + esc(r.voucherNo) + '">' + esc(r.voucherNo) + '</span>'],
        ['业务类型', 'bizType', 'text'], ['借方合计(元)', 'totalCent', 'cent'],
        ['业务单号', 'bizNo', 'wrap'], ['冲正原凭证', 'originalVoucherNo', 'text'],
        ['状态', 'status', 'pill'], ['时间', 'createdAt', 'time']]
    },
    {
      key: 'bills', label: '渠道账单', url: '/api/bills?limit=50',
      cols: [['渠道流水号', 'channelTxnNo', 'text'], ['账单日期', 'billDate', 'text'],
        ['渠道', 'channel', 'text'], ['金额(元)', 'amountCent', 'cent'],
        ['状态', 'status', 'pill'], ['被篡改', 'tampered', 'text']]
    },
    {
      key: 'refunds', label: '退款冲正', url: '/api/refunds?limit=50',
      cols: [['退款单', 'refundNo', 'text'], ['原订单', 'originalOrderNo', 'text'],
        ['退款(元)', 'amountCent', 'cent'], ['冲减商户', 'merchantCent', 'cent'],
        ['冲减佣金', 'commissionCent', 'cent'], ['冲正凭证', 'reversalVoucherNo', 'text'],
        ['状态', 'status', 'pill'], ['时间', 'createdAt', 'time']]
    },
    {
      key: 'settlements', label: '结算批次', url: '/api/settlements?limit=50',
      cols: [['批次', 'batchNo', 'text'], ['商户', 'merchantNo', 'text'], ['日期', 'settleDate', 'text'],
        ['分账总额', 'grossCent', 'cent'], ['退款轧差', 'refundCent', 'cent'],
        ['佣金', 'feeCent', 'cent'], ['实付(元)', 'netCent', 'cent'],
        ['状态', 'status', 'pill'], ['凭证', 'voucherNo', 'text']]
    },
    {
      key: 'diffs', label: '差异工单', url: '/api/diffs?limit=50',
      cols: [['#', 'id', 'text'], ['对账批次', 'reconNo', 'text'], ['日期', 'reconDate', 'text'],
        ['差异类型', 'diffType', 'pill'], ['范围', 'diffScope', 'text'], ['业务单号', 'bizNo', 'wrap'],
        ['期望(元)', 'expectedCent', 'cent'], ['实际(元)', 'actualCent', 'cent'],
        ['状态', 'status', 'pill'], ['处理人', 'handler', 'text'],
        ['', '_op', 'custom', (r) => (r.status === 'RESOLVED' || r.status === 'REJECTED')
          ? '<span style="color:var(--muted)">已闭环</span>'
          : '<button class="btn tiny" data-resolve="' + r.id + '">闭环</button>']]
    },
    { key: 'rules', label: '风控规则', url: '/api/rules', custom: rulesHtml },
    {
      key: 'events-table', label: '事件明细', url: '/api/events?limit=50',
      cols: [['#', 'id', 'text'], ['时间', 'createdAt', 'time'], ['主题', 'topic', 'text'],
        ['动作', 'action', 'text'], ['结果', 'result', 'pill'], ['业务单号', 'bizNo', 'text'],
        ['详情', 'detail', 'wrap'], ['traceNo', 'traceNo', 'text']]
    }
  ];

  function rulesHtml(rows) {
    if (!rows.length) return '<div class="empty">暂无规则</div>';
    let h = '<table><thead><tr><th>规则编码</th><th>说明</th><th>值（可直接改）</th><th>启用</th><th>操作</th></tr></thead><tbody>';
    rows.forEach((r) => {
      h += '<tr><td>' + esc(r.ruleCode) + '</td><td class="wrap">' + esc(r.ruleName)
        + '<br><span style="color:var(--muted)">' + esc(r.remark || '') + '</span></td>'
        + '<td><input class="rule-input" id="rv_' + r.id + '" value="' + esc(String(r.ruleValue)) + '"></td>'
        + '<td><input class="rule-check" type="checkbox" id="re_' + r.id + '"' + (r.enabled === 1 ? ' checked' : '') + '></td>'
        + '<td><button class="btn tiny" data-rule="' + esc(r.ruleCode) + '" data-ruleid="' + r.id + '">保存</button></td></tr>';
    });
    return h + '</tbody></table>';
  }

  function tableHtml(rows, tab) {
    if (!rows || !rows.length) return '<div class="empty">暂无数据，去左边操作台造一条</div>';
    let h = '<table><thead><tr>' + tab.cols.map((c) => '<th>' + esc(c[0]) + '</th>').join('') + '</tr></thead><tbody>';
    rows.forEach((r) => {
      h += '<tr>' + tab.cols.map((c) => {
        const val = c[2] === 'custom' ? c[3](r) : COL[c[2]](r, c[1]);
        return '<td class="' + (c[2] === 'cent' ? 'num' : (c[1] === 'detail' || c[1] === 'bizNo' || c[1] === 'remark' ? 'wrap' : '')) + '">' + val + '</td>';
      }).join('') + '</tr>';
    });
    return h + '</tbody></table>';
  }

  function activeTab() {
    return TABS.find((t) => t.key === state.activeTab) || TABS[0];
  }

  function renderTabs() {
    $('tabs').innerHTML = TABS.map((t) =>
      '<button class="tab' + (t.key === state.activeTab ? ' active' : '') + '" data-tab="' + t.key + '">' + esc(t.label) + '</button>'
    ).join('');
  }

  async function refreshTab() {
    const tab = activeTab();
    const r = await get(tab.url);
    if (r.code !== 0) return;
    $('tablePanel').innerHTML = tab.custom ? tab.custom(r.data) : tableHtml(r.data, tab);
    if (tab.key === 'orders') {
      state.flowFlags.success = r.data.some((o) => o.status !== 'INIT' && o.status !== 'FAILED');
    }
  }

  async function refreshDashboard() {
    const r = await get('/api/dashboard');
    if (r.code !== 0) return;
    const d = state.dash = r.data;
    $('kOrder').textContent = d.orderCount;
    $('kVoucher').textContent = d.voucherCount;
    $('kEntry').textContent = d.entryCount;
    $('kDr').textContent = yuan(d.drCent);
    $('kCr').textContent = yuan(d.crCent);
    $('kDiff').textContent = d.openDiffCount;
    const badge = $('balanceBadge');
    badge.textContent = d.balanced ? '借贷平衡 ✓' : '借贷不平衡 ✗';
    badge.className = 'badge ' + (d.balanced ? 'badge-ok' : 'badge-bad');
    renderFlow();
  }

  function renderFlow() {
    const d = state.dash || {};
    const steps = [
      ['下单', (d.orderCount || 0) > 0, '创建支付订单'],
      ['回调', !!state.flowFlags.success, '渠道扣款通知'],
      ['记账', (d.voucherCount || 0) > 0, '凭证 + 分录'],
      ['分账', (d.voucherCount || 0) > 0, '商户 / 佣金'],
      ['对账', !!state.reconDone, '三层核对'],
      ['结算', (d.settledCount || 0) > 0, 'T+1 打款']
    ];
    $('flow').innerHTML = steps.map((s) =>
      '<div class="flow-node' + (s[1] ? ' on' : '') + '">' + esc(s[0]) + '<b>' + esc(s[2]) + '</b></div>'
    ).join('');
  }

  function eventHtml(e) {
    let cls = '';
    if (e.result === 'REJECTED') cls = 'reject';
    else if (e.result === 'IDEMPOTENT' || e.result === 'SKIPPED') cls = 'idem';
    else if (e.result === 'DIFF_FOUND') cls = 'diff';
    else if (e.result === 'OK') cls = 'ok';
    return '<div class="event ' + cls + '"><div class="event-head">'
      + '<span class="event-time">' + esc(shortTime(e.createdAt)) + '</span>'
      + '<span class="topic">' + esc(e.topic) + '</span>'
      + '<span class="event-action">' + esc(e.action) + '</span>'
      + '<span class="event-result ' + esc(e.result) + '">' + esc(e.result) + '</span></div>'
      + '<div class="event-detail">' + esc(e.detail) + '</div>'
      + '<div class="event-trace">traceNo ' + esc(e.traceNo) + ' · ' + esc(e.bizNo || '-') + '</div></div>';
  }

  async function loadEvents() {
    const r = await get('/api/events?limit=60');
    if (r.code !== 0) return;
    const list = r.data || [];
    $('eventStream').innerHTML = list.length
      ? list.map(eventHtml).join('') : '<div class="event-empty">还没有事件，去左边创建第一笔订单</div>';
    state.lastEventId = list.reduce((m, e) => Math.max(m, Number(e.id) || 0), 0);
    state.reconDone = list.some((e) => String(e.action).indexOf('对账完成') >= 0);
    state.flowFlags.success = state.flowFlags.success
      || list.some((e) => String(e.action).indexOf('回调 SUCCESS') >= 0 || e.action === '批量造数');
    renderFlow();
  }

  async function pollEvents() {
    const r = await get('/api/events/after?afterId=' + state.lastEventId + '&limit=100');
    if (r.code !== 0 || !r.data || !r.data.length) return;
    const box = $('eventStream');
    if (box.querySelector('.event-empty')) box.innerHTML = '';
    [...r.data].reverse().forEach((e) => {
      box.insertAdjacentHTML('afterbegin', eventHtml(e));
      state.lastEventId = Math.max(state.lastEventId, Number(e.id) || 0);
    });
    while (box.children.length > 120) box.removeChild(box.lastChild);
    if (r.data.some((e) => String(e.action).indexOf('对账完成') >= 0)) state.reconDone = true;
    if (r.data.some((e) => String(e.action).indexOf('回调 SUCCESS') >= 0 || e.action === '批量造数')) {
      state.flowFlags.success = true;
    }
    renderFlow();
  }

  function describe(d) {
    if (!d) return '操作完成';
    if (d.detail) return d.detail;
    if (d.orderNo) return '订单 ' + d.orderNo + ' 状态 ' + d.status + '，金额 ' + yuan(d.amountCent)
      + ' 元（分账 商户 ' + yuan(d.merchantCent) + ' / 佣金 ' + yuan(d.commissionCent) + '）';
    if (d.refundNo) return '退款单 ' + d.refundNo + '，冲正凭证 ' + d.reversalVoucherNo;
    if (d.batchNo) return '结算批次 ' + d.batchNo + '，新建 ' + d.created + ' 笔、跳过 ' + d.skipped
      + ' 笔，实际打款 ' + yuan(d.totalCent) + ' 元';
    if (d.reconNo) return '对账 ' + d.reconNo + '：渠道核对 ' + d.channelChecked + ' 笔，新增差异 '
      + d.diffFound + ' 笔，未闭环 ' + d.openDiffCount + ' 笔';
    if (d.created !== undefined) return '生成 ' + d.created + ' 笔支付，风控拒绝 ' + d.rejected
      + ' 笔，耗时 ' + d.elapsedMs + ' ms';
    if (d.reset) return '业务数据已清空，账户回到期初余额';
    return '操作完成';
  }

  const ACTIONS = {
    createPayment: () => post('/api/payments', {
      buyerNo: v('buyerNo'), merchantNo: v('merchantNo'), amountYuan: v('amountYuan'),
      feeRate: v('feeRate'), channel: v('channel'), idempotencyKey: v('idempotencyKey') || undefined
    }),
    notify: () => post('/api/payments/notify', {
      orderNo: v('notifyOrderNo'), result: v('result'), duplicate: Number(v('duplicate')) || 1
    }),
    refund: () => post('/api/payments/refund', {
      orderNo: v('refundOrderNo'), amountYuan: v('refundAmount')
    }),
    seed: () => post('/api/seed', { count: Number(v('seedCount')) || 50 }),
    anomaly: () => post('/api/anomaly', { type: v('anomalyType') }),
    recon: () => post('/api/recon/run', { date: v('reconDate') }),
    settle: () => post('/api/settlements/run', { date: v('settleDate') }),
    reset: () => post('/api/reset')
  };

  async function runAction(name, btn) {
    btn.disabled = true;
    try {
      const r = await ACTIONS[name]();
      toast(describe(r.code === 0 ? r.data : null) + (r.code === 0 ? '' : '｜' + r.message), r.code !== 0);
      if (name === 'createPayment' && r.code === 0 && r.data && r.data.orderNo) {
        $('notifyOrderNo').value = r.data.orderNo;
        $('refundOrderNo').value = r.data.orderNo;
      }
      if (name === 'reset' && r.code === 0) {
        state.reconDone = false;
        state.flowFlags.success = false;
        $('eventStream').innerHTML = '';
      }
      await Promise.all([refreshDashboard(), refreshTab(), pollEvents()]);
    } catch (err) {
      toast('请求失败：' + err.message, true);
    } finally {
      btn.disabled = false;
    }
  }

  async function fillOrder(targetId) {
    const r = await get('/api/orders?limit=1');
    if (r.code === 0 && r.data && r.data.length) {
      $(targetId).value = r.data[0].orderNo;
      toast('已填入最新订单 ' + r.data[0].orderNo);
    } else {
      toast('还没有订单，先点第 1 步创建', true);
    }
  }

  $('tabs').addEventListener('click', (e) => {
    const btn = e.target.closest('[data-tab]');
    if (!btn) return;
    state.activeTab = btn.dataset.tab;
    renderTabs();
    refreshTab();
  });

  $('tablePanel').addEventListener('click', async (e) => {
    const btn = e.target.closest('button');
    const link = e.target.closest('[data-voucher]');

    if (btn && btn.dataset.resolve) {
      btn.disabled = true;
      const r = await post('/api/recon/resolve', {
        id: Number(btn.dataset.resolve), handler: 'OPS_01', remark: '控制台人工核查后确认闭环'
      });
      toast(r.code === 0 ? '工单 #' + r.data.id + ' 已闭环' : r.message, r.code !== 0);
      await Promise.all([refreshDashboard(), refreshTab(), pollEvents()]);
      return;
    }
    if (btn && btn.dataset.rule) {
      btn.disabled = true;
      const id = btn.dataset.ruleid;
      const r = await put('/api/rules/' + encodeURIComponent(btn.dataset.rule), {
        value: $('rv_' + id).value, enabled: $('re_' + id).checked ? 1 : 0
      });
      toast(r.code === 0 ? '规则 ' + r.data.ruleCode + ' 已更新为 ' + r.data.ruleValue : r.message, r.code !== 0);
      await pollEvents();
      btn.disabled = false;
      return;
    }
    if (link) {
      const no = link.dataset.voucher;
      const r = await get('/api/vouchers/' + encodeURIComponent(no));
      if (r.code === 0 && r.data && r.data.entries) {
        const lines = r.data.entries.map((en) =>
          (en.direction === 'DR' ? '借 ' : '贷 ') + en.accountNo + ' ' + yuan(en.amountCent)
          + ' 元 (' + yuan(en.beforeBalanceCent) + ' → ' + yuan(en.afterBalanceCent) + ')');
        toast('凭证 ' + no + '：' + lines.join(' ｜ '));
      }
    }
  });

  document.querySelectorAll('[data-action]').forEach((btn) => {
    btn.addEventListener('click', () => runAction(btn.dataset.action, btn));
  });
  $('fillOrder').addEventListener('click', () => fillOrder('notifyOrderNo'));
  $('fillRefundOrder').addEventListener('click', () => fillOrder('refundOrderNo'));

  function todayStr() {
    const d = new Date();
    return d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0')
      + '-' + String(d.getDate()).padStart(2, '0');
  }

  async function init() {
    $('reconDate').value = todayStr();
    $('settleDate').value = todayStr();
    renderTabs();
    renderFlow();
    try {
      await Promise.all([refreshDashboard(), refreshTab(), loadEvents()]);
    } catch (err) {
      toast('后端尚未就绪：' + err.message, true);
    }
    setInterval(pollEvents, 1500);
    setInterval(refreshDashboard, 3000);
    setInterval(refreshTab, 6000);
  }

  init();
})();
