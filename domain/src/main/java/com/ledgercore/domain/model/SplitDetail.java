package com.ledgercore.domain.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SplitDetail {
    private Long id;
    private String orderNo;
    private String merchantNo;
    private String accountNo;
    private long amountCent;
    private String voucherNo;
    private LocalDateTime createdAt;
}
