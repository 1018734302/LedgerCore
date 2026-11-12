package com.ledgercore.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ledgercore.domain.model.ChannelBill;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface ChannelBillMapper extends BaseMapper<ChannelBill> {

    @Select("SELECT * FROM channel_bill WHERE bill_date = #{billDate}")
    List<ChannelBill> selectByDate(@Param("billDate") String billDate);

    @Select("SELECT * FROM channel_bill ORDER BY id DESC LIMIT #{limit}")
    List<ChannelBill> selectRecent(@Param("limit") int limit);
}
