package com.ledgercore.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ledgercore.domain.model.ReconDiff;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface ReconDiffMapper extends BaseMapper<ReconDiff> {

    @Select("SELECT * FROM recon_diff ORDER BY id DESC LIMIT #{limit}")
    List<ReconDiff> selectRecent(@Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM recon_diff WHERE status <> 'RESOLVED' AND status <> 'REJECTED'")
    long countOpen();
}
