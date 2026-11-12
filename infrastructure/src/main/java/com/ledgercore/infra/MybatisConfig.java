package com.ledgercore.infra;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("com.ledgercore.infra.mapper")
public class MybatisConfig {
}
