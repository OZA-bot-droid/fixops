package com.fixops.member.model;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name="members")
public class Member {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
 @Column(nullable=false,unique=true) public String email;
 @Column(name="password_hash",nullable=false) public String passwordHash;
 @Column(name="display_name",nullable=false) public String displayName;
 @Column(name="email_verified",nullable=false) public boolean emailVerified;
 @Column(name="platform_admin",nullable=false) public boolean platformAdmin;
 @Column(name="created_at",nullable=false) public Instant createdAt=Instant.now();
 protected Member() {}
 public Member(String email,String passwordHash,String displayName,boolean emailVerified){this.email=email;this.passwordHash=passwordHash;this.displayName=displayName;this.emailVerified=emailVerified;}
}
