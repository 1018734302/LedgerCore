package com.ledgercore.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ledgercore.domain.model.EventLog;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface EventLogMapper extends BaseMapper<EventLog> {

    /** 页面轮询：按 id 增量拉取，实现「真实操作数据流」 */
    @Select("SELECT * FROM event_log WHERE id > #{afterId} ORDER BY id ASC LIMIT #{limit}")
    List<EventLog> selectAfter(@Param("afterId") long afterId, @Param("limit") int limit);

    @Select("SELECT * FROM event_log ORDER BY id DESC LIMIT #{limit}")
    List<EventLog> selectRecent(@Param("limit") int limit);
}
