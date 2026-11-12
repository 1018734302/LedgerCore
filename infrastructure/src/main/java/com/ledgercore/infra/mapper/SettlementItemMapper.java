package com.ledgercore.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ledgercore.domain.model.SettlementItem;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface SettlementItemMapper extends BaseMapper<SettlementItem> {

    @Select("SELECT * FROM settlement_item ORDER BY id DESC LIMIT #{limit}")
    List<SettlementItem> selectRecent(@Param("limit") int limit);
}
