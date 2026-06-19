package com.autoschedule.member.repository;

import com.autoschedule.member.domain.Member;
import com.autoschedule.member.domain.MemberStatus;
import com.autoschedule.member.domain.SocialProvider;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 회원 소셜 식별자 기반 조회와 저장을 담당한다.
 */
public interface MemberRepository extends JpaRepository<Member, Long> {

    /**
     * 회원 ID와 상태로 회원을 조회한다.
     */
    Optional<Member> findByIdAndStatus(Long id, MemberStatus status);

    /**
     * 회원 ID 목록과 상태로 회원 목록을 조회한다.
     */
    List<Member> findByIdInAndStatus(Collection<Long> ids, MemberStatus status);

    /**
     * 일반 사용자 API에서 사용할 활성 회원 단건 조회 메서드다.
     */
    default Optional<Member> findActiveById(Long id) {
        return findByIdAndStatus(id, MemberStatus.ACTIVE);
    }

    /**
     * 일반 사용자 API에서 사용할 활성 회원 목록 조회 메서드다.
     */
    default List<Member> findActiveByIdIn(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return findByIdInAndStatus(ids, MemberStatus.ACTIVE);
    }

    /**
     * 소셜 제공자와 소셜 subject로 회원을 조회한다.
     */
    Optional<Member> findBySocialProviderAndSocialSubject(SocialProvider socialProvider, String socialSubject);

    /**
     * 소셜 제공자와 소셜 subject로 이미 가입된 회원이 있는지 확인한다.
     */
    boolean existsBySocialProviderAndSocialSubject(SocialProvider socialProvider, String socialSubject);
}
