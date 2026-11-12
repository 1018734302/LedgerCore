package com.ledgercore.app.service;

import com.ledgercore.domain.Constants;
import com.ledgercore.infra.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/** 一键清空业务数据、账户回到期初余额，方便反复演示。 */
@Service
@RequiredArgsConstructor
public class ResetService {

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
    private final EventService events;

    @Transactional
    public Map<String, Object> reset() {
        entryMapper.delete(null);
        voucherMapper.delete(null);
        splitMapper.delete(null);
        refundMapper.delete(null);
        settlementMapper.delete(null);
        diffMapper.delete(null);
        billMapper.delete(null);
        txnMapper.delete(null);
        orderMapper.delete(null);
        eventMapper.delete(null);
        accountMapper.resetBalances();
        events.log(Constants.TOPIC_SYSTEM, "RESET", "重置演示环境", "OK",
                "业务数据已清空，5 个账户余额回到期初（银行存款期初 100 万元）");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("reset", true);
        return m;
    }
}
