package com.ledgercore.domain;

/** 领域常量：状态与类型用字符串常量表达，保持领域层零框架依赖。 */
public final class Constants {

    private Constants() {
    }

    // 账户编号
    public static final String ACC_CHANNEL = "ACC_CHANNEL_CLEARING";
    public static final String ACC_MERCHANT = "ACC_MERCHANT_SETTLE";
    public static final String ACC_COMMISSION = "ACC_PLATFORM_COMMISSION";
    public static final String ACC_BANK = "ACC_BANK_DEPOSIT";
    public static final String ACC_BUYER = "ACC_BUYER_BALANCE";

    // 科目性质（决定借贷方向对余额的影响）
    public static final String NATURE_ASSET = "ASSET";
    public static final String NATURE_LIABILITY = "LIABILITY";
    public static final String NATURE_INCOME = "INCOME";

    // 借贷方向
    public static final String DR = "DR";
    public static final String CR = "CR";

    // 支付订单状态
    public static final String ORDER_INIT = "INIT";
    public static final String ORDER_PAYING = "PAYING";
    public static final String ORDER_SUCCESS = "SUCCESS";
    public static final String ORDER_FAILED = "FAILED";
    public static final String ORDER_CLOSED = "CLOSED";
    public static final String ORDER_PART_REFUNDED = "PART_REFUNDED";
    public static final String ORDER_REFUNDED = "REFUNDED";

    // 凭证业务类型
    public static final String BIZ_PAYMENT = "PAYMENT";
    public static final String BIZ_REFUND = "REFUND";
    public static final String BIZ_SETTLEMENT = "SETTLEMENT";

    // 结算 / 工单状态
    public static final String SETTLE_PENDING = "PENDING";
    public static final String SETTLE_SETTLED = "SETTLED";
    public static final String TICKET_OPEN = "OPEN";
    public static final String TICKET_PROCESSING = "PROCESSING";
    public static final String TICKET_RESOLVED = "RESOLVED";
    public static final String TICKET_REJECTED = "REJECTED";

    // 对账差异类型与范围
    public static final String DIFF_LONG = "LONG";                    // 渠道有、内部无（长款）
    public static final String DIFF_SHORT = "SHORT";                  // 内部有、渠道无（短款）
    public static final String DIFF_AMOUNT = "AMOUNT_MISMATCH";
    public static final String DIFF_STATUS = "STATUS_MISMATCH";
    public static final String DIFF_BALANCE = "BALANCE_MISMATCH";     // 总分核对不平
    public static final String DIFF_LEDGER = "LEDGER_UNBALANCED";     // 借贷不平
    public static final String SCOPE_CHANNEL = "CHANNEL";
    public static final String SCOPE_ACCOUNT = "ACCOUNT";
    public static final String SCOPE_GLOBAL = "GLOBAL";

    // 事件主题
    public static final String TOPIC_PAYMENT = "PAYMENT";
    public static final String TOPIC_LEDGER = "LEDGER";
    public static final String TOPIC_REFUND = "REFUND";
    public static final String TOPIC_SETTLE = "SETTLEMENT";
    public static final String TOPIC_RECON = "RECON";
    public static final String TOPIC_RISK = "RISK";
    public static final String TOPIC_SYSTEM = "SYSTEM";

    // 风控规则编码
    public static final String RULE_MAX_SINGLE = "MAX_SINGLE_CENT";
    public static final String RULE_DAILY_LIMIT = "DAILY_LIMIT_CENT";
    public static final String RULE_BLACKLIST = "MERCHANT_BLACKLIST";
}
