package com.ledgercore.app.dto;

import lombok.Data;

/** 演示控制台统一入参：所有操作复用一个命令对象，便于前端造数与 Swagger 调试。 */
@Data
public class ApiCommand {
    private String buyerNo;
    private String merchantNo;
    private String amountYuan;
    private String feeRate;
    private String channel;
    private String idempotencyKey;

    private String orderNo;
    /** SUCCESS / FAILED */
    private String result = "SUCCESS";
    /** 同一回调重复送达次数，用于演示幂等 */
    private int duplicate = 1;

    /** 对账 / 结算日期 yyyy-MM-dd */
    private String date;
    /** 异常注入类型 */
    private String type;

    private int count = 100;
    private int limit = 50;
    private long afterId = 0L;

    private Long id;
    private String handler;
    private String remark;
    private String code;
    private String value;
    private Integer enabled;

    private String voucherNo;
}
