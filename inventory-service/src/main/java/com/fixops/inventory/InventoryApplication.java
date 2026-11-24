package com.fixops.inventory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
/**
 * inventory-service 시작점. main() 을 실행하면 Spring 이 이 패키지(com.fixops.inventory)와
 * 하위 패키지를 훑어서 @RestController, @Service, @Repository 등을 찾아 객체로 만들고 서로 연결한다.
 * 시작할 때 Flyway 가 DB 마이그레이션(V1, V2 ...)을 적용하고, JPA 가 Entity 와 테이블이 맞는지 검사한다.
 */
@SpringBootApplication
public class InventoryApplication {
    public static void main(String[] args) { SpringApplication.run(InventoryApplication.class, args); }
}
