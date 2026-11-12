package com.ledgercore.domain.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RiskRule {
    private Long id;
    private String ruleCode;
    private String ruleName;
    private String ruleValue;
    private int enabled;
    private String remark;
    private LocalDateTime updatedAt;
}
