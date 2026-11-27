package com.fixops.workorder.booking.holiday.client;

import com.fixops.workorder.booking.config.HolidayApiProperties;
import com.fixops.workorder.booking.holiday.dto.PublicHolidayDTO;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.DefaultUriBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import java.io.StringReader;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.nio.charset.StandardCharsets;

@Component
public class HolidayApiClient {

    private final HolidayApiProperties properties;

    private final RestClient restClient;


    public HolidayApiClient(HolidayApiProperties properties) {

        this.properties = properties;


        // 외부 API Timeout 설정
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();

        factory.setConnectTimeout(properties.getConnectTimeout());

        factory.setReadTimeout(properties.getReadTimeout());


        // 인증키 URL 인코딩 처리
        DefaultUriBuilderFactory uriFactory = new DefaultUriBuilderFactory(properties.getBaseUrl());

        uriFactory.setEncodingMode(DefaultUriBuilderFactory.EncodingMode.TEMPLATE_AND_VALUES);


        this.restClient = RestClient.builder()

                .uriBuilderFactory(uriFactory)

                .requestFactory(factory)

                .build();
    }


    // =====================================
    // 월별 공휴일 API 호출
    // =====================================

    public List<PublicHolidayDTO> fetchHolidays(YearMonth yearMonth) {


        byte[] response;

        try {

            response = restClient.get()

                    .uri(uriBuilder -> uriBuilder

                            .path("/getRestDeInfo")

                            .queryParam("serviceKey", "{serviceKey}")

                            .queryParam("solYear", yearMonth.getYear())

                            .queryParam("solMonth", String.format(Locale.ROOT, "%02d", yearMonth.getMonthValue()))

                            .queryParam("pageNo", 1)

                            .queryParam("numOfRows", 100)

                            .build(properties.getServiceKey()))

                    .retrieve()

                    .body(byte[].class);

        } catch (RestClientResponseException ex) {

            throw new IllegalStateException("Holiday API HTTP error: " + ex.getStatusCode().value());

        } catch (Exception ex) {

            throw new IllegalStateException("Holiday API request failed: " + ex.getClass().getSimpleName());
        }


        // 응답 데이터 존재 여부 검증
        if (response == null || response.length == 0) {

            throw new IllegalStateException("공휴일 API 응답이 비어 있습니다.");
        }


        // UTF-8 인코딩 명시적 적용
        String xml = new String(response, StandardCharsets.UTF_8);


        // 기존 XML 파싱 로직 사용
        return parseHolidays(xml, yearMonth);
    }


    // =====================================
    // XML 응답 파싱
    // =====================================

    private List<PublicHolidayDTO> parseHolidays(

            String xml,

            YearMonth requestedMonth

    ) {

        if (xml == null || xml.isBlank()) {

            throw new IllegalStateException("공휴일 API 응답이 비어 있습니다.");
        }


        try {

            // XML 파서 생성
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();


            // 외부 XML 엔티티 참조 방지
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);

            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);

            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");

            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");

            factory.setXIncludeAware(false);

            factory.setExpandEntityReferences(false);


            Document document = factory

                    .newDocumentBuilder()

                    .parse(new InputSource(new StringReader(xml)));


            Element root = document.getDocumentElement();


            // 정상 API 응답 구조인지 확인
            if (!"response".equals(root.getTagName())) {

                throw new IllegalStateException("공휴일 API 응답 구조 오류");
            }


            Element header = getRequiredElement(root, "header");


            // HTTP 200과 API 정상 처리 여부는 별개
            String resultCode = getRequiredText(header, "resultCode");


            if (!"00".equals(resultCode)) {

                throw new IllegalStateException("공휴일 API 처리 실패");
            }


            Element body = getRequiredElement(root, "body");


            int totalCount = Integer.parseInt(getRequiredText(body, "totalCount"));


            NodeList items = body.getElementsByTagName("item");


            // 한 달 전체 데이터가 반환되었는지 확인
            if (totalCount < 0

                    || totalCount > 100

                    || totalCount != items.getLength()) {

                throw new IllegalStateException("공휴일 API 전체 데이터 수 불일치");
            }


            // 동일 날짜에 여러 공휴일 명칭이 존재할 수 있으므로
            // 날짜별로 명칭을 병합
            Map<LocalDate, TreeSet<String>> holidays = new TreeMap<>();


            for (int i = 0; i < items.getLength(); i++) {

                Element item = (Element) items.item(i);


                String isHoliday = getRequiredText(item, "isHoliday");


                if (!"Y".equals(isHoliday) && !"N".equals(isHoliday)) {

                    throw new IllegalStateException("공휴일 여부 값 오류");
                }


                LocalDate holidayDate = LocalDate.parse(

                        getRequiredText(item, "locdate"),

                        DateTimeFormatter.BASIC_ISO_DATE);


                // 요청한 월의 데이터인지 확인
                if (!YearMonth.from(holidayDate).equals(requestedMonth)) {

                    throw new IllegalStateException("공휴일 조회 대상 월 불일치");
                }


                // 실제 휴일이 아닌 특일은 제외
                if ("N".equals(isHoliday)) {

                    continue;
                }


                String holidayName = getRequiredText(item, "dateName");


                holidays

                        .computeIfAbsent(holidayDate, key -> new TreeSet<>())

                        .add(holidayName);
            }


            List<PublicHolidayDTO> result = new ArrayList<>();


            holidays.forEach((date, names) -> {

                result.add(new PublicHolidayDTO(

                        date,

                        String.join(" / ", names)));
            });


            return List.copyOf(result);

        } catch (Exception ex) {

            // 검증 실패한 응답은 저장하지 않음
            throw new IllegalStateException("공휴일 API 응답 파싱 또는 검증 실패");
        }
    }


    // =====================================
    // XML 필수 Element 조회
    // =====================================

    private Element getRequiredElement(

            Element parent,

            String tagName

    ) {

        NodeList nodes = parent.getElementsByTagName(tagName);


        if (nodes.getLength() != 1) {

            throw new IllegalStateException("XML 필수 항목 누락 또는 중복: " + tagName);
        }


        return (Element) nodes.item(0);
    }


    // =====================================
    // XML 필수 문자열 조회
    // =====================================

    private String getRequiredText(

            Element parent,

            String tagName

    ) {

        String value = getRequiredElement(

                parent,

                tagName

        ).getTextContent().trim();


        if (value.isBlank()) {

            throw new IllegalStateException("XML 필수 데이터 누락: " + tagName);
        }


        return value;
    }
}