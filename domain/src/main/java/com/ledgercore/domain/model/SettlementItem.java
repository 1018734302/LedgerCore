package com.ledgercore.domain.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SettlementItem {
    private Long id;
    private String batchNo;
    private String merchantNo;
    private String settleDate;
    private long grossCent;
    private long refundCent;
    private long feeCent;
    private long netCent;
    private String status;
    private String voucherNo;
    private LocalDateTime createdAt;
}
