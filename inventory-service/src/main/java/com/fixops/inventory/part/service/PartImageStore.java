package com.fixops.inventory.part.service;

import com.fixops.inventory.common.image.StoredImage;
import com.fixops.inventory.common.image.UploadedImage;
import com.fixops.inventory.part.exception.PartNotFoundException;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * 부품 사진 (부품 하나에 1장, 관리자가 부품 등록할 때 첨부, DB 저장).
 * 부품(part) 엔티티는 바꾸지 않고 따로 테이블(part_image)에 둔다 → 목록 조회가 사진 때문에 무거워지지 않는다.
 *
 * save 는 PartService.create 의 트랜잭션 안에서 불린다.
 * JdbcTemplate 도 JPA 와 같은 DB 연결을 쓰므로, 사진 저장이 실패하면 부품 등록도 함께 취소된다.
 */
@Component
public class PartImageStore {

    private final JdbcTemplate jdbc;

    public PartImageStore(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    void save(long organizationId, long partId, MultipartFile file) {
        byte[] data = UploadedImage.bytes(file);
        jdbc.update("INSERT INTO part_image (part_id, organization_id, file_name, content_type, size_bytes, data) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                partId, organizationId, UploadedImage.safeName(file.getOriginalFilename()),
                UploadedImage.detectType(data), data.length, data);
    }

    /** 사진이 있는지 (단건 조회·수정 응답의 hasImage) */
    boolean exists(long partId) {
        Boolean found = jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM part_image WHERE part_id = ?)", Boolean.class, partId);
        return Boolean.TRUE.equals(found);
    }

    /** 사진 보기. 같은 조직 부품만. 사진이 없거나 다른 조직이면 404 */
    public StoredImage load(long organizationId, long partId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT content_type, data FROM part_image WHERE part_id = ? AND organization_id = ?",
                partId, organizationId);
        if (rows.isEmpty()) {
            throw new PartNotFoundException(partId);
        }
        return new StoredImage((String) rows.get(0).get("content_type"), (byte[]) rows.get(0).get("data"));
    }
}
