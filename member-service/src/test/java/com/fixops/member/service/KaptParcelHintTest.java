package com.fixops.member.service;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;

class KaptParcelHintTest {
    private final JsonMapper mapper=JsonMapper.builder().build();
    @Test void parsesObservedLot(){
        var hint=KaptClient.parseParcelHint(mapper.readTree("{\"bjdCode\":\"1168010100\",\"kaptAddr\":\"서울특별시 강남구 역삼동 795-41 역삼동월드메르디앙아파트\"}")).orElseThrow();
        assertEquals("11680",hint.sigunguCd());
        assertEquals("10100",hint.bjdongCd());
        assertEquals("0795",hint.bun());
        assertEquals("0041",hint.ji());
        assertEquals("0",hint.platGbCd());
    }
    @Test void rejectsRoadAndMultipleLots(){
        for(String addr:java.util.List.of("서울특별시 강남구 도곡로21길 15","서울특별시 강남구 역삼동 795-41 외 2필지")){
            var row=mapper.createObjectNode().put("bjdCode","1168010100").put("kaptAddr",addr);
            assertTrue(KaptClient.parseParcelHint(row).isEmpty());
        }
    }
}
