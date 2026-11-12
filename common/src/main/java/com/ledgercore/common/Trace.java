package com.ledgercore.common;

import org.slf4j.MDC;

/** 全链路 traceNo：同时写入 MDC（日志）与 event_log（页面事件流）。 */
public final class Trace {

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private Trace() {
    }

    public static String start() {
        String traceNo = Ids.shortId("TR");
        CURRENT.set(traceNo);
        MDC.put("traceNo", traceNo);
        return traceNo;
    }

    public static String current() {
        String v = CURRENT.get();
        return v == null ? "" : v;
    }

    public static String ensure() {
        String v = CURRENT.get();
        if (v == null) {
            return start();
        }
        return v;
    }

    public static void clear() {
        CURRENT.remove();
        MDC.remove("traceNo");
    }
}
