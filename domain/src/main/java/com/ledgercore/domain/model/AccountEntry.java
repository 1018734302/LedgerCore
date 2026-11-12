package com.ledgercore.domain.model;

import lombok.Data;

import java.time.LocalDateTime;

/** 分录：只追加，禁止 UPDATE / DELETE；余额快照用于逐笔回溯与总分核对。 */
@Data
public class AccountEntry {
    private Long id;
    private String voucherNo;
    private String accountNo;
    private String direction;
    private long amountCent;
    private String bizNo;
    private String idempotencyKey;
    private long beforeBalanceCent;
    private long afterBalanceCent;
    private LocalDateTime createdAt;
}
