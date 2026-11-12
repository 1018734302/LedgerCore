package com.ledgercore.domain.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class Account {
    private Long id;
    private String accountNo;
    private String accountName;
    /** ASSET / LIABILITY / INCOME，决定借贷方向 */
    private String accountType;
    private long balanceCent;
    private long openingBalanceCent;
    private long frozenCent;
    private String status;
    private long version;
    private LocalDateTime createdAt;
}
