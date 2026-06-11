package com.autoschedule.crew.repository;

import com.autoschedule.crew.domain.Crew;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 사업장 소속 정보의 저장과 조회를 담당한다.
 */
public interface CrewRepository extends JpaRepository<Crew, Long> {
}
