package com.ledgercore.app.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ledgercore.common.Ids;
import com.ledgercore.common.LedgerException;
import com.ledgercore.common.Money;
import com.ledgercore.domain.Constants;
import com.ledgercore.domain.LedgerRules;
import com.ledgercore.domain.model.Account;
import com.ledgercore.domain.model.AccountEntry;
import com.ledgercore.domain.model.Voucher;
import com.ledgercore.infra.mapper.AccountEntryMapper;
import com.ledgercore.infra.mapper.AccountMapper;
import com.ledgercore.infra.mapper.VoucherMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 记账内核。所有资金变动都必须经过这里：
 * 借贷平衡 -> 凭证落库 -> 行锁 + CAS 更新余额 -> 只追加分录（含余额快照）。
 * 更正一律生成反向凭证，历史分录永不修改。
 */
@Service
@RequiredArgsConstructor
public class LedgerService {

    private final AccountMapper accountMapper;
    private final VoucherMapper voucherMapper;
    private final AccountEntryMapper entryMapper;
    private final EventService events;

    public List<Account> accounts() {
        return accountMapper.selectList(null);
    }

    @Transactional
    public Voucher post(String bizType, String bizNo, String idempotencyKey,
                        String originalVoucherNo, String remark, List<AccountEntry> entries) {
        LedgerRules.assertBalanced(entries);

        LambdaQueryWrapper<Voucher> q = new LambdaQueryWrapper<Voucher>()
                .eq(Voucher::getBizNo, bizNo)
                .eq(Voucher::getIdempotencyKey, idempotencyKey);
        Long exists = voucherMapper.selectCount(q);
        if (exists != null && exists > 0) {
            Voucher existing = voucherMapper.selectOne(q);
            events.log(Constants.TOPIC_LEDGER, bizNo, "幂等命中，跳过重复记账", "SKIPPED",
                    "凭证 " + existing.getVoucherNo() + " 已存在，未重复产生分录");
            return existing;
        }

        Voucher v = new Voucher();
        v.setVoucherNo(Ids.of("VC"));
        v.setBizType(bizType);
        v.setBizNo(bizNo);
        v.setTotalCent(entries.stream()
                .filter(e -> Constants.DR.equals(e.getDirection()))
                .mapToLong(AccountEntry::getAmountCent).sum());
        v.setStatus("POSTED");
        v.setOriginalVoucherNo(originalVoucherNo);
        v.setIdempotencyKey(idempotencyKey);
        v.setRemark(remark);
        voucherMapper.insert(v);

        for (AccountEntry e : entries) {
            Account acc = accountMapper.selectForUpdate(e.getAccountNo());
            if (acc == null) {
                throw new LedgerException("账户不存在: " + e.getAccountNo());
            }
            long delta = LedgerRules.delta(acc.getAccountType(), e.getDirection(), e.getAmountCent());
            long before = acc.getBalanceCent();
            long after = before + delta;
            if (Constants.NATURE_ASSET.equals(acc.getAccountType()) && after < 0) {
                throw new LedgerException("资产账户余额不足: " + acc.getAccountNo()
                        + " 现有 " + Money.format(before) + " 需变动 " + Money.format(delta));
            }
            int updated = accountMapper.casUpdateBalance(e.getAccountNo(), after, acc.getVersion());
            if (updated != 1) {
                throw new LedgerException("并发冲突，账户 " + e.getAccountNo() + " 版本已变化，请重试");
            }
            e.setVoucherNo(v.getVoucherNo());
            e.setBizNo(bizNo);
            e.setIdempotencyKey(idempotencyKey);
            e.setBeforeBalanceCent(before);
            e.setAfterBalanceCent(after);
            entryMapper.insert(e);
        }

        events.log(Constants.TOPIC_LEDGER, bizNo, "记账凭证 " + v.getVoucherNo(), "OK",
                bizType + " 借方合计 " + Money.format(v.getTotalCent()) + " 元，分录 " + entries.size()
                        + " 条，借贷平衡通过");
        return v;
    }

    public static AccountEntry dr(String accountNo, long cent) {
        return entry(accountNo, Constants.DR, cent);
    }

    public static AccountEntry cr(String accountNo, long cent) {
        return entry(accountNo, Constants.CR, cent);
    }

    private static AccountEntry entry(String accountNo, String direction, long cent) {
        AccountEntry e = new AccountEntry();
        e.setAccountNo(accountNo);
        e.setDirection(direction);
        e.setAmountCent(cent);
        return e;
    }
}
