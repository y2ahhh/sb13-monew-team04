package com.codeit.sb13.monew.interest.repository;

import com.codeit.sb13.monew.interest.domain.Interest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
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

    /**
     * 모든 관심사의 이름만 조회한다.
     *
     * <p>관심사 등록 시 새 이름이 기존 이름과 유사한지 비교하는 데 사용된다.
     * 키워드 등 나머지 필드가 필요 없는 비교이므로, 엔티티 전체를 불러오는
     * {@link #findAll()} 대신 이름 컬럼만 조회한다.</p>
     *
     * @return 저장된 모든 관심사의 이름 목록
     */
    @Query("select i.name from Interest i")
    List<String> findAllNames();
}
