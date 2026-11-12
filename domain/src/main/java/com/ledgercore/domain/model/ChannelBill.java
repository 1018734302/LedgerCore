package com.ledgercore.domain.model;

import lombok.Data;

import java.time.LocalDateTime;

/** 渠道日终对账文件解析结果，代表「渠道侧真相」；tampered 用于演示人为制造差异。 */
@Data
public class ChannelBill {
    private Long id;
    private String billDate;
    private String channelTxnNo;
    private String channel;
    private long amountCent;
    private String status;
    private int tampered;
    private LocalDateTime createdAt;
}
