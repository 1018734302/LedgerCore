package com.ledgercore.app.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ledgercore.common.LedgerException;
import com.ledgercore.common.Money;
import com.ledgercore.domain.Constants;
import com.ledgercore.domain.model.RiskRule;
import com.ledgercore.infra.mapper.PaymentOrderMapper;
import com.ledgercore.infra.mapper.RiskRuleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/** 轻量风控：规则存库、可热更新，命中即拦截并留痕。 */
@Service
@RequiredArgsConstructor
public class RiskService {

    private final RiskRuleMapper ruleMapper;
    private final PaymentOrderMapper orderMapper;
    private final EventService events;

    public List<RiskRule> rules() {
        return ruleMapper.selectList(null);
    }

    private RiskRule find(String code) {
        return ruleMapper.selectOne(new LambdaQueryWrapper<RiskRule>().eq(RiskRule::getRuleCode, code));
    }

    private boolean enabled(String code) {
        RiskRule r = find(code);
        return r != null && r.getEnabled() == 1;
    }

    private String value(String code, String fallback) {
        RiskRule r = find(code);
        return r == null || r.getRuleValue() == null ? fallback : r.getRuleValue();
    }

    public void check(String merchantNo, long amountCent) {
        if (enabled(Constants.RULE_MAX_SINGLE)) {
            long max = Long.parseLong(value(Constants.RULE_MAX_SINGLE, "500000"));
            if (amountCent > max) {
                events.log(Constants.TOPIC_RISK, merchantNo, "单笔限额拦截", "REJECTED",
                        "金额 " + Money.format(amountCent) + " 元超过阈值 " + Money.format(max) + " 元");
                throw new LedgerException("风控拒绝：单笔金额 " + Money.format(amountCent)
                        + " 元超过限额 " + Money.format(max) + " 元");
            }
        }
        if (enabled(Constants.RULE_DAILY_LIMIT)) {
            long limit = Long.parseLong(value(Constants.RULE_DAILY_LIMIT, "2000000000"));
            long today = orderMapper.sumAmountSince(LocalDate.now().atStartOfDay());
            if (today + amountCent > limit) {
                events.log(Constants.TOPIC_RISK, merchantNo, "日累计限额拦截", "REJECTED",
                        "今日累计 " + Money.format(today) + " 元，加本笔后超过 " + Money.format(limit) + " 元");
                throw new LedgerException("风控拒绝：超出当日累计限额");
            }
        }
        if (enabled(Constants.RULE_BLACKLIST)) {
            String list = value(Constants.RULE_BLACKLIST, "");
            for (String item : list.split(",")) {
                if (!item.isBlank() && item.trim().equals(merchantNo)) {
                    events.log(Constants.TOPIC_RISK, merchantNo, "黑名单拦截", "REJECTED", "商户在黑名单中");
                    throw new LedgerException("风控拒绝：商户 " + merchantNo + " 在黑名单中");
                }
            }
        }
    }

    @Transactional
    public RiskRule update(String code, String value, Integer enabled) {
        RiskRule r = find(code);
        if (r == null) {
            throw new LedgerException("规则不存在: " + code);
        }
        if (value != null) {
            r.setRuleValue(value);
        }
        if (enabled != null) {
            r.setEnabled(enabled);
        }
        ruleMapper.updateById(r);
        events.log(Constants.TOPIC_RISK, code, "更新风控规则", "OK",
                "值=" + r.getRuleValue() + " 启用=" + r.getEnabled());
        return r;
    }
}
