package com.ledgercore.domain.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class Voucher {
    private Long id;
    private String voucherNo;
    private String bizType;
    private String bizNo;
    private long totalCent;
    private String status;
    /** 冲正凭证指向原凭证，历史分录永不修改 */
    private String originalVoucherNo;
    private String idempotencyKey;
    private String remark;
    private LocalDateTime createdAt;
}
