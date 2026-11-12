package com.ledgercore.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ledgercore.domain.model.Account;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface AccountMapper extends BaseMapper<Account> {

    /** 行锁：同一账户并发记账时串行化，防止超扣 */
    @Select("SELECT * FROM account WHERE account_no = #{accountNo} FOR UPDATE")
    Account selectForUpdate(@Param("accountNo") String accountNo);

    /** 乐观锁兜底：版本不匹配说明并发冲突，由上层重试 */
    @Update("UPDATE account SET balance_cent = #{balanceCent}, version = version + 1 "
            + "WHERE account_no = #{accountNo} AND version = #{version}")
    int casUpdateBalance(@Param("accountNo") String accountNo,
                         @Param("balanceCent") long balanceCent,
                         @Param("version") long version);

    @Update("UPDATE account SET balance_cent = opening_balance_cent, version = 0")
    int resetBalances();
}
