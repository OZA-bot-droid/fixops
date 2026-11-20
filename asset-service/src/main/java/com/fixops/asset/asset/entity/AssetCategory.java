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

/** 산업별로 공통 사용하는 설비 분류. */
@Entity
@Table(name = "asset_category", uniqueConstraints = @UniqueConstraint(
        name = "uq_asset_category_industry_name", columnNames = {"industry_type", "name"}))
public class AssetCategory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "industry_type", nullable = false, length = 20)
    private IndustryType industryType;

    @NotBlank
    @Size(max = 100)
    @Column(nullable = false, length = 100)
    private String name;

    protected AssetCategory() {}

    public Long getId() { return id; }
    public IndustryType getIndustryType() { return industryType; }
    public String getName() { return name; }
}
