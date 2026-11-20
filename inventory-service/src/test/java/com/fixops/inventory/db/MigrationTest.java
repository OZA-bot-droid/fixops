package com.fixops.inventory.db;

import static org.assertj.core.api.Assertions.assertThat;

import com.fixops.inventory.support.PostgresTestBase;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** Flyway가 V1을 적용해 재고 테이블을 만들었는지 확인한다. */
class MigrationTest extends PostgresTestBase {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void flywayCreatesInventoryTables() {
        List<String> tables = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'", String.class);
        assertThat(tables).contains("part", "stock_balance", "stock_movement", "part_usage");
    }
}
