package com.ledgercore.common;

public class LedgerException extends RuntimeException {
    private final String code;

    public LedgerException(String message) {
        this("LEDGER_ERROR", message);
    }

    public LedgerException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
