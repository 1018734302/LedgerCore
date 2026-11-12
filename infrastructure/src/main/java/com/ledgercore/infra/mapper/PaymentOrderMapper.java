package com.ledgercore.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ledgercore.domain.model.PaymentOrder;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

public interface PaymentOrderMapper extends BaseMapper<PaymentOrder> {

    @Select("SELECT * FROM payment_order ORDER BY id DESC LIMIT #{limit}")
    List<PaymentOrder> selectRecent(@Param("limit") int limit);

    @Select("SELECT COALESCE(SUM(amount_cent), 0) FROM payment_order "
            + "WHERE created_at >= #{start} AND status <> 'FAILED'")
    long sumAmountSince(@Param("start") LocalDateTime start);

    /** 状态 + 版本双条件 CAS，晚到的回调无法覆盖已成功状态 */
    @Update("UPDATE payment_order SET status = #{to}, channel_txn_no = #{channelTxnNo}, "
            + "version = version + 1 WHERE order_no = #{orderNo} AND status = #{from} AND version = #{version}")
    int casTransit(@Param("orderNo") String orderNo,
                   @Param("from") String from,
                   @Param("to") String to,
                   @Param("channelTxnNo") String channelTxnNo,
                   @Param("version") long version);

    /** 退款 CAS：累计退款额不得超过订单金额，天然防超退 */
    @Update("UPDATE payment_order SET status = #{to}, refunded_cent = refunded_cent + #{delta}, "
            + "version = version + 1 WHERE order_no = #{orderNo} "
            + "AND status IN ('SUCCESS', 'PART_REFUNDED') "
            + "AND refunded_cent + #{delta} <= amount_cent AND version = #{version}")
    int casRefund(@Param("orderNo") String orderNo,
                  @Param("to") String to,
                  @Param("delta") long delta,
                  @Param("version") long version);
}
