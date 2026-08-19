package com.codeit.sb13.monew.interest.service;

import com.codeit.sb13.monew.global.exception.interest.InterestNameDuplicatedException;
import com.codeit.sb13.monew.interest.controller.dto.InterestResponse;
import com.codeit.sb13.monew.interest.domain.Interest;
import com.codeit.sb13.monew.interest.repository.InterestRepository;
import com.codeit.sb13.monew.interest.repository.SubscribeRepository;
import com.codeit.sb13.monew.interest.service.dto.InterestCreateCommand;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.apache.commons.text.similarity.LevenshteinDistance;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class InterestServiceImpl implements InterestService{

    /**
     * 새 이름과 기존 이름의 Levenshtein 유사도가 이 값 이상이면
     * "이미 존재하는 관심사"로 간주해 등록을 거부한다.
     */
    private final static double SIMILAR_NAME_THRESHOLD = 0.8;

    private final InterestRepository interestRepository;
    private final SubscribeRepository subscribeRepository;

    /**
     * {@inheritDoc}
     *
     * <p>이름 중복을 두 단계로 확인한 뒤, {@link Interest#create}로 애그리거트를 만들고
     * 커맨드의 키워드를 순서대로 {@link Interest#addKeyword}에 넘겨 채운다.
     * 새로 만든 관심사는 아직 구독 레코드와 연결된 적이 없으므로, 구독자 수와
     * 구독 여부는 항상 0/false로 응답한다.</p>
     *
     * <p>첫 번째 단계는 {@link InterestRepository#existsByName}으로 완전히 같은
     * 이름이 있는지 확인한다. 인덱스를 타는 가벼운 조회라 대부분의 등록 요청은
     * 이 단계에서 곧바로 끝난다. 두 번째 단계는 {@link #isNameTooSimilarToExisting}로
     * 기존 이름들과 {@link #SIMILAR_NAME_THRESHOLD} 이상 유사한 이름이 있는지 확인한다.
     * 완전히 같은 이름은 유사도가 항상 1.0이라 이 단계에서도 걸러지지만, 기존 이름
     * 전체를 불러와 하나하나 비교해야 하는 무거운 연산이라 정확 일치를 먼저 걸러낸
     * 뒤에만 실행하도록 순서를 나눴다.</p>
     *
     * <p>사전 중복 확인과 실제 저장 사이에는 경쟁 구간이 존재한다. 두 요청이 동시에
     * 같은 이름 또는 서로 유사한 이름으로 등록을 시도하면 둘 다 사전 확인을 통과할
     * 수 있다. 완전히 같은 이름은 {@code interests.name}에 걸린 유니크 제약이 최후
     * 방어선 역할을 하지만({@code saveAndFlush}로 즉시 반영해 이 메서드 안에서 곧바로
     * 잡아낸다), 유사 이름 판별은 DB 제약으로 표현할 수 없는 조건이라 그 방어선이
     * 통하지 않는다. 그래서 이 메서드 전체를 {@link Isolation#SERIALIZABLE} 격리
     * 수준으로 실행해, 서로 다른 두 트랜잭션이 겹치는 이름 집합을 동시에 읽고 각자
     * 다른 이름을 저장하는 이상 현상 자체를 DB가 감지해 한쪽을 직렬화 실패로
     * 되돌리게 한다. 되돌려진 트랜잭션은 {@link #create}를 다시 호출해도 사전 확인부터
     * 다시 하므로 결과적으로 안전하게 재시도할 수 있는데, 이 재시도는
     * {@link Retryable @Retryable}로 처리한다. 재시도 어드바이저가 트랜잭션 어드바이저
     * 바깥에서 감싸도록 {@code RetryConfig}에서 순서를 명시적으로 고정해뒀기 때문에,
     * 재시도할 때마다 새 트랜잭션이 열린다.</p>
     */
    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    @Retryable(
            retryFor = ConcurrencyFailureException.class,
            backoff = @Backoff(delay = 50, maxDelay = 200, random = true)
    )
    public InterestResponse create(InterestCreateCommand command) {
        if (interestRepository.existsByName(command.name())) {
            throw new InterestNameDuplicatedException(command.name());
        }

        if (isNameTooSimilarToExisting(command.name())) {
            throw new InterestNameDuplicatedException(command.name());
        }

        Interest interest = Interest.create(command.name());
        command.keywords().forEach(interest::addKeyword);

        try {
            Interest saved = interestRepository.saveAndFlush(interest);
            return InterestResponse.of(saved, 0L, false);
        } catch (DataIntegrityViolationException e) {
            if (isNameUniqueViolation(e)) {
                throw new InterestNameDuplicatedException(command.name(), e);
            }
            throw e;
        }
    }

    /**
     * 저장 시점에 발생한 무결성 위반이 {@code interests.name}의 유니크 제약
     * ({@code uk_interests_name}) 때문인지 판별한다.
     *
     * <p>운영 DB(Postgres)는 따옴표로 감싸지 않은 식별자를 소문자로 저장하지만,
     * 테스트에 쓰는 H2는 제약/인덱스 이름을 대문자로 돌려준다
     * (예: {@code "PUBLIC.UK_INTERESTS_NAME INDEX PUBLIC.UK_INTERESTS_NAME_INDEX_C ..."}).
     * DB마다 대소문자 표기가 달라질 수 있으므로, 메시지를 소문자로 바꾼 뒤
     * 비교해 대소문자에 관계없이 판별되도록 한다. 이때 {@link String#toLowerCase()}를
     * 로케일 없이 쓰면 JVM 기본 로케일이 튀르키예어일 경우 대문자 {@code I}가
     * 점 없는 {@code ı}로 변환되어 {@code "INTERESTS"}가 {@code "interests"}가 아닌
     * {@code "ınterests"}로 바뀌는 문제가 있어, 언어와 무관한 {@link Locale#ROOT}를
     * 명시적으로 사용한다.</p>
     */
    private boolean isNameUniqueViolation(DataIntegrityViolationException e) {
        String message = e.getMostSpecificCause().getMessage();
        return message != null
                && message.toLowerCase(Locale.ROOT).contains("uk_interests_name");
    }

    /**
     * 새로 등록하려는 이름이 기존 관심사 이름 중 하나와
     * {@link #SIMILAR_NAME_THRESHOLD} 이상 유사한지 확인한다.
     *
     * <p>{@link InterestRepository#findAllNames}로 기존 이름 전체를 가져와
     * {@link #getLevenshteinMatchRate}로 하나씩 비교한다. 완전히 같은 이름은
     * 유사도가 항상 1.0이므로 이 메서드만으로도 정확 일치를 포함한 모든 경우를
     * 판별할 수 있지만, {@link #create}에서는 성능을 위해 정확 일치를
     * {@link InterestRepository#existsByName}으로 먼저 걸러낸 뒤에만 이 메서드를
     * 호출한다.</p>
     *
     * @param newName 새로 등록하려는 관심사 이름
     * @return 기존 이름 중 하나라도 임계값 이상 유사하면 {@code true}
     */
    private boolean isNameTooSimilarToExisting(String newName) {
        return interestRepository.findAllNames().stream()
                .anyMatch(existingName ->
                        getLevenshteinMatchRate(newName, existingName) >= SIMILAR_NAME_THRESHOLD);
    }

    /**
     * 두 문자열의 Levenshtein 편집 거리를 0(전혀 다름)~1(완전히 같음) 사이의
     * 유사도로 환산한다.
     *
     * <p>{@code 1 - (편집 거리 / 두 문자열 중 더 긴 쪽의 길이)}로 계산한다.
     * 예를 들어 길이 6인 문자열이 길이 5인 문자열과 편집 거리 1(글자 하나 차이)이면
     * 유사도는 {@code 1 - 1/6 ≈ 0.833}이다. 두 문자열이 모두 빈 문자열이면
     * 편집 거리도 0이고 나눌 길이도 0이 되어 0으로 나누기 문제가 생기므로,
     * 이 경우는 완전히 같은 것으로 보아 1.0을 반환한다.</p>
     *
     * @param newName      새로 등록하려는 관심사 이름
     * @param existingName 비교 대상이 되는 기존 관심사 이름
     * @return 0.0(전혀 다름) ~ 1.0(완전히 같음) 사이의 유사도
     */
    private double getLevenshteinMatchRate(String newName, String existingName) {
        int maxLength = Math.max(newName.length(), existingName.length());
        if (maxLength == 0) return 1.0;

        int distance = LevenshteinDistance.getDefaultInstance().apply(newName, existingName);
        return 1.0 - (double) distance / maxLength;
    }
}
