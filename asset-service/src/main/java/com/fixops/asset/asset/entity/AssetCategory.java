package com.fixops.asset.asset.entity;

import com.fixops.asset.common.enums.IndustryType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 조직별로 관리하는 산업 유형별 설비 분류. */
@Entity
@Table(name = "asset_category", uniqueConstraints = @UniqueConstraint(
        name = "uq_asset_category_org_industry_name",
        columnNames = {"organization_id", "industry_type", "name"}))
public class AssetCategory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "industry_type", nullable = false, length = 20)
    private IndustryType industryType;

    @NotBlank
    @Size(max = 100)
    @Column(nullable = false, length = 100)
    private String name;

    protected AssetCategory() {}

    public AssetCategory(Long organizationId, IndustryType industryType, String name) {
        this.organizationId = organizationId;
        this.industryType = industryType;
        this.name = name;
    }

    public Long getId() { return id; }
    public Long getOrganizationId() { return organizationId; }
    public IndustryType getIndustryType() { return industryType; }
    public String getName() { return name; }

    public void rename(String name) { this.name = name; }
}
