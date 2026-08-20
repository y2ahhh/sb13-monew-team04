package com.codeit.sb13.monew.interest.controller;

import com.codeit.sb13.monew.global.dto.CursorPageResponseDto;
import com.codeit.sb13.monew.interest.controller.dto.InterestCreateRequest;
import com.codeit.sb13.monew.interest.controller.dto.InterestResponse;
import com.codeit.sb13.monew.interest.service.InterestService;
import com.codeit.sb13.monew.interest.service.dto.InterestCreateCommand;
import com.codeit.sb13.monew.interest.service.dto.InterestOrderBy;
import com.codeit.sb13.monew.interest.service.dto.InterestSearchCommand;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/interests")
public class InterestController {

    private final InterestService interestService;

    /**
     * 새로운 관심사를 등록한다.
     *
     * <p>이름과 키워드 목록의 형식 검증({@code @NotBlank}, {@code @Size} 등)은
     * {@link InterestCreateRequest}에서 처리되며, 여기서 실패하면 서비스 계층에
     * 도달하기 전에 {@code GLB_001}로 응답된다. 이름 중복 검사와 도메인 불변조건은
     * {@link InterestService#create}가 책임진다.</p>
     *
     * @param request 등록할 관심사의 이름과 키워드 목록
     * @return 201 상태코드와 등록된 관심사 정보
     */
    @PostMapping
    public ResponseEntity<InterestResponse> create(@Valid @RequestBody InterestCreateRequest request) {
        InterestResponse response =
                interestService.create(new InterestCreateCommand(request.name(), request.keywords()));

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * 조건에 맞는 관심사 목록을 커서 기반으로 조회한다.
     *
     * <p>{@code orderBy}, {@code direction}이 허용된 값(각각 {@code name}/{@code subscriberCount},
     * {@code ASC}/{@code DESC}) 밖이면 스프링이 바인딩 단계에서
     * {@code MethodArgumentTypeMismatchException}을 던지고, {@code GlobalExceptionHandler}가
     * 이를 {@code GLB_001}(400)로 응답한다. {@code cursor}/{@code after}는 이전 응답의
     * {@code nextCursor}/{@code nextAfter}를 그대로 돌려보내는 값으로, 첫 페이지 조회 시에는
     * 생략한다.</p>
     *
     * <p>{@code Monew-Request-User-ID} 헤더로 전달된 사용자를 기준으로 각 관심사의
     * {@code subscribedByMe}를 계산한다. 이 값이 실제로 유효한 사용자인지는 이
     * 계층에서 검증하지 않는다.</p>
     *
     * @param keyword 검색어(관심사 이름 또는 키워드에 포함). 생략하면 전체 대상
     * @param orderBy 정렬 기준({@code name} 또는 {@code subscriberCount})
     * @param direction 정렬 방향({@code ASC} 또는 {@code DESC})
     * @param cursor 이전 페이지 마지막 항목의 정렬 기준 값. 첫 페이지 조회 시 생략
     * @param after 이전 페이지 마지막 항목의 생성 시각. 첫 페이지 조회 시 생략
     * @param limit 조회할 최대 개수. 1 미만이면 400(GLB_001)으로 응답한다.
     * @param requestUserId 요청자 id
     * @return 200 상태코드와 조회된 관심사 목록, 다음 페이지를 위한 커서 정보
     */
    @GetMapping
    public ResponseEntity<CursorPageResponseDto<InterestResponse>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam InterestOrderBy orderBy,
            @RequestParam Sort.Direction direction,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime after,
            @RequestParam int limit,
            @RequestHeader("Monew-Request-User-ID") UUID requestUserId
    ) {
        if (limit < 1) {
            throw new IllegalArgumentException("limit은 1 이상이어야 합니다: " + limit);
        }

        CursorPageResponseDto<InterestResponse> response = interestService.search(
                new InterestSearchCommand(
                        keyword,
                        orderBy,
                        direction,
                        cursor,
                        after,
                        limit,
                        requestUserId
                )
        );

        return ResponseEntity.ok(response);
    }
}
