package com.ledgercore.domain.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class PaymentOrder {
    private Long id;
    private String orderNo;
    private String buyerNo;
    private String merchantNo;
    private long amountCent;
    private BigDecimal feeRate;
    private long merchantCent;
    private long commissionCent;
    private String channel;
    private String status;
    private String channelTxnNo;
    private long refundedCent;
    private String idempotencyKey;
    private long version;
    private LocalDateTime createdAt;
}
