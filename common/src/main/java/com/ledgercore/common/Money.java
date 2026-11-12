package com.ledgercore.common;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** 金额工具：内部一律以「分」为单位的 long 存储，比率计算才用 BigDecimal。 */
public final class Money {

    private Money() {
    }

    public static String format(long cent) {
        return BigDecimal.valueOf(cent, 2).toPlainString();
    }

    public static String formatWithSign(long cent) {
        return (cent > 0 ? "+" : "") + format(cent);
    }

    public static long fromYuan(String yuan) {
        return new BigDecimal(yuan).movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    public static long fromYuan(double yuan) {
        return BigDecimal.valueOf(yuan).movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    /** 佣金 = 订单金额 * 费率，四舍五入到分；商户应收 = 总额 - 佣金，保证分账后不丢一分钱。 */
    public static long commission(long totalCent, BigDecimal rate) {
        if (rate == null || rate.signum() < 0 || rate.compareTo(BigDecimal.ONE) >= 0) {
            throw new LedgerException("费率必须是 [0, 1) 区间的小数");
        }
        return BigDecimal.valueOf(totalCent).multiply(rate)
                .setScale(0, RoundingMode.HALF_UP).longValueExact();
    }
}
