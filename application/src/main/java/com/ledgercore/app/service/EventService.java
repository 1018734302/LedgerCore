package com.ledgercore.app.service;

import com.ledgercore.common.Ids;
import com.ledgercore.common.Trace;
import com.ledgercore.domain.model.EventLog;
import com.ledgercore.infra.mapper.EventLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 全链路事件流。使用 REQUIRES_NEW：即便主事务回滚，失败/拒绝的轨迹也必须留痕，
 * 这正是「查得清、说得明」的审计要求，也是页面实时数据流的来源。
 */
@Service
@RequiredArgsConstructor
public class EventService {

    private final EventLogMapper mapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(String topic, String bizNo, String action, String result, String detail) {
        EventLog e = new EventLog();
        e.setEventNo(Ids.of("EV"));
        e.setTopic(topic);
        e.setBizNo(bizNo);
        e.setAction(action);
        e.setResult(result);
        e.setDetail(detail == null ? "" : (detail.length() > 900 ? detail.substring(0, 900) : detail));
        e.setTraceNo(Trace.ensure());
        e.setStatus("DONE");
        mapper.insert(e);
    }

    public List<EventLog> recent(int limit) {
        return mapper.selectRecent(clamp(limit));
    }

    public List<EventLog> after(long afterId, int limit) {
        return mapper.selectAfter(afterId, clamp(limit));
    }

    private int clamp(int limit) {
        return Math.max(1, Math.min(limit, 200));
    }
}
