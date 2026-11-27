package com.fixops.inventory.request.service;

import com.fixops.inventory.common.exception.auth.OrgAccessDeniedException;
import com.fixops.inventory.common.image.UploadedImage;
import com.fixops.inventory.request.dto.PartRequestImageFile;
import com.fixops.inventory.request.dto.PartRequestImageInfo;
import com.fixops.inventory.request.exception.PartRequestNotFoundException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.multipart.MultipartFile;

/**
 * 부품 요청 첨부 사진 저장·조회 (DB 저장, 2026-11-27 결정).
 *
 * 왜 DB 에 저장하나
 *   추가 설정 없이 로컬·Docker·AWS(RDS) 어디서나 동작하고, 권한 확인도 요청과 같은 방식으로 할 수 있다.
 *   대신 DB 가 커지므로 장당 5MB, 요청당 1장으로 제한한다 (DB 의 CHECK 제약으로도 막음).
 *
 * 보안
 *   - 형식·크기 검사는 UploadedImage (부품 사진과 같은 규칙)
 *   - 사진 보기는 같은 조직만, TECHNICIAN 은 자기 요청의 사진만
 */
class PartRequestImageStore {

    static final int MAX_FILES = 1;
    private final JdbcTemplate jdbc;

    PartRequestImageStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 저장 전에 검사: 개수, 크기, 실제 형식. 하나라도 틀리면 아무것도 저장하지 않는다 (400) */
    static void validate(List<MultipartFile> files) {
        if (files.size() > MAX_FILES) {
            throw new IllegalArgumentException("사진은 " + MAX_FILES + "장만 첨부할 수 있습니다.");
        }
        files.forEach(UploadedImage::validate);
    }

    /** 사진 저장 (요청 등록과 같은 트랜잭션 안에서 부른다) */
    void save(long organizationId, long requestId, List<MultipartFile> files) {
        for (MultipartFile f : files) {
            byte[] data = UploadedImage.bytes(f);
            jdbc.update("INSERT INTO part_request_image (request_id, organization_id, file_name, content_type, size_bytes, data) "
                            + "VALUES (?, ?, ?, ?, ?, ?)",
                    requestId, organizationId, UploadedImage.safeName(f.getOriginalFilename()), UploadedImage.detectType(data), data.length, data);
        }
    }

    /** 여러 요청의 사진 정보를 한 번에 (목록에서 요청마다 따로 묻지 않도록) */
    Map<Long, List<PartRequestImageInfo>> infos(Collection<Long> requestIds) {
        if (requestIds.isEmpty()) {
            return Map.of();
        }
        String marks = requestIds.stream().map(id -> "?").collect(Collectors.joining(","));
        Map<Long, List<PartRequestImageInfo>> result = new HashMap<>();
        jdbc.query("SELECT id, request_id, file_name, content_type, size_bytes FROM part_request_image "
                        + "WHERE request_id IN (" + marks + ") ORDER BY id",
                rs -> {
                    result.computeIfAbsent(rs.getLong("request_id"), k -> new java.util.ArrayList<>())
                            .add(new PartRequestImageInfo(rs.getLong("id"), rs.getString("file_name"),
                                    rs.getString("content_type"), rs.getInt("size_bytes")));
                },
                requestIds.toArray());
        return result;
    }

    /**
     * 사진 한 장 보기. 같은 조직의 요청만, onlyRequester 가 있으면 그 사람이 만든 요청만.
     * 없거나 남의 것이면 "없음"(404) 으로 답한다 (있다는 사실도 알려 주지 않기 위해)
     */
    PartRequestImageFile load(long organizationId, long requestId, long imageId, Long onlyRequester) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT i.content_type, i.data, r.requested_by FROM part_request_image i "
                        + "JOIN part_request r ON r.id = i.request_id "
                        + "WHERE i.id = ? AND i.request_id = ? AND i.organization_id = ?",
                imageId, requestId, organizationId);
        if (rows.isEmpty()) {
            throw new PartRequestNotFoundException(requestId);
        }
        Map<String, Object> row = rows.get(0);
        if (onlyRequester != null && ((Number) row.get("requested_by")).longValue() != onlyRequester) {
            throw new OrgAccessDeniedException();
        }
        return new PartRequestImageFile((String) row.get("content_type"), (byte[]) row.get("data"));
    }
}
