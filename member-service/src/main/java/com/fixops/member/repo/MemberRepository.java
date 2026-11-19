package com.fixops.member.repo;
import com.fixops.member.model.Member;
import org.springframework.data.jpa.repository.JpaRepository;
public interface MemberRepository extends JpaRepository<Member,Long> { java.util.Optional<Member> findByEmail(String email); }
