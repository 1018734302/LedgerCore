package com.ledgercore.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ledgercore.domain.model.AccountEntry;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface AccountEntryMapper extends BaseMapper<AccountEntry> {

    @Select("SELECT COALESCE(SUM(CASE WHEN direction = 'DR' THEN amount_cent ELSE 0 END), 0) FROM account_entry")
    long sumDr();

    @Select("SELECT COALESCE(SUM(CASE WHEN direction = 'CR' THEN amount_cent ELSE 0 END), 0) FROM account_entry")
    long sumCr();

    /** 账户净变动（未乘科目方向系数），用于总分核对 */
    @Select("SELECT COALESCE(SUM(CASE WHEN direction = 'DR' THEN amount_cent ELSE -amount_cent END), 0) "
            + "FROM account_entry WHERE account_no = #{accountNo}")
    long sumSignedDelta(@Param("accountNo") String accountNo);

    /** 逐凭证校验借贷平衡，返回不平衡的凭证数 */
    @Select("SELECT COUNT(*) FROM (SELECT voucher_no FROM account_entry GROUP BY voucher_no "
            + "HAVING SUM(CASE WHEN direction = 'DR' THEN amount_cent ELSE 0 END) "
            + "<> SUM(CASE WHEN direction = 'CR' THEN amount_cent ELSE 0 END)) t")
    long countUnbalancedVouchers();

    @Select("SELECT * FROM account_entry ORDER BY id DESC LIMIT #{limit}")
    List<AccountEntry> selectRecent(@Param("limit") int limit);

    @Select("SELECT * FROM account_entry WHERE voucher_no = #{voucherNo} ORDER BY id")
    List<AccountEntry> selectByVoucher(@Param("voucherNo") String voucherNo);
}
