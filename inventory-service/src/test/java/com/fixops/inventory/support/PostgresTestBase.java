package com.fixops.inventory.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * 모든 통합 테스트가 컨테이너 하나를 공유한다(싱글톤 패턴).
 * Spring은 같은 설정의 컨텍스트를 캐시해 재사용하므로, 컨테이너 수명이 테스트 클래스에 묶이면
 * 두 번째 클래스부터 이미 종료된 컨테이너에 접속하려다 ConnectException이 난다.
 */
@SpringBootTest
public abstract class PostgresTestBase {

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16");

    static {
        POSTGRES.start();
    }
}
