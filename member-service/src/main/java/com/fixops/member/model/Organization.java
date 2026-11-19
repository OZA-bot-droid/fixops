package com.fixops.member.model;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name="organizations")
public class Organization {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
 @Column(name="applicant_id",nullable=false) public Long applicantId;
 @Column(nullable=false) public String name;
 @Column(nullable=false) public String kind;
 @Column(nullable=false) public String status="PENDING_APPROVAL";
 @Column(name="review_reason") public String reviewReason;
 @Column(name="reviewed_by") public Long reviewedBy;
 @Column(name="reviewed_at") public Instant reviewedAt;
 @Column(name="created_at",nullable=false) public Instant createdAt=Instant.now();
 protected Organization() {}
 public Organization(Long applicantId,String name,String kind){this.applicantId=applicantId;this.name=name;this.kind=kind;}
}
