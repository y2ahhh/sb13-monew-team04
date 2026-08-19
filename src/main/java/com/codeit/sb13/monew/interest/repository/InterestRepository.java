package com.codeit.sb13.monew.interest.repository;

import com.codeit.sb13.monew.interest.domain.Interest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface InterestRepository extends JpaRepository<Interest, UUID> {

    /**
     * 주어진 이름을 가진 관심사가 이미 존재하는지 확인한다.
     *
     * <p>관심사 등록 시 이름 중복 검사에 사용된다. 엔티티 전체를 조회하지 않고
     * 존재 여부만 확인하므로 단순 조회보다 가볍다.</p>
     *
     * @param name 확인할 관심사 이름
     * @return 동일한 이름의 관심사가 이미 존재하면 {@code true}
     */
    boolean existsByName(String name);
}
