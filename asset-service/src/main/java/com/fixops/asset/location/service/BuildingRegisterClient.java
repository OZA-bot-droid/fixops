package com.fixops.asset.location.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.nio.ByteBuffer;
import java.io.ByteArrayOutputStream;

@Component
public class BuildingRegisterClient {
    private static final String BASE="https://apis.data.go.kr/1613000/BldRgstHubService/getBrExposInfo";
    private static final int SIZE=100, MAX_PAGES=100, MAX_BYTES=2*1024*1024;
    private final String key;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();
    private final JsonMapper mapper=JsonMapper.builder().build();
    private final Semaphore slots=new Semaphore(2);
    public BuildingRegisterClient(@Value("${fixops.building-register.service-key:}") String key){this.key=key;}
    public record Parcel(String sigunguCd,String bjdongCd,String platGbCd,String bun,String ji) {
        public Parcel {
            if (!matches(sigunguCd,"[0-9]{5}") || !matches(bjdongCd,"[0-9]{5}")
                || !matches(platGbCd,"[012]") || !matches(bun,"[0-9]{4}") || !matches(ji,"[0-9]{4}") || "0000".equals(bun))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"INVALID_INPUT");
        }
        private static boolean matches(String v,String regex){return v!=null&&v.matches(regex);}
    }
    public record Unit(String recordId,String buildingNo,String unitNo,String buildingName,String unitName,
                       String address,String roadAddress,String buildingLabel,String floor) {}
    public enum UnitClassification { RESIDENTIAL_CANDIDATE, NON_RESIDENTIAL_CANDIDATE, REVIEW_REQUIRED }
    public record ExcludedUnit(String recordId,String buildingLabel,String buildingName,String unitName,
                               UnitClassification classification,String reason) {}
    public record Preview(List<Unit> units,int excludedCount,int sourceCount,int nonResidentialCount,
                          int reviewCount,List<ExcludedUnit> excludedUnits) {}
    private static final List<String> NON_RESIDENTIAL_KEYWORDS=List.of("상가","상점","근린생활","판매시설",
        "관리사무소","관리사무실","경비실","기계실","전기실","주차장","경로당","노인정","창고");
    private static final List<String> REVIEW_KEYWORDS=List.of("오피스텔","사무실","업무시설","복합시설");
    record Classification(UnitClassification type,String reason) {}
    static Classification classify(JsonNode row){
        String combined=(val(row,"bldNm")+" "+val(row,"dongNm")+" "+val(row,"hoNm")).replaceAll("\\s+","");
        for(String keyword:NON_RESIDENTIAL_KEYWORDS)
            if(combined.contains(keyword))return new Classification(UnitClassification.NON_RESIDENTIAL_CANDIDATE,"비주거 키워드: "+keyword);
        for(String keyword:REVIEW_KEYWORDS)
            if(combined.contains(keyword))return new Classification(UnitClassification.REVIEW_REQUIRED,"검토 키워드: "+keyword);
        if(!"2".equals(val(row,"regstrGbCd"))||!"4".equals(val(row,"regstrKindCd")))
            return new Classification(UnitClassification.REVIEW_REQUIRED,"집합건축물 전유부 여부 미확인");
        String building=val(row,"dongNm"),unit=val(row,"hoNm"),id=val(row,"mgmBldrgstPk");
        if(id.isBlank()||id.length()>100||!building.matches("[0-9]{1,39}동?")||!unit.matches("[0-9]{1,39}호?"))
            return new Classification(UnitClassification.REVIEW_REQUIRED,"동·호수 또는 식별자 형식 확인 필요");
        return new Classification(UnitClassification.RESIDENTIAL_CANDIDATE,"동·호수 형식 일치");
    }
    static String code(String value,String suffix){
        String result=value.trim();
        if(result.endsWith(suffix)) result=result.substring(0,result.length()-suffix.length()).trim();
        return result;
    }
    static String val(JsonNode row,String name){var n=row.path(name);return n.isNull()||n.isMissingNode()?"":n.asText("").trim();}
    static Optional<Unit> residential(JsonNode row){
        // ExposInfo does not supply official usage fields. Classification is a candidate only.
        if(classify(row).type()!=UnitClassification.RESIDENTIAL_CANDIDATE)return Optional.empty();
        String building=val(row,"dongNm"), unit=val(row,"hoNm"), id=val(row,"mgmBldrgstPk");
        String bc=code(building,"동"), uc=code(unit,"호");
        if(id.isBlank()||id.length()>100||bc.isBlank()||uc.isBlank()||bc.length()>40||uc.length()>40)
            return Optional.empty();
        return Optional.of(new Unit(id,bc,uc,building,unit,val(row,"platPlc"),val(row,"newPlatPlc"),val(row,"bldNm"),val(row,"flrNo")));
    }
    public Preview preview(Parcel parcel){
        if(key.isBlank()||!slots.tryAcquire())throw unavailable();
        long deadline=System.nanoTime()+Duration.ofSeconds(35).toNanos();
        try{
            var units=new LinkedHashMap<String,Unit>();
            var ids=new HashSet<String>();
            int total=-1, nonResidential=0, review=0;
            var excludedUnits=new ArrayList<ExcludedUnit>();
            for(int page=1;page<=MAX_PAGES;page++){
                var body=fetch(parcel,page,deadline);
                int count=Integer.parseInt(val(body,"totalCount"));
                if(count<0||count>SIZE*MAX_PAGES||(total>=0&&total!=count))throw unavailable();
                total=count;
                var items=body.path("items").path("item");
                List<JsonNode> rows=new ArrayList<>();
                if(items.isArray())items.forEach(rows::add);
                else if(items.isObject())rows.add(items);
                else if(body.path("items").isArray())body.path("items").forEach(rows::add);
                int expected=(int)Math.min(SIZE,Math.max(0L,total-(page-1L)*SIZE));
                if(rows.size()!=expected)throw unavailable();
                for(var row:rows){
                    String id=val(row,"mgmBldrgstPk");
                    if(id.isBlank()||!ids.add(id))throw unavailable();
                    var unit=residential(row);
                    if(unit.isEmpty()){
                        var classification=classify(row);
                        if(classification.type()==UnitClassification.NON_RESIDENTIAL_CANDIDATE)nonResidential++;
                        else review++;
                        excludedUnits.add(new ExcludedUnit(id,val(row,"bldNm"),val(row,"dongNm"),val(row,"hoNm"),
                            classification.type(),classification.reason()));
                        continue;
                    }
                    Unit u=unit.get();
                    String k=u.buildingNo()+"\u0000"+u.unitNo();
                    if(units.putIfAbsent(k,u)!=null)throw new ResponseStatusException(HttpStatus.CONFLICT,"INVALID_STATE_TRANSITION");
                }
                if((long)page*SIZE>=total)return new Preview(List.copyOf(units.values()),nonResidential+review,total,nonResidential,review,List.copyOf(excludedUnits));
            }
            throw unavailable();
        }catch(ResponseStatusException e){throw e;}
        catch(Exception e){throw unavailable();}
        finally{slots.release();}
    }
    private JsonNode fetch(Parcel p,int page,long deadline) throws Exception{
        long remaining=deadline-System.nanoTime();
        if(remaining<=0)throw unavailable();
        Duration timeout=Duration.ofNanos(Math.min(remaining,Duration.ofSeconds(12).toNanos()));
        var uri=UriComponentsBuilder.fromUriString(BASE).queryParam("serviceKey","{key}")
            .queryParam("sigunguCd",p.sigunguCd()).queryParam("bjdongCd",p.bjdongCd())
            .queryParam("platGbCd",p.platGbCd()).queryParam("bun",p.bun()).queryParam("ji",p.ji())
            .queryParam("_type","json").queryParam("pageNo",page).queryParam("numOfRows",SIZE)
            .encode().buildAndExpand(Map.of("key",key)).toUri();
        var request=HttpRequest.newBuilder(uri).timeout(timeout).header("Accept","application/json").GET().build();
        var pending=http.sendAsync(request, info -> new BoundedBody());
        try{
            var response=pending.get(Math.max(1,timeout.toMillis()),TimeUnit.MILLISECONDS);
            if(response.statusCode()!=200)throw unavailable();
            var root=mapper.readTree(response.body()).path("response");
            if(!Set.of("00","0000","0").contains(val(root.path("header"),"resultCode")))throw unavailable();
            return root.path("body");
        }catch(InterruptedException e){Thread.currentThread().interrupt();throw e;}
        finally{if(!pending.isDone())pending.cancel(true);}
    }
    static class BoundedBody implements HttpResponse.BodySubscriber<byte[]>{
        private final CompletableFuture<byte[]> result=new CompletableFuture<>();
        private final ByteArrayOutputStream buffer=new ByteArrayOutputStream();
        private Flow.Subscription subscription;
        public CompletionStage<byte[]> getBody(){return result;}
        public void onSubscribe(Flow.Subscription s){subscription=s;s.request(1);}
        public void onNext(List<ByteBuffer> chunks){
            for(var chunk:chunks){
                if(chunk.remaining()>MAX_BYTES-buffer.size()){subscription.cancel();result.completeExceptionally(new IllegalStateException("Body too large"));return;}
                byte[] b=new byte[chunk.remaining()];chunk.get(b);buffer.writeBytes(b);
            }
            subscription.request(1);
        }
        public void onError(Throwable t){result.completeExceptionally(t);}
        public void onComplete(){result.complete(buffer.toByteArray());}
    }
    private static ResponseStatusException unavailable(){return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"DEPENDENCY_UNAVAILABLE");}
}
