package com.autoschedule.member.repository;

import com.autoschedule.member.domain.Member;
import com.autoschedule.member.domain.SocialProvider;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 회원 소셜 식별자 기반 조회와 저장을 담당한다.
 */
public interface MemberRepository extends JpaRepository<Member, Long> {

    /**
     * 소셜 제공자와 소셜 subject로 회원을 조회한다.
     */
    Optional<Member> findBySocialProviderAndSocialSubject(SocialProvider socialProvider, String socialSubject);

    /**
     * 소셜 제공자와 소셜 subject로 이미 가입된 회원이 있는지 확인한다.
     */
    boolean existsBySocialProviderAndSocialSubject(SocialProvider socialProvider, String socialSubject);
}
