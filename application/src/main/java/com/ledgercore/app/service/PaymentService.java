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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 支付链路。回调入账事务一次覆盖：订单状态 CAS + 渠道流水 + 渠道账单 + 凭证 + 分录
 * + 账户余额 + 分账明细，不留给「半条分录」任何机会。
 */
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentOrderMapper orderMapper;
    private final ChannelTxnMapper txnMapper;
    private final ChannelBillMapper billMapper;
    private final SplitDetailMapper splitMapper;
    private final RefundOrderMapper refundMapper;
    private final VoucherMapper voucherMapper;
    private final LedgerService ledger;
    private final RiskService risk;
    private final EventService events;

    private static String str(String v, String fallback) {
        return v == null || v.isBlank() ? fallback : v;
    }

    public PaymentOrder get(String orderNo) {
        if (orderNo == null || orderNo.isBlank()) {
            return null;
        }
        return orderMapper.selectOne(new LambdaQueryWrapper<PaymentOrder>()
                .eq(PaymentOrder::getOrderNo, orderNo));
    }

    @Transactional
    public PaymentOrder create(ApiCommand cmd) {
        return create(cmd, false);
    }

    @Transactional
    public PaymentOrder create(ApiCommand cmd, boolean silent) {
        long amountCent = Money.fromYuan(str(cmd.getAmountYuan(), "100.00"));
        if (amountCent <= 0) {
            throw new LedgerException("支付金额必须大于 0");
        }
        BigDecimal rate = new BigDecimal(str(cmd.getFeeRate(), "0.0100"));
        String buyer = str(cmd.getBuyerNo(), "BUYER_001");
        String merchant = str(cmd.getMerchantNo(), "MERCHANT_A");
        String channel = str(cmd.getChannel(), "WECHAT");
        String idem = str(cmd.getIdempotencyKey(), Ids.of("IDEM"));

        risk.check(merchant, amountCent);

        LambdaQueryWrapper<PaymentOrder> idemQuery = new LambdaQueryWrapper<PaymentOrder>()
                .eq(PaymentOrder::getIdempotencyKey, idem);
        Long exists = orderMapper.selectCount(idemQuery);
        if (exists != null && exists > 0) {
            PaymentOrder existing = orderMapper.selectOne(idemQuery);
            events.log(Constants.TOPIC_PAYMENT, existing.getOrderNo(), "创建订单幂等命中", "SKIPPED",
                    "幂等键 " + idem + " 已存在，未重复创建");
            return existing;
        }

        long[] parts = LedgerRules.split(amountCent, rate);
        PaymentOrder o = new PaymentOrder();
        o.setOrderNo(Ids.of("ORD"));
        o.setBuyerNo(buyer);
        o.setMerchantNo(merchant);
        o.setAmountCent(amountCent);
        o.setFeeRate(rate);
        o.setMerchantCent(parts[0]);
        o.setCommissionCent(parts[1]);
        o.setChannel(channel);
        o.setStatus(Constants.ORDER_INIT);
        o.setRefundedCent(0L);
        o.setIdempotencyKey(idem);
        o.setVersion(0L);
        orderMapper.insert(o);

        if (!silent) {
            events.log(Constants.TOPIC_PAYMENT, o.getOrderNo(), "创建支付订单", "OK",
                    "买家 " + buyer + " 商户 " + merchant + " 金额 " + Money.format(amountCent)
                            + " 元，费率 " + rate.toPlainString() + "，分账 商户 "
                            + Money.format(parts[0]) + " / 佣金 " + Money.format(parts[1]) + " 元");
        }
        return o;
    }

    @Transactional
    public PaymentOrder notify(ApiCommand cmd) {
        return notify(cmd, false);
    }

    @Transactional
    public PaymentOrder notify(ApiCommand cmd, boolean silent) {
        int times = Math.max(1, Math.min(cmd.getDuplicate() <= 0 ? 1 : cmd.getDuplicate(), 5));
        PaymentOrder last = null;
        for (int i = 1; i <= times; i++) {
            last = doNotify(cmd, i, times, silent);
        }
        return last;
    }

    private PaymentOrder doNotify(ApiCommand cmd, int seq, int total, boolean silent) {
        PaymentOrder o = get(cmd.getOrderNo());
        if (o == null) {
            events.log(Constants.TOPIC_PAYMENT, cmd.getOrderNo(), "渠道回调", "REJECTED", "订单不存在");
            throw new LedgerException("订单不存在: " + cmd.getOrderNo());
        }
        String result = str(cmd.getResult(), "SUCCESS").toUpperCase();

        if (Constants.ORDER_SUCCESS.equals(o.getStatus())
                || Constants.ORDER_PART_REFUNDED.equals(o.getStatus())
                || Constants.ORDER_REFUNDED.equals(o.getStatus())) {
            if (!"FAILED".equals(result) && !silent) {
                events.log(Constants.TOPIC_PAYMENT, o.getOrderNo(),
                        "重复回调 第 " + seq + "/" + total + " 次", "IDEMPOTENT",
                        "订单已 " + o.getStatus() + "，状态机拒绝重复入账：未新增凭证、未新增分录、余额不变");
            }
            if ("FAILED".equals(result)) {
                events.log(Constants.TOPIC_PAYMENT, o.getOrderNo(), "迟到/乱序的失败回调", "REJECTED",
                        "状态机禁止 " + o.getStatus() + " -> FAILED，拒绝覆盖已成功状态");
                throw new LedgerException("状态机拒绝乱序回调：" + o.getStatus() + " -> FAILED");
            }
            return o;
        }
        if (!Constants.ORDER_INIT.equals(o.getStatus()) && !Constants.ORDER_PAYING.equals(o.getStatus())) {
            throw new LedgerException("订单当前状态不可回调: " + o.getStatus());
        }

        if ("FAILED".equals(result)) {
            LedgerRules.assertTransit("ORDER", o.getStatus(), Constants.ORDER_FAILED);
            orderMapper.casTransit(o.getOrderNo(), o.getStatus(), Constants.ORDER_FAILED, "", o.getVersion());
            events.log(Constants.TOPIC_PAYMENT, o.getOrderNo(), "渠道回调 FAILED", "OK", "订单关闭，未产生任何分录");
            return get(o.getOrderNo());
        }

        LedgerRules.assertTransit("ORDER", o.getStatus(), Constants.ORDER_SUCCESS);
        String txnNo = Ids.of("CHTXN");
        int cas = orderMapper.casTransit(o.getOrderNo(), o.getStatus(),
                Constants.ORDER_SUCCESS, txnNo, o.getVersion());
        if (cas != 1) {
            throw new LedgerException("并发回调冲突，订单版本已变化");
        }

        ChannelTxn txn = new ChannelTxn();
        txn.setChannelTxnNo(txnNo);
        txn.setChannel(o.getChannel());
        txn.setOrderNo(o.getOrderNo());
        txn.setAmountCent(o.getAmountCent());
        txn.setStatus("SUCCESS");
        txn.setNotifyAt(LocalDateTime.now());
        txnMapper.insert(txn);

        ChannelBill bill = new ChannelBill();
        bill.setBillDate(LocalDate.now().toString());
        bill.setChannelTxnNo(txnNo);
        bill.setChannel(o.getChannel());
        bill.setAmountCent(o.getAmountCent());
        bill.setStatus("SUCCESS");
        bill.setTampered(0);
        billMapper.insert(bill);

        List<AccountEntry> entries = new ArrayList<>();
        entries.add(LedgerService.dr(Constants.ACC_CHANNEL, o.getAmountCent()));
        if (o.getMerchantCent() > 0) {
            entries.add(LedgerService.cr(Constants.ACC_MERCHANT, o.getMerchantCent()));
        }
        if (o.getCommissionCent() > 0) {
            entries.add(LedgerService.cr(Constants.ACC_COMMISSION, o.getCommissionCent()));
        }
        Voucher v = ledger.post(Constants.BIZ_PAYMENT, o.getOrderNo(),
                "PAY:" + o.getOrderNo(), null,
                "支付入账：借渠道待清算，贷商户待结算与平台佣金", entries);

        insertSplit(o, Constants.ACC_MERCHANT, o.getMerchantCent(), v.getVoucherNo());
        insertSplit(o, Constants.ACC_COMMISSION, o.getCommissionCent(), v.getVoucherNo());

        if (!silent) {
            events.log(Constants.TOPIC_PAYMENT, o.getOrderNo(), "渠道回调 SUCCESS", "OK",
                    "渠道流水 " + txnNo + " 已生成渠道账单；凭证 " + v.getVoucherNo()
                            + " 记账完成，分账 商户 " + Money.format(o.getMerchantCent())
                            + " 元 / 佣金 " + Money.format(o.getCommissionCent()) + " 元");
        }
        return get(o.getOrderNo());
    }

    private void insertSplit(PaymentOrder o, String accountNo, long cent, String voucherNo) {
        SplitDetail sd = new SplitDetail();
        sd.setOrderNo(o.getOrderNo());
        sd.setMerchantNo(o.getMerchantNo());
        sd.setAccountNo(accountNo);
        sd.setAmountCent(cent);
        sd.setVoucherNo(voucherNo);
        splitMapper.insert(sd);
    }

    /** 退款：不修改历史分录，生成借贷互换的冲正凭证，并同步调整渠道侧账单。 */
    @Transactional
    public RefundOrder refund(ApiCommand cmd) {
        PaymentOrder o = get(cmd.getOrderNo());
        if (o == null) {
            throw new LedgerException("订单不存在: " + cmd.getOrderNo());
        }
        if (!Constants.ORDER_SUCCESS.equals(o.getStatus())
                && !Constants.ORDER_PART_REFUNDED.equals(o.getStatus())) {
            throw new LedgerException("只有已支付订单可退款，当前状态: " + o.getStatus());
        }
        long amountCent = Money.fromYuan(str(cmd.getAmountYuan(), "10.00"));
        long remaining = o.getAmountCent() - o.getRefundedCent();
        if (amountCent <= 0 || amountCent > remaining) {
            throw new LedgerException("退款金额非法，可退 " + Money.format(remaining) + " 元");
        }
        long commissionCent = Money.commission(amountCent, o.getFeeRate());
        long merchantCent = amountCent - commissionCent;
        String target = amountCent == remaining ? Constants.ORDER_REFUNDED : Constants.ORDER_PART_REFUNDED;
        int cas = orderMapper.casRefund(o.getOrderNo(), target, amountCent, o.getVersion());
        if (cas != 1) {
            throw new LedgerException("并发退款冲突或超出可退金额");
        }

        Voucher original = voucherMapper.selectOne(new LambdaQueryWrapper<Voucher>()
                .eq(Voucher::getBizNo, o.getOrderNo())
                .eq(Voucher::getBizType, Constants.BIZ_PAYMENT));

        List<AccountEntry> entries = new ArrayList<>();
        entries.add(LedgerService.cr(Constants.ACC_CHANNEL, amountCent));
        if (merchantCent > 0) {
            entries.add(LedgerService.dr(Constants.ACC_MERCHANT, merchantCent));
        }
        if (commissionCent > 0) {
            entries.add(LedgerService.dr(Constants.ACC_COMMISSION, commissionCent));
        }

        String refundNo = Ids.of("RFD");
        Voucher reversal = ledger.post(Constants.BIZ_REFUND, refundNo,
                "REFUND:" + refundNo, original == null ? null : original.getVoucherNo(),
                "退款冲正：原凭证 " + (original == null ? "-" : original.getVoucherNo()) + " 的反向分录", entries);

        ChannelBill bill = billMapper.selectOne(new LambdaQueryWrapper<ChannelBill>()
                .eq(ChannelBill::getChannelTxnNo, o.getChannelTxnNo()));
        if (bill != null) {
            bill.setAmountCent(bill.getAmountCent() - amountCent);
            billMapper.updateById(bill);
        }

        RefundOrder r = new RefundOrder();
        r.setRefundNo(refundNo);
        r.setOriginalOrderNo(o.getOrderNo());
        r.setAmountCent(amountCent);
        r.setMerchantCent(merchantCent);
        r.setCommissionCent(commissionCent);
        r.setReversalVoucherNo(reversal.getVoucherNo());
        r.setStatus("SUCCESS");
        refundMapper.insert(r);

        events.log(Constants.TOPIC_REFUND, o.getOrderNo(), "退款冲正 " + refundNo, "OK",
                "退款 " + Money.format(amountCent) + " 元，冲减商户 " + Money.format(merchantCent)
                        + " / 佣金 " + Money.format(commissionCent) + " 元，冲正凭证 "
                        + reversal.getVoucherNo() + "；历史分录未改动");
        return r;
    }
}
