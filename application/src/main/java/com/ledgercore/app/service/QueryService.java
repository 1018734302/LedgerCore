package com.ledgercore.app.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ledgercore.domain.model.*;
import com.ledgercore.infra.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 只读查询，供控制台各面板与轮询使用。 */
@Service
@RequiredArgsConstructor
public class QueryService {

    private final AccountMapper accountMapper;
    private final VoucherMapper voucherMapper;
    private final AccountEntryMapper entryMapper;
    private final PaymentOrderMapper orderMapper;
    private final ChannelTxnMapper txnMapper;
    private final ChannelBillMapper billMapper;
    private final SplitDetailMapper splitMapper;
    private final RefundOrderMapper refundMapper;
    private final SettlementItemMapper settlementMapper;
    private final ReconDiffMapper diffMapper;
    private final EventLogMapper eventMapper;
    private final RiskRuleMapper ruleMapper;

    private int clamp(int limit) {
        return Math.max(1, Math.min(limit, 200));
    }

    public Map<String, Object> summary() {
        Map<String, Object> m = new LinkedHashMap<>();
        long dr = entryMapper.sumDr();
        long cr = entryMapper.sumCr();
        long bad = entryMapper.countUnbalancedVouchers();
        m.put("accounts", accountMapper.selectList(null));
        m.put("orderCount", orderMapper.selectCount(null));
        m.put("voucherCount", voucherMapper.selectCount(null));
        m.put("entryCount", entryMapper.selectCount(null));
        m.put("eventCount", eventMapper.selectCount(null));
        m.put("openDiffCount", diffMapper.countOpen());
        m.put("settledCount", settlementMapper.selectCount(null));
        m.put("drCent", dr);
        m.put("crCent", cr);
        m.put("unbalancedVouchers", bad);
        m.put("balanced", dr == cr && bad == 0);
        return m;
    }

    public List<Account> accounts() {
        return accountMapper.selectList(null);
    }

    public List<PaymentOrder> orders(int limit) {
        return orderMapper.selectRecent(clamp(limit));
    }

    public List<AccountEntry> entries(int limit) {
        return entryMapper.selectRecent(clamp(limit));
    }

    public List<Voucher> vouchers(int limit) {
        return voucherMapper.selectList(new LambdaQueryWrapper<Voucher>()
                .orderByDesc(Voucher::getId).last("LIMIT " + clamp(limit)));
    }

    public List<ChannelTxn> channelTxns(int limit) {
        return txnMapper.selectList(new LambdaQueryWrapper<ChannelTxn>()
                .orderByDesc(ChannelTxn::getId).last("LIMIT " + clamp(limit)));
    }

    public List<ChannelBill> bills(int limit) {
        return billMapper.selectRecent(clamp(limit));
    }

    public List<SplitDetail> splits(int limit) {
        return splitMapper.selectList(new LambdaQueryWrapper<SplitDetail>()
                .orderByDesc(SplitDetail::getId).last("LIMIT " + clamp(limit)));
    }

    public List<RefundOrder> refunds(int limit) {
        return refundMapper.selectList(new LambdaQueryWrapper<RefundOrder>()
                .orderByDesc(RefundOrder::getId).last("LIMIT " + clamp(limit)));
    }

    public List<SettlementItem> settlements(int limit) {
        return settlementMapper.selectRecent(clamp(limit));
    }

    public List<ReconDiff> diffs(int limit) {
        return diffMapper.selectRecent(clamp(limit));
    }

    public List<EventLog> events(int limit) {
        return eventMapper.selectRecent(clamp(limit));
    }

    public List<EventLog> eventsAfter(long afterId, int limit) {
        return eventMapper.selectAfter(afterId, clamp(limit));
    }

    public List<RiskRule> rules() {
        return ruleMapper.selectList(null);
    }

    public Map<String, Object> voucherDetail(String voucherNo) {
        Map<String, Object> m = new LinkedHashMap<>();
        Voucher v = voucherMapper.selectOne(new LambdaQueryWrapper<Voucher>()
                .eq(Voucher::getVoucherNo, voucherNo));
        m.put("voucher", v);
        m.put("entries", v == null ? List.of() : entryMapper.selectByVoucher(voucherNo));
        return m;
    }
}
