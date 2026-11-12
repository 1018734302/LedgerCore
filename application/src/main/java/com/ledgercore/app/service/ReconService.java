package com.ledgercore.app.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ledgercore.app.dto.ApiCommand;
import com.ledgercore.common.Ids;
import com.ledgercore.common.LedgerException;
import com.ledgercore.common.Money;
import com.ledgercore.domain.Constants;
import com.ledgercore.domain.LedgerRules;
import com.ledgercore.domain.model.*;
import com.ledgercore.infra.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

/**
 * 日终对账：三层核对
 * 1) 全局借贷平衡 + 逐凭证平衡；2) 账户总分核对；3) 渠道账单与内部流水双向核对。
 * 差异自动落差错工单，绝不静默吞掉。
 */
@Service
@RequiredArgsConstructor
public class ReconService {

    private final AccountMapper accountMapper;
    private final AccountEntryMapper entryMapper;
    private final PaymentOrderMapper orderMapper;
    private final ChannelTxnMapper txnMapper;
    private final ChannelBillMapper billMapper;
    private final ReconDiffMapper diffMapper;
    private final EventService events;

    private static String str(String v, String fallback) {
        return v == null || v.isBlank() ? fallback : v;
    }

    @Transactional
    public Map<String, Object> run(ApiCommand cmd) {
        String date = str(cmd.getDate(), LocalDate.now().toString());
        String reconNo = Ids.of("RC");
        int found = 0;
        int checked = 0;

        long dr = entryMapper.sumDr();
        long cr = entryMapper.sumCr();
        if (dr != cr) {
            if (addDiff(reconNo, date, Constants.DIFF_LEDGER, Constants.SCOPE_GLOBAL, "GLOBAL", dr, cr,
                    "全库借方合计与贷方合计不一致")) {
                found++;
            }
        }
        long bad = entryMapper.countUnbalancedVouchers();
        if (bad > 0) {
            if (addDiff(reconNo, date, Constants.DIFF_LEDGER, Constants.SCOPE_GLOBAL, "VOUCHERS",
                    0, bad, "存在 " + bad + " 张借贷不平衡的凭证")) {
                found++;
            }
        }

        for (Account acc : accountMapper.selectList(null)) {
            long signed = entryMapper.sumSignedDelta(acc.getAccountNo());
            long expected = acc.getOpeningBalanceCent() + LedgerRules.sign(acc.getAccountType()) * signed;
            if (expected != acc.getBalanceCent()) {
                if (addDiff(reconNo, date, Constants.DIFF_BALANCE, Constants.SCOPE_ACCOUNT,
                        acc.getAccountNo(), expected, acc.getBalanceCent(),
                        "账户余额 != 期初 + Σ分录净变动")) {
                    found++;
                }
            }
        }

        List<ChannelBill> bills = billMapper.selectByDate(date);
        Map<String, ChannelBill> billMap = new HashMap<>();
        for (ChannelBill b : bills) {
            billMap.put(b.getChannelTxnNo(), b);
        }
        List<PaymentOrder> orders = orderMapper.selectList(null);
        Map<String, PaymentOrder> orderMap = new HashMap<>();
        for (PaymentOrder o : orders) {
            orderMap.put(o.getOrderNo(), o);
        }
        List<ChannelTxn> txns = txnMapper.selectList(null);
        Set<String> matched = new HashSet<>();

        for (ChannelTxn txn : txns) {
            if (txn.getNotifyAt() == null
                    || !date.equals(txn.getNotifyAt().toLocalDate().toString())) {
                continue;
            }
            checked++;
            ChannelBill bill = billMap.get(txn.getChannelTxnNo());
            if (bill == null) {
                if (addDiff(reconNo, date, Constants.DIFF_SHORT, Constants.SCOPE_CHANNEL,
                        txn.getChannelTxnNo(), txn.getAmountCent(), 0,
                        "内部有渠道流水，渠道账单缺失（短款）")) {
                    found++;
                }
                continue;
            }
            matched.add(bill.getChannelTxnNo());
            PaymentOrder o = orderMap.get(txn.getOrderNo());
            long internalNet = o == null ? txn.getAmountCent() : o.getAmountCent() - o.getRefundedCent();
            if (bill.getAmountCent() != internalNet) {
                if (addDiff(reconNo, date, Constants.DIFF_AMOUNT, Constants.SCOPE_CHANNEL,
                        txn.getChannelTxnNo(), internalNet, bill.getAmountCent(),
                        "渠道账单金额与内部净额不符（退款已轧差）")) {
                    found++;
                }
            } else if (!"SUCCESS".equals(bill.getStatus())) {
                if (addDiff(reconNo, date, Constants.DIFF_STATUS, Constants.SCOPE_CHANNEL,
                        txn.getChannelTxnNo(), internalNet, bill.getAmountCent(),
                        "渠道账单状态非 SUCCESS")) {
                    found++;
                }
            }
        }
        for (ChannelBill bill : bills) {
            if (!matched.contains(bill.getChannelTxnNo())) {
                if (addDiff(reconNo, date, Constants.DIFF_LONG, Constants.SCOPE_CHANNEL,
                        bill.getChannelTxnNo(), 0, bill.getAmountCent(),
                        "渠道账单存在，内部无对应流水（长款）")) {
                    found++;
                }
            }
        }

        events.log(Constants.TOPIC_RECON, reconNo, "日终对账完成", found == 0 ? "OK" : "DIFF_FOUND",
                "日期 " + date + "｜借贷 DR=" + Money.format(dr) + " CR=" + Money.format(cr)
                        + "｜账户总分核对 " + accountMapper.selectCount(null) + " 个｜渠道核对 "
                        + checked + " 笔｜新增差异 " + found + " 笔");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("reconNo", reconNo);
        result.put("date", date);
        result.put("drCent", dr);
        result.put("crCent", cr);
        result.put("unbalancedVouchers", bad);
        result.put("channelChecked", checked);
        result.put("diffFound", found);
        result.put("openDiffCount", diffMapper.countOpen());
        return result;
    }

