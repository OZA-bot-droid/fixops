package com.fixops.inventory.service;

import com.fixops.inventory.common.exception.DuplicateSkuException;
import com.fixops.inventory.common.exception.PartNotFoundException;
import com.fixops.inventory.common.exception.StockNotFoundException;
import com.fixops.inventory.dto.PartCreateCommand;
import com.fixops.inventory.dto.PartPageResult;
import com.fixops.inventory.dto.PartResult;
import com.fixops.inventory.dto.PartSearchCommand;
import com.fixops.inventory.entity.Part;
import com.fixops.inventory.entity.StockBalance;
import com.fixops.inventory.repository.PartRepository;
import com.fixops.inventory.repository.StockBalanceRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 부품 기준 정보 (등록, 목록 조회, 단건 조회).
 * 수정(PATCH)은 팀장 협의 후 추가한다. 재고 수량은 여기서 바꾸지 않는다 (입고·사용 API로만 변경).
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
    public PartResult create(PartCreateCommand c) {
        // 1) 먼저 확인: 같은 조직에 같은 SKU가 있으면 기존 부품 정보와 함께 409
        partRepository.findByOrganizationIdAndSku(c.organizationId(), c.sku())
                .ifPresent(existing -> {
                    throw new DuplicateSkuException(c.sku(), existing.getId(), existing.getStatus());
                });

        // 2) 부품 저장. saveAndFlush 로 바로 INSERT 해서, 동시에 같은 SKU가 들어오면 여기서 DB가 막는다.
        Part part;
        try {
            part = partRepository.saveAndFlush(Part.create(
                    c.organizationId(), c.sku(), c.name(), c.category(), c.unit(), c.safetyStock()));
        } catch (DataIntegrityViolationException e) {
            // 1)의 확인을 동시에 통과한 두 요청 중 늦은 쪽. DB 의 uq_part_org_sku 가 막았다.
            throw new DuplicateSkuException(c.sku(), null, null);
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
    public PartPageResult search(PartSearchCommand c, Pageable pageable) {
        Page<PartResult> page = stockBalanceRepository.search(
                        c.organizationId(),
                        c.status().toStatusNames(),
                        toKeywordPattern(c.keyword()),
                        c.lowStockOnly(),
                        pageable)
                .map(PartResult::from);   // SQL 결과 한 줄(PartStockRow) → PartResult 로 하나씩 바꾼다
        return PartPageResult.from(page);
    }

    /**
     * 부품 단건 조회 (MCP get_part_stock 용, GET /parts/{partId} 는 팀장 승인 대기).
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

    /** 검색어를 like 패턴으로 바꾼다. 없으면 "%"(전부 통과). */
    private static String toKeywordPattern(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return "%";
        }
        return "%" + keyword.trim() + "%";
    }
}
