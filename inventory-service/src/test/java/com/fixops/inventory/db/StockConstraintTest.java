package com.fixops.inventory.db;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fixops.inventory.support.PostgresTestBase;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/** DB 제약이 서비스 코드와 무관하게 최후 방어선으로 동작하는지 확인한다. */
class StockConstraintTest extends PostgresTestBase {

    @Autowired
    JdbcTemplate jdbc;

    private long newPart(long orgId, String sku) {
        return jdbc.queryForObject(
                "INSERT INTO part (organization_id, sku, name) VALUES (?, ?, 'test') RETURNING id",
                Long.class, orgId, sku);
    }

    @Test
    void negativeStockIsRejected() {
        long partId = newPart(1, "NEG-" + UUID.randomUUID());
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO stock_balance (part_id, quantity) VALUES (?, -1)", partId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void duplicateSkuInSameOrganizationIsRejected() {
        String sku = "DUP-" + UUID.randomUUID();
        newPart(1, sku);
        assertThatThrownBy(() -> newPart(1, sku)).isInstanceOf(DataIntegrityViolationException.class);
    }
}
