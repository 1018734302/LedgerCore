package com.ledgercore.domain.model;

import lombok.Data;

import java.time.LocalDateTime;

/** 渠道 Mock 的扣款流水，代表平台侧记录的「内部真相」。 */
@Data
public class ChannelTxn {
    private Long id;
    private String channelTxnNo;
    private String channel;
    private String orderNo;
    private long amountCent;
    private String status;
    private LocalDateTime notifyAt;
    private LocalDateTime createdAt;
}
