package com.ledgercore.app.service;

import com.ledgercore.app.dto.ApiCommand;
import com.ledgercore.common.Ids;
import com.ledgercore.common.LedgerException;
import com.ledgercore.common.Money;
import com.ledgercore.domain.Constants;
import com.ledgercore.domain.model.ChannelBill;
import com.ledgercore.domain.model.PaymentOrder;
import com.ledgercore.infra.mapper.ChannelBillMapper;
import com.ledgercore.infra.mapper.PaymentOrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/** 造数与异常注入：让前端可以手动制造真实数据与真实故障。 */
@Service
@RequiredArgsConstructor
public class SeedService {

    private static final String[] MERCHANTS = {"MERCHANT_A", "MERCHANT_B", "MERCHANT_C"};

    private final PaymentService payment;
    private final PaymentOrderMapper orderMapper;
    private final ChannelBillMapper billMapper;
    private final EventService events;

    public Map<String, Object> seed(ApiCommand cmd) {
        int count = Math.max(1, Math.min(cmd.getCount() <= 0 ? 50 : cmd.getCount(), 2000));
        long start = System.currentTimeMillis();
        int ok = 0;
        int rejected = 0;
        for (int i = 0; i < count; i++) {
            ApiCommand create = new ApiCommand();
            create.setMerchantNo(MERCHANTS[i % MERCHANTS.length]);
            create.setBuyerNo(String.format(Locale.ROOT, "BUYER_%03d", i % 50));
            create.setAmountYuan(String.format(Locale.ROOT, "%.2f",
                    10 + ThreadLocalRandom.current().nextDouble(490)));
            create.setFeeRate("0.0100");
            create.setChannel(i % 2 == 0 ? "WECHAT" : "ALIPAY");
            try {
                PaymentOrder o = payment.create(create, true);
                ApiCommand notify = new ApiCommand();
                notify.setOrderNo(o.getOrderNo());
                notify.setResult("SUCCESS");
                notify.setDuplicate(1);
                payment.notify(notify, true);
                ok++;
            } catch (RuntimeException ex) {
                rejected++;
            }
        }
        long ms = System.currentTimeMillis() - start;
        events.log(Constants.TOPIC_SYSTEM, "SEED", "批量造数", "OK",
                "生成 " + ok + " 笔支付（每笔均走完整记账链路）、风控拒绝 " + rejected
                        + " 笔，耗时 " + ms + " ms");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("requested", count);
        result.put("created", ok);
        result.put("rejected", rejected);
        result.put("elapsedMs", ms);
        return result;
    }

    public Map<String, Object> inject(ApiCommand cmd) {
        String type = cmd.getType() == null ? "" : cmd.getType().toUpperCase();
        String detail;
        switch (type) {
            case "BILL_AMOUNT_MISMATCH" -> {
                ChannelBill b = latestBill();
                b.setAmountCent(b.getAmountCent() + 100);
                b.setTampered(1);
                billMapper.updateById(b);
                detail = "渠道账单 " + b.getChannelTxnNo() + " 金额被人为加 1.00 元，"
                        + "下次对账应产生 AMOUNT_MISMATCH";
            }
            case "MISSING_BILL" -> {
                ChannelBill b = latestBill();
                billMapper.deleteById(b.getId());
                detail = "渠道账单 " + b.getChannelTxnNo() + " 被删除，下次对账应产生 SHORT 短款";
            }
            case "EXTRA_BILL" -> {
                ChannelBill fake = new ChannelBill();
                fake.setBillDate(LocalDate.now().toString());
                fake.setChannelTxnNo("FAKE-" + Ids.shortId(""));
                fake.setChannel("WECHAT");
                fake.setAmountCent(8888);
                fake.setStatus("SUCCESS");
                fake.setTampered(1);
                billMapper.insert(fake);
                detail = "插入一条内部无对应流水的渠道账单 " + fake.getChannelTxnNo()
                        + "，下次对账应产生 LONG 长款";
            }
            case "DUPLICATE_NOTIFY" -> {
                PaymentOrder o = latestSuccessOrder();
                ApiCommand n = new ApiCommand();
                n.setOrderNo(o.getOrderNo());
                n.setResult("SUCCESS");
                n.setDuplicate(3);
                payment.notify(n);
                detail = "订单 " + o.getOrderNo() + " 连续送达 3 次回调，"
                        + "应只有第一次记账，后两次被幂等拦截";
            }
            case "LATE_FAILURE" -> {
                PaymentOrder o = latestSuccessOrder();
                ApiCommand n = new ApiCommand();
                n.setOrderNo(o.getOrderNo());
                n.setResult("FAILED");
                n.setDuplicate(1);
                try {
                    payment.notify(n);
                    detail = "迟到失败回调未被拦截（异常）";
                } catch (LedgerException ex) {
                    detail = "订单 " + o.getOrderNo() + " 已 SUCCESS，迟到的 FAILED 回调被状态机拒绝："
                            + ex.getMessage() + "；订单状态与余额均未改变";
                }
            }
            default -> throw new LedgerException("未知异常类型: " + type
                    + "，可用 BILL_AMOUNT_MISMATCH / MISSING_BILL / EXTRA_BILL / DUPLICATE_NOTIFY / LATE_FAILURE");
        }
        events.log(Constants.TOPIC_SYSTEM, "ANOMALY", "注入异常 " + type, "OK", detail);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", type);
        result.put("detail", detail);
        return result;
    }

    private ChannelBill latestBill() {
        List<ChannelBill> list = billMapper.selectRecent(1);
        if (list.isEmpty()) {
            throw new LedgerException("还没有渠道账单，请先创建并支付一笔订单");
        }
        return list.get(0);
    }

    private PaymentOrder latestSuccessOrder() {
        List<PaymentOrder> list = orderMapper.selectRecent(20);
        for (PaymentOrder o : list) {
            if (Constants.ORDER_SUCCESS.equals(o.getStatus())
                    || Constants.ORDER_PART_REFUNDED.equals(o.getStatus())) {
                return o;
            }
        }
        throw new LedgerException("还没有已支付订单，请先创建并支付一笔订单");
    }
}
