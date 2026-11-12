package com.ledgercore.domain;

import com.ledgercore.common.LedgerException;
import com.ledgercore.common.Money;
import com.ledgercore.domain.model.AccountEntry;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LedgerRulesTest {

    private static AccountEntry entry(String direction, long cent) {
        AccountEntry e = new AccountEntry();
        e.setDirection(direction);
        e.setAmountCent(cent);
        return e;
    }

    @Test
    void balancedVoucherPasses() {
        assertDoesNotThrow(() -> LedgerRules.assertBalanced(List.of(
                entry(Constants.DR, 10000),
                entry(Constants.CR, 9900),
                entry(Constants.CR, 100))));
    }

    @Test
    void unbalancedVoucherRejected() {
        assertThrows(LedgerException.class, () -> LedgerRules.assertBalanced(List.of(
                entry(Constants.DR, 10000), entry(Constants.CR, 9900))));
    }

    @Test
    void singleEntryAndNonPositiveRejected() {
        assertThrows(LedgerException.class, () -> LedgerRules.assertBalanced(List.of(entry(Constants.DR, 100))));
        assertThrows(LedgerException.class, () -> LedgerRules.assertBalanced(List.of(
                entry(Constants.DR, 0), entry(Constants.CR, 0))));
    }

    @Test
    void directionDependsOnAccountNature() {
        assertEquals(100L, LedgerRules.delta(Constants.NATURE_ASSET, Constants.DR, 100));
        assertEquals(-100L, LedgerRules.delta(Constants.NATURE_ASSET, Constants.CR, 100));
        assertEquals(100L, LedgerRules.delta(Constants.NATURE_LIABILITY, Constants.CR, 100));
        assertEquals(-100L, LedgerRules.delta(Constants.NATURE_LIABILITY, Constants.DR, 100));
    }

    @Test
    void splitNeverLosesACent() {
        for (long total : new long[]{1L, 3L, 333L, 10000L, 99999999L}) {
            long[] parts = LedgerRules.split(total, new BigDecimal("0.0100"));
            assertEquals(total, parts[0] + parts[1], "分账后总额必须守恒");
        }
        long[] p = LedgerRules.split(10000, new BigDecimal("0.0100"));
        assertEquals(9900L, p[0]);
        assertEquals(100L, p[1]);
    }

    @Test
    void stateMachineOnlyMovesForward() {
        assertTrue(LedgerRules.canTransit("ORDER", Constants.ORDER_INIT, Constants.ORDER_SUCCESS));
        assertFalse(LedgerRules.canTransit("ORDER", Constants.ORDER_SUCCESS, Constants.ORDER_INIT));
        assertFalse(LedgerRules.canTransit("ORDER", Constants.ORDER_SUCCESS, Constants.ORDER_FAILED));
    }

    @Test
    void moneyIsCentBased() {
        assertEquals(10000L, Money.fromYuan("100.00"));
        assertEquals("100.00", Money.format(10000L));
        assertEquals("0.01", Money.format(1L));
    }
}
