package com.fixops.inventory.part.service;

import com.fixops.inventory.part.exception.DuplicateSkuException;
import com.fixops.inventory.part.exception.PartNotFoundException;
import com.fixops.inventory.stock.exception.StockNotFoundException;
import com.fixops.inventory.part.dto.PartCreateRequest;
import com.fixops.inventory.part.dto.PartPageResult;
import com.fixops.inventory.part.dto.PartResult;
import com.fixops.inventory.part.dto.PartStatusFilter;
import com.fixops.inventory.part.dto.PartUpdateRequest;
import com.fixops.inventory.part.entity.Part;
import com.fixops.inventory.stock.entity.StockBalance;
import com.fixops.inventory.part.repository.PartRepository;
import com.fixops.inventory.stock.repository.StockBalanceRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 부품 기준 정보 (등록, 목록 조회, 단건 조회).
 * 재고 수량과 SKU 는 여기서 바꾸지 않는다 (수량은 입고·사용 API로만 변경).
 */
@Service
public class PartService {

    private final PartRepository partRepository;
    private final StockBalanceRepository stockBalanceRepository;

    // 생성자가 하나면 Spring 이 자동으로 Repository 를 넣어 준다 (생성자 주입).
    public PartService(PartRepository partRepository, StockBalanceRepository stockBalanceRepository) {
        this.partRepository = partRepository;
        this.stockBalanceRepository = stockBalanceRepository;
    }

    /**
     * 부품 등록.
     * 부품(part)과 수량 0인 재고 행(stock_balance)을 같은 트랜잭션에서 만든다.
     * 둘 중 하나라도 실패하면 둘 다 취소되어 "재고 행 없는 부품"이 생기지 않는다.
     */
    @Transactional
    public PartResult create(long organizationId, PartCreateRequest r) {
        // 입력 정리: 앞뒤 공백 제거. " BOLT-M8" 과 "BOLT-M8" 이 서로 다른 SKU 로 등록되는 것을 막는다.
        // (빈칸·길이 검사는 Controller 의 @Valid 가 이미 했다. 여기서는 다른 곳에서 불려도 안전하게 한 번 더 확인)
        String sku = requireText(r.sku(), "sku");
        String name = requireText(r.name(), "name");
        if (r.safetyStock() != null && r.safetyStock() < 0) {
            throw new IllegalArgumentException("safetyStock은 0 이상이어야 합니다.");
        }

        // 1) 먼저 확인: 같은 조직에 같은 SKU가 있으면 기존 부품 정보와 함께 409
        partRepository.findByOrganizationIdAndSku(organizationId, sku)
                .ifPresent(existing -> {
                    throw new DuplicateSkuException(sku, existing.getId(), existing.getStatus());
                });

        // 2) 부품 저장. saveAndFlush 로 바로 INSERT 해서, 동시에 같은 SKU가 들어오면 여기서 DB가 막는다.
        Part part;
        try {
            part = partRepository.saveAndFlush(Part.create(
                    organizationId, sku, name, r.category(), r.unit(), r.safetyStock()));
        } catch (DataIntegrityViolationException e) {
            // 1)의 확인을 동시에 통과한 두 요청 중 늦은 쪽. DB 의 uq_part_org_sku 가 막았다.
            throw new DuplicateSkuException(sku, null, null);
        }

        // 3) 수량 0인 재고 행 저장
        StockBalance balance = stockBalanceRepository.save(StockBalance.zeroFor(part));

        return PartResult.from(balance);
    }

    /**
     * 부품 목록 조회. 우리 조직 부품만, 현재 수량과 함께 돌려준다.
     * readOnly = true : 조회만 하므로 변경 감지를 하지 않아 조금 가볍다.
     */
    @Transactional(readOnly = true)
    public PartPageResult search(long organizationId, String keyword, PartStatusFilter status,
                                 boolean lowStockOnly, Pageable pageable) {
        PartStatusFilter filter = (status == null) ? PartStatusFilter.ACTIVE : status;   // OpenAPI 기본값
        Page<PartResult> page = stockBalanceRepository.search(
                        organizationId,
                        filter.toStatusNames(),
                        toKeywordPattern(keyword),
                        lowStockOnly,
                        pageable)
                .map(PartResult::from);   // SQL 결과 한 줄(PartStockRow) → PartResult 로 하나씩 바꾼다
        return PartPageResult.from(page);
    }

    /**
     * 부품 단건 조회 (GET /parts/{partId}, MCP get_part_stock 용). ⏳ 주소는 팀장님 승인 대기.
     * 다른 조직 부품이면 "없음"(404)으로 답한다.
     */
    @Transactional(readOnly = true)
    public PartResult get(long organizationId, long partId) {
        Part part = partRepository.findByIdAndOrganizationId(partId, organizationId)
                .orElseThrow(() -> new PartNotFoundException(partId));
        StockBalance balance = stockBalanceRepository.findByPartId(part.getId())
                .orElseThrow(() -> new StockNotFoundException("stock balance not found: partId=" + partId));
        return PartResult.from(balance);
    }

    /**
     * 부품 정보 수정 (name, category, unit, safetyStock).
     *
     * 1) 우리 단지 부품인지 확인하며 가져온다 (다른 단지 부품이면 404)
     * 2) Entity 의 changeInfo 로 값만 바꾼다. save() 를 부르지 않아도
     *    JPA 가 DB 에서 읽어 온 객체를 지켜보고 있다가 UPDATE 한다 (변경 감지).
     * 3) flush: 지금 바로 UPDATE 를 실행시킨다. 그래야 @PreUpdate 가 돌아 updatedAt 이 바뀐 값으로 응답된다.
     */
    @Transactional
    public PartResult update(long organizationId, long partId, PartUpdateRequest r) {
        // 입력 확인. null 은 "바꾸지 않음"이고, 최소 한 항목은 있어야 한다.
        if (r.name() == null && r.category() == null && r.unit() == null && r.safetyStock() == null) {
            throw new IllegalArgumentException("수정할 항목이 없습니다.");
        }
        String name = (r.name() == null) ? null : requireText(r.name(), "name");   // 보냈다면 빈칸 불가
        String unit = (r.unit() == null) ? null : requireText(r.unit(), "unit");
        if (r.safetyStock() != null && r.safetyStock() < 0) {
            throw new IllegalArgumentException("safetyStock은 0 이상이어야 합니다.");
        }

        Part part = partRepository.findByIdAndOrganizationId(partId, organizationId)
                .orElseThrow(() -> new PartNotFoundException(partId));

        part.changeInfo(name, r.category(), unit, r.safetyStock());
        partRepository.flush();

        StockBalance balance = stockBalanceRepository.findByPartId(part.getId())
                .orElseThrow(() -> new StockNotFoundException("stock balance not found: partId=" + part.getId()));
        return PartResult.from(balance);
    }

    /** 비어 있으면 안 되는 글자 값: 공백이면 400(IllegalArgumentException), 아니면 앞뒤 공백을 지워 돌려준다. */
    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + "은(는) 비워 둘 수 없습니다.");
        }
        return value.trim();
    }

    /** 검색어를 like 패턴으로 바꾼다. 없으면 "%"(전부 통과). */
    private static String toKeywordPattern(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return "%";
        }
        return "%" + keyword.trim() + "%";
    }
}