    private boolean addDiff(String reconNo, String date, String type, String scope, String bizNo,
                            long expected, long actual, String remark) {
        Long c = diffMapper.selectCount(new LambdaQueryWrapper<ReconDiff>()
                .eq(ReconDiff::getReconDate, date)
                .eq(ReconDiff::getDiffType, type)
                .eq(ReconDiff::getBizNo, bizNo)
                .ne(ReconDiff::getStatus, Constants.TICKET_RESOLVED)
                .ne(ReconDiff::getStatus, Constants.TICKET_REJECTED));
        if (c != null && c > 0) {
            return false;
        }
        ReconDiff d = new ReconDiff();
        d.setReconNo(reconNo);
        d.setReconDate(date);
        d.setDiffType(type);
        d.setDiffScope(scope);
        d.setBizNo(bizNo);
        d.setExpectedCent(expected);
        d.setActualCent(actual);
        d.setStatus(Constants.TICKET_OPEN);
        d.setRemark(remark + "（期望 " + Money.format(expected) + " / 实际 " + Money.format(actual) + "）");
        diffMapper.insert(d);
        return true;
    }

    @Transactional
    public ReconDiff resolve(ApiCommand cmd) {
        if (cmd.getId() == null) {
            throw new LedgerException("缺少工单 id");
        }
        ReconDiff d = diffMapper.selectById(cmd.getId());
        if (d == null) {
            throw new LedgerException("工单不存在: " + cmd.getId());
        }
        LedgerRules.assertTransit("TICKET", d.getStatus(), Constants.TICKET_RESOLVED);
        d.setStatus(Constants.TICKET_RESOLVED);
        d.setHandler(str(cmd.getHandler(), "OPS_01"));
        d.setRemark(str(cmd.getRemark(), "人工核查后确认并闭环"));
        diffMapper.updateById(d);
        events.log(Constants.TOPIC_RECON, d.getReconNo(), "差错工单闭环 #" + d.getId(), "OK",
                d.getDiffType() + " / " + d.getBizNo() + " 由 " + d.getHandler() + " 处理："
                        + d.getRemark());
        return d;
    }
}
