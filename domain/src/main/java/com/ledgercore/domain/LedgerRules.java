package com.ledgercore.domain;

import com.ledgercore.common.LedgerException;
import com.ledgercore.common.Money;
import com.ledgercore.domain.model.AccountEntry;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 记账内核规则，纯 Java、不依赖任何框架，可脱离数据库做单元测试。
 *
 * 科目性质决定借贷方向：资产类 DR 增 / CR 减；负债与收入类 CR 增 / DR 减。
 * 余额变化 delta = sign(nature) * (DR ? +amount : -amount)。
 */
public final class LedgerRules {

    private static final Map<String, Set<String>> FLOW = new HashMap<>();

    static {
        flow("ORDER", Constants.ORDER_INIT, Constants.ORDER_PAYING, Constants.ORDER_SUCCESS,
                Constants.ORDER_FAILED, Constants.ORDER_CLOSED);
        flow("ORDER", Constants.ORDER_PAYING, Constants.ORDER_SUCCESS, Constants.ORDER_FAILED);
        flow("ORDER", Constants.ORDER_SUCCESS, Constants.ORDER_PART_REFUNDED,
                Constants.ORDER_REFUNDED, Constants.ORDER_CLOSED);
        flow("ORDER", Constants.ORDER_PART_REFUNDED, Constants.ORDER_PART_REFUNDED, Constants.ORDER_REFUNDED);
        flow("SETTLE", Constants.SETTLE_PENDING, Constants.SETTLE_SETTLED);
        flow("TICKET", Constants.TICKET_OPEN, Constants.TICKET_PROCESSING,
                Constants.TICKET_RESOLVED, Constants.TICKET_REJECTED);
        flow("TICKET", Constants.TICKET_PROCESSING, Constants.TICKET_RESOLVED, Constants.TICKET_REJECTED);
    }

    private LedgerRules() {
    }

    private static void flow(String scope, String from, String... to) {
        FLOW.put(scope + ":" + from, Set.of(to));
    }

    public static long sign(String nature) {
        return Constants.NATURE_ASSET.equals(nature) ? 1L : -1L;
    }

    public static long delta(String nature, String direction, long amount) {
        if (!Constants.DR.equals(direction) && !Constants.CR.equals(direction)) {
            throw new LedgerException("非法借贷方向: " + direction);
        }
        return sign(nature) * (Constants.DR.equals(direction) ? amount : -amount);
    }

    /** 提交凭证前的硬约束：至少两条分录、方向合法、金额为正、借方合计等于贷方合计。 */
    public static void assertBalanced(List<AccountEntry> entries) {
        if (entries == null || entries.size() < 2) {
            throw new LedgerException("凭证至少需要两条分录");
        }
        long dr = 0L;
        long cr = 0L;
        for (AccountEntry e : entries) {
            if (e.getAmountCent() <= 0) {
                throw new LedgerException("分录金额必须为正数");
            }
            if (Constants.DR.equals(e.getDirection())) {
                dr += e.getAmountCent();
            } else if (Constants.CR.equals(e.getDirection())) {
                cr += e.getAmountCent();
            } else {
                throw new LedgerException("非法借贷方向: " + e.getDirection());
            }
        }
        if (dr != cr || dr == 0) {
            throw new LedgerException("借贷不平衡: DR=" + Money.format(dr) + " CR=" + Money.format(cr));
        }
    }

    public static boolean canTransit(String scope, String from, String to) {
        Set<String> next = FLOW.get(scope + ":" + from);
        return next != null && next.contains(to);
    }

    public static void assertTransit(String scope, String from, String to) {
        if (!canTransit(scope, from, to)) {
            throw new LedgerException("非法状态流转: " + scope + " " + from + " -> " + to);
        }
    }

    /** 分账：佣金四舍五入到分，商户应收取差额，保证两者之和恒等于订单金额。 */
    public static long[] split(long totalCent, BigDecimal feeRate) {
        long commission = Money.commission(totalCent, feeRate);
        if (commission > totalCent) {
            throw new LedgerException("佣金不能超过订单金额");
        }
        return new long[]{totalCent - commission, commission};
    }
}
