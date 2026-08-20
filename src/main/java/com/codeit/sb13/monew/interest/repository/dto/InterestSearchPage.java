package com.codeit.sb13.monew.interest.repository.dto;

import com.codeit.sb13.monew.interest.domain.Interest;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * {@code InterestRepositoryCustom#search}의 조회 결과를 담는 DTO.
 *
 * <p>페이지에 담긴 관심사 엔티티 목록과 함께, 엔티티 자체는 알 수 없는
 * 구독 관련 정보(구독자 수, 요청자의 구독 여부)를 id 기준으로 함께 담는다.
 * 서비스 계층이 이 값들을 조합해 {@code InterestResponse}로 변환한다.</p>
 *
 * @param interests 이번 페이지에 담긴 관심사 목록
 * @param subscriberCounts 관심사 id별 구독자 수
 * @param subscribedInterestIds 요청자가 구독 중인 관심사 id 집합
 * @param hasNext 다음 페이지 존재 여부
 * @param totalElements 검색 조건을 만족하는 전체 관심사 수
 */
public record InterestSearchPage(
        List<Interest> interests,
        Map<UUID, Long> subscriberCounts,
        Set<UUID> subscribedInterestIds,
        boolean hasNext,
        long totalElements
) {
}
