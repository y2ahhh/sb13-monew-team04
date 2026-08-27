package com.codeit.sb13.monew.article.repository.dto;

import com.codeit.sb13.monew.article.domain.ArticleSource;
import com.codeit.sb13.monew.article.service.dto.ArticleOrderBy;
import org.springframework.data.domain.Sort;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * @param keyword 검색어(제목 또는 요약에 포함). {@code null}/공백이면 전체 대상
 * @param sourceIn 출처 복수 선택. {@code null}이거나 비어 있으면 전체 출처
 * @param publishDateFrom 발행일 시작(포함). {@code null}이면 하한 없음
 * @param publishDateTo 발행일 종료(포함). {@code null}이면 상한 없음
 * @param requestUserId 요청자 id. 각 기사의 조회 여부를 계산하는 데 쓰임
 * @param orderBy 정렬 기준
 * @param direction 정렬 방향
 * @param cursor 이전 페이지 마지막 항목의 커서. {@code "정렬 기준 값|id"} 형태이며,
 *               첫 페이지면 {@code null}
 * @param after 이전 페이지 마지막 항목의 생성 시각(보조 커서). 첫 페이지면 {@code null}
 * @param idAfter 이전 페이지 마지막 항목의 id(3차 커서). 별도 파라미터로 오지 않으면
 *                {@code cursor}에 함께 실려 온 값을 쓴다. 페이지 경계에서 정렬 기준 값과
 *                생성 시각이 모두 같은 기사를 건너뛰지 않으려면 반드시 필요하다
 * @param limit 조회할 최대 개수
 */
public record ArticleSearchCondition(
        String keyword,
        List<ArticleSource> sourceIn,
        LocalDateTime publishDateFrom,
        LocalDateTime publishDateTo,
        ArticleOrderBy orderBy,
        Sort.Direction direction,
        String cursor,
        LocalDateTime after,
        UUID idAfter,
        int limit,
        UUID requestUserId
) {

    /**
     * 커서에 3차 정렬 기준(id)을 함께 실어 보낼 때 쓰는 구분자.
     *
     * <p>정렬 기준 값은 ISO-8601 날짜이거나 정수라 이 문자가 들어가진 않는다.</p>
     */
    public static final String CURSOR_DELIMITER = "|";
}