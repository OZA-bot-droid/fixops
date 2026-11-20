package com.fixops.inventory.service;

import com.fixops.inventory.dto.DeductCommand;
import com.fixops.inventory.dto.DeductResult;
import com.fixops.inventory.common.exception.IdempotencyKeyReuseException;
import com.fixops.inventory.common.exception.InsufficientStockException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fixops.inventory.support.PostgresTestBase;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** 두 동시성 방식(FOR UPDATE / 조건부 UPDATE)이 같은 불변식을 지키는지 같은 시나리오로 검증한다. */
class StockConcurrencyTest extends PostgresTestBase {

    private static final long ORG = 1L;

    enum Strategy {
        FOR_UPDATE, CONDITIONAL_UPDATE
    }

    @Autowired
    StockDeductionService service;

    @Autowired
    JdbcTemplate jdbc;

    private Function<DeductCommand, DeductResult> pick(Strategy s) {
        return s == Strategy.FOR_UPDATE ? service::deductWithLock : service::deductConditional;
    }

    private long seed(int quantity) {
        long partId = jdbc.queryForObject(
                "INSERT INTO part (organization_id, sku, name) VALUES (?, ?, 'test') RETURNING id",
                Long.class, ORG, "SKU-" + UUID.randomUUID());
        jdbc.update("INSERT INTO stock_balance (part_id, quantity) VALUES (?, ?)", partId, quantity);
        return partId;
    }

    private int balance(long partId) {
        return jdbc.queryForObject("SELECT quantity FROM stock_balance WHERE part_id = ?", Integer.class, partId);
    }

    private int count(String table, long partId) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE part_id = ?", Integer.class, partId);
    }

    /** 모든 스레드가 준비될 때까지 기다렸다가 동시에 출발시킨다. 결과 또는 예외를 그대로 모은다. */
    private <T> List<Object> runConcurrently(int threads, java.util.function.IntFunction<Callable<T>> taskFactory)
            throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Object>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            Callable<T> task = taskFactory.apply(i);
            Callable<Object> wrapper = () -> {
                ready.countDown();
                go.await();
                try {
                    return task.call();
                } catch (Exception e) {
                    return e;
                }
            };
            futures.add(pool.submit(wrapper));
        }
        ready.await();
        go.countDown();
        List<Object> results = new ArrayList<>();
        for (Future<Object> f : futures) {
            results.add(f.get());
        }
        pool.shutdown();
        return results;
    }

    @ParameterizedTest
    @EnumSource(Strategy.class)
    void onlyOneOfManyCompetingRequestsSucceedsWhenStockIsShort(Strategy strategy) throws Exception {
        long partId = seed(5);
        var deduct = pick(strategy);

        List<Object> results = runConcurrently(8, i -> () -> deduct.apply(
                new DeductCommand(ORG, 100 + i, partId, 4, "key-" + UUID.randomUUID(), 1L)));

        long success = results.stream().filter(r -> r instanceof DeductResult).count();
        long insufficient = results.stream().filter(r -> r instanceof InsufficientStockException).count();
        assertThat(success).isEqualTo(1);
        assertThat(insufficient).isEqualTo(7);
        assertThat(balance(partId)).isEqualTo(1);
        assertThat(count("part_usage", partId)).isEqualTo(1);
        assertThat(count("stock_movement", partId)).isEqualTo(1);
    }

    @ParameterizedTest
    @EnumSource(Strategy.class)
    void allRequestsSucceedAndBalanceIsExactWhenStockIsEnough(Strategy strategy) throws Exception {
        long partId = seed(100);
        var deduct = pick(strategy);

        List<Object> results = runConcurrently(16, i -> () -> deduct.apply(
                new DeductCommand(ORG, 200 + i, partId, 3, "key-" + UUID.randomUUID(), 1L)));

        assertThat(results).allMatch(r -> r instanceof DeductResult);
        assertThat(balance(partId)).isEqualTo(100 - 16 * 3);
        assertThat(count("stock_movement", partId)).isEqualTo(16);
        Integer ledgerSum = jdbc.queryForObject(
                "SELECT sum(quantity_delta) FROM stock_movement WHERE part_id = ?", Integer.class, partId);
        assertThat(ledgerSum).isEqualTo(-48);
    }

    @ParameterizedTest
    @EnumSource(Strategy.class)
    void sameOperationKeyDeductsOnlyOnce(Strategy strategy) throws Exception {
        long partId = seed(10);
        var deduct = pick(strategy);
        String key = "retry-" + UUID.randomUUID();

        List<Object> results = runConcurrently(8, i -> () -> deduct.apply(
                new DeductCommand(ORG, 300, partId, 2, key, 1L)));

        assertThat(results).allMatch(r -> r instanceof DeductResult);
        long originals = results.stream().filter(r -> r instanceof DeductResult d && !d.replayed()).count();
        assertThat(originals).isEqualTo(1);
        assertThat(results.stream().map(r -> ((DeductResult) r).usageId()).distinct()).hasSize(1);
        assertThat(balance(partId)).isEqualTo(8);
        assertThat(count("part_usage", partId)).isEqualTo(1);
    }

    @ParameterizedTest
    @EnumSource(Strategy.class)
    void sameKeyWithDifferentContentIsRejected(Strategy strategy) {
        long partId = seed(10);
        var deduct = pick(strategy);
        String key = "reuse-" + UUID.randomUUID();
        deduct.apply(new DeductCommand(ORG, 400, partId, 2, key, 1L));

        assertThatThrownBy(() -> deduct.apply(new DeductCommand(ORG, 400, partId, 5, key, 1L)))
                .isInstanceOf(IdempotencyKeyReuseException.class);
        assertThat(balance(partId)).isEqualTo(8);
    }

    @ParameterizedTest
    @EnumSource(Strategy.class)
    void failedDeductionLeavesNoUsageOrLedgerRow(Strategy strategy) {
        long partId = seed(1);
        var deduct = pick(strategy);

        assertThatThrownBy(() -> deduct.apply(
                new DeductCommand(ORG, 500, partId, 2, "fail-" + UUID.randomUUID(), 1L)))
                .isInstanceOf(InsufficientStockException.class);

        assertThat(balance(partId)).isEqualTo(1);
        assertThat(count("part_usage", partId)).isZero();
        assertThat(count("stock_movement", partId)).isZero();
    }
}
