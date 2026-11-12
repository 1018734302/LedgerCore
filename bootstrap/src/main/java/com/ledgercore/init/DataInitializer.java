package com.ledgercore.init;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ledgercore.app.service.EventService;
import com.ledgercore.domain.Constants;
import com.ledgercore.domain.model.Account;
import com.ledgercore.domain.model.RiskRule;
import com.ledgercore.infra.mapper.AccountMapper;
import com.ledgercore.infra.mapper.RiskRuleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** 启动时幂等初始化科目账户与风控规则。期初余额不来自分录，总分核对以 opening_balance_cent 为基准。 */
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final AccountMapper accountMapper;
    private final RiskRuleMapper ruleMapper;
    private final EventService events;

    @Override
    public void run(ApplicationArguments args) {
        account(Constants.ACC_CHANNEL, "渠道待清算", Constants.NATURE_ASSET, 0L);
        account(Constants.ACC_MERCHANT, "商户待结算", Constants.NATURE_LIABILITY, 0L);
        account(Constants.ACC_COMMISSION, "平台佣金收入", Constants.NATURE_INCOME, 0L);
        account(Constants.ACC_BANK, "银行存款", Constants.NATURE_ASSET, 100_000_000L);
        account(Constants.ACC_BUYER, "买家余额", Constants.NATURE_LIABILITY, 0L);

        rule(Constants.RULE_MAX_SINGLE, "单笔支付上限（分）", "500000", "默认 5000.00 元，改小可演示拦截");
        rule(Constants.RULE_DAILY_LIMIT, "当日累计支付上限（分）", "2000000000", "默认 20000000.00 元");
        rule(Constants.RULE_BLACKLIST, "商户黑名单（逗号分隔）", "", "填入商户号即可演示黑名单拦截");

        events.log(Constants.TOPIC_SYSTEM, "BOOT", "系统启动完成", "OK",
                "5 个科目账户与 3 条风控规则就绪；H2 文件库 ./data/ledgercore.mv.db，重启数据不丢失");
    }

    private void account(String no, String name, String nature, long opening) {
        Long c = accountMapper.selectCount(new LambdaQueryWrapper<Account>()
                .eq(Account::getAccountNo, no));
        if (c != null && c > 0) {
            return;
        }
        Account a = new Account();
        a.setAccountNo(no);
        a.setAccountName(name);
        a.setAccountType(nature);
        a.setOpeningBalanceCent(opening);
        a.setBalanceCent(opening);
        a.setFrozenCent(0L);
        a.setStatus("ACTIVE");
        a.setVersion(0L);
        accountMapper.insert(a);
    }

    private void rule(String code, String name, String value, String remark) {
        Long c = ruleMapper.selectCount(new LambdaQueryWrapper<RiskRule>()
                .eq(RiskRule::getRuleCode, code));
        if (c != null && c > 0) {
            return;
        }
        RiskRule r = new RiskRule();
        r.setRuleCode(code);
        r.setRuleName(name);
        r.setRuleValue(value);
        r.setEnabled(1);
        r.setRemark(remark);
        ruleMapper.insert(r);
    }
}
