package com.ledgercore.domain.model;

import lombok.Data;

import java.time.LocalDateTime;

/** 对账差异与差错工单合一：OPEN -> PROCESSING -> RESOLVED / REJECTED。 */
@Data
public class ReconDiff {
    private Long id;
    private String reconNo;
    private String reconDate;
    private String diffType;
    private String diffScope;
    private String bizNo;
    private long expectedCent;
    private long actualCent;
    private String status;
    private String handler;
    private String remark;
    private LocalDateTime createdAt;
}
