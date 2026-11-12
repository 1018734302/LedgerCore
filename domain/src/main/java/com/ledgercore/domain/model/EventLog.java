package com.ledgercore.domain.model;

import lombok.Data;

import java.time.LocalDateTime;

/** 全链路事件流：页面实时可见的操作轨迹，同时承担审计留痕与本地消息表职责。 */
@Data
public class EventLog {
    private Long id;
    private String eventNo;
    private String topic;
    private String bizNo;
    private String action;
    private String result;
    private String detail;
    private String traceNo;
    private String status;
    private int retryCount;
    private LocalDateTime createdAt;
}
