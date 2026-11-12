package com.ledgercore.app.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ledgercore.app.dto.ApiCommand;
import com.ledgercore.common.Ids;
import com.ledgercore.common.Money;
import com.ledgercore.domain.Constants;
import com.ledgercore.domain.model.*;
import com.ledgercore.infra.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

/** T+1 结算：按商户汇总当日分账、轧差退款，生成结算凭证（借商户待结算 / 贷银行存款）。 */
@Service
@RequiredArgsConstructor
public class SettlementService {

    private final PaymentOrderMapper orderMapper;
    private final RefundOrderMapper refundMapper;
    private final SplitDetailMapper splitMapper;
    private final SettlementItemMapper settlementMapper;
    private final LedgerService ledger;
    private final EventService events;

    private static String str(String v, String fallback) {
        return v == null || v.isBlank() ? fallback : v;
    }

    @Transactional
    public Map<String, Object> run(ApiCommand cmd) {
        String date = str(cmd.getDate(), LocalDate.now().toString());
        String batchNo = Ids.of("STL");

        Map<String, long[]> byMerchant = new LinkedHashMap<>();
        for (PaymentOrder o : orderMapper.selectList(null)) {
            if (o.getCreatedAt() == null || !date.equals(o.getCreatedAt().toLocalDate().toString())) {
                continue;
            }
            if (Constants.ORDER_FAILED.equals(o.getStatus()) || Constants.ORDER_CLOSED.equals(o.getStatus())) {
                continue;
            }
            long[] acc = byMerchant.computeIfAbsent(o.getMerchantNo(), k -> new long[3]);
            acc[0] += o.getMerchantCent();
            acc[1] += o.getCommissionCent();
        }
        Map<String, PaymentOrder> orderMap = new HashMap<>();
        for (PaymentOrder o : orderMapper.selectList(null)) {
            orderMap.put(o.getOrderNo(), o);
        }
        for (RefundOrder r : refundMapper.selectList(null)) {
            if (r.getCreatedAt() == null || !date.equals(r.getCreatedAt().toLocalDate().toString())) {
                continue;
            }
            PaymentOrder o = orderMap.get(r.getOriginalOrderNo());
            if (o == null) {
                continue;
            }
            long[] acc = byMerchant.computeIfAbsent(o.getMerchantNo(), k -> new long[3]);
            acc[2] += r.getMerchantCent();
        }

        int created = 0;
        int skipped = 0;
        long totalCent = 0L;
        for (Map.Entry<String, long[]> e : byMerchant.entrySet()) {
            String merchant = e.getKey();
            long gross = e.getValue()[0];
            long fee = e.getValue()[1];
            long refund = e.getValue()[2];
            long net = gross - refund;
            if (net <= 0) {
                skipped++;
                continue;
            }
            Long done = settlementMapper.selectCount(new LambdaQueryWrapper<SettlementItem>()
                    .eq(SettlementItem::getSettleDate, date)
                    .eq(SettlementItem::getMerchantNo, merchant)
                    .eq(SettlementItem::getStatus, Constants.SETTLE_SETTLED));
            if (done != null && done > 0) {
                skipped++;
                events.log(Constants.TOPIC_SETTLE, batchNo, "结算幂等跳过 " + merchant, "SKIPPED",
                        "该商户在 " + date + " 已结算，未重复打款");
                continue;
            }

            List<AccountEntry> entries = new ArrayList<>();
            entries.add(LedgerService.dr(Constants.ACC_MERCHANT, net));
            entries.add(LedgerService.cr(Constants.ACC_BANK, net));
            String bizNo = batchNo + ":" + merchant;
            Voucher v = ledger.post(Constants.BIZ_SETTLEMENT, bizNo, "SETTLE:" + bizNo, null,
                    "T+1 结算打款：冲减商户待结算负债，减少银行存款", entries);

            SettlementItem item = new SettlementItem();
            item.setBatchNo(batchNo);
            item.setMerchantNo(merchant);
            item.setSettleDate(date);
            item.setGrossCent(gross);
            item.setRefundCent(refund);
            item.setFeeCent(fee);
            item.setNetCent(net);
            item.setStatus(Constants.SETTLE_SETTLED);
            item.setVoucherNo(v.getVoucherNo());
            settlementMapper.insert(item);
            created++;
            totalCent += net;
        }

        events.log(Constants.TOPIC_SETTLE, batchNo, "T+1 结算完成", "OK",
                "日期 " + date + "｜结算商户 " + created + " 个｜跳过 " + skipped
                        + " 个｜实际打款 " + Money.format(totalCent) + " 元｜凭证均已借贷平衡");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("batchNo", batchNo);
        result.put("date", date);
        result.put("created", created);
        result.put("skipped", skipped);
        result.put("totalCent", totalCent);
        return result;
    }
}
