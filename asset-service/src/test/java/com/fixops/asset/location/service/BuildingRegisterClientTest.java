package com.fixops.asset.location.service;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;
import static com.fixops.asset.location.service.BuildingRegisterClient.UnitClassification.*;

class BuildingRegisterClientTest {
    private final JsonMapper mapper=JsonMapper.builder().build();
    private tools.jackson.databind.JsonNode row(String name,String dong,String ho){
        return mapper.createObjectNode().put("mgmBldrgstPk",10241104177L)
            .put("regstrGbCd","2").put("regstrKindCd","4").put("bldNm",name)
            .put("dongNm",dong).put("hoNm",ho);
    }
    @Test void actualExposShapeWithoutUsageFields(){
        var unit=BuildingRegisterClient.residential(row("도곡한신아파트2동","2","607호")).orElseThrow();
        assertEquals("10241104177",unit.recordId());
        assertEquals("2",unit.buildingNo());
        assertEquals("607",unit.unitNo());
    }
    @Test void acceptsSuffixesAndPreservesLeadingZeros(){
        var unit=BuildingRegisterClient.residential(row("아파트","01동","0101호")).orElseThrow();
        assertEquals("01",unit.buildingNo());
        assertEquals("0101",unit.unitNo());
    }
    @Test void commercialKeywordTakesPrecedence(){
        var commercial=row("도곡한신아파트상가"," ","10호");
        assertEquals(NON_RESIDENTIAL_CANDIDATE,BuildingRegisterClient.classify(commercial).type());
        assertTrue(BuildingRegisterClient.residential(commercial).isEmpty());
        assertEquals(NON_RESIDENTIAL_CANDIDATE,BuildingRegisterClient.classify(row("아파트 상가","101","101호")).type());
    }
    @Test void ambiguousOrMissingCodesRequireReview(){
        for(var r:java.util.List.of(row("오피스텔","101","101호"),row("아파트","","301호"),row("아파트 A동","A","301호"))){
            assertEquals(REVIEW_REQUIRED,BuildingRegisterClient.classify(r).type());
            assertTrue(BuildingRegisterClient.residential(r).isEmpty());
        }
    }
    @Test void requiresExactParcel(){
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->new BuildingRegisterClient.Parcel("11680","10100","0","0000","0000"));
    }
}
