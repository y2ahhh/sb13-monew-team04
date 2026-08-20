package com.codeit.sb13.monew.interest.repository;

import com.codeit.sb13.monew.interest.repository.dto.InterestSearchPage;
import com.codeit.sb13.monew.interest.service.dto.InterestOrderBy;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.data.domain.Sort;

/**
 * QueryDSL로 구현하는 관심사 목록 조회.
 *
 * <p>동적 검색어 필터, 정렬 기준 선택, 커서 기반 페이지네이션처럼
 * Spring Data JPA의 메서드 이름 규칙이나 {@code @Query}만으로는 표현하기
 * 어려운 조회라 커스텀 구현으로 분리했다.</p>
 */
public interface InterestRepositoryCustom {

    /**
     * 조건에 맞는 관심사 목록을 커서 기반으로 조회한다.
     *
     * @param keyword 검색어(관심사 이름 또는 키워드 텍스트에 포함). {@code null}/공백이면 전체 대상
     * @param orderBy 정렬 기준
     * @param direction 정렬 방향
     * @param cursor 이전 페이지 마지막 항목의 정렬 기준 값. 첫 페이지 조회 시 {@code null}
     * @param after 이전 페이지 마지막 항목의 생성 시각(보조 커서). 첫 페이지 조회 시 {@code null}
     * @param limit 조회할 최대 개수
     * @param requestUserId 요청자 id. {@code null}이면 구독 여부는 계산하지 않는다
     * @return 조회 결과
     */
    InterestSearchPage search(
            String keyword,
            InterestOrderBy orderBy,
            Sort.Direction direction,
            String cursor,
            LocalDateTime after,
            int limit,
            UUID requestUserId
    );
}
