package com.ledgercore.domain.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RefundOrder {
    private Long id;
    private String refundNo;
    private String originalOrderNo;
    private long amountCent;
    private long merchantCent;
    private long commissionCent;
    private String reversalVoucherNo;
    private String status;
    private LocalDateTime createdAt;
}
