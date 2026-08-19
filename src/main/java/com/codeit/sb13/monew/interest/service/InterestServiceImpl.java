package com.codeit.sb13.monew.interest.service;

import com.codeit.sb13.monew.global.exception.interest.InterestNameDuplicatedException;
import com.codeit.sb13.monew.interest.controller.dto.InterestResponse;
import com.codeit.sb13.monew.interest.domain.Interest;
import com.codeit.sb13.monew.interest.repository.InterestRepository;
import com.codeit.sb13.monew.interest.repository.SubscribeRepository;
import com.codeit.sb13.monew.interest.service.dto.InterestCreateCommand;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class InterestServiceImpl implements InterestService{

    private final InterestRepository interestRepository;
    private final SubscribeRepository subscribeRepository;

    /**
     * {@inheritDoc}
     *
     * <p>이름 중복을 먼저 확인한 뒤, {@link Interest#create}로 애그리거트를 만들고
     * 커맨드의 키워드를 순서대로 {@link Interest#addKeyword}에 넘겨 채운다.
     * 새로 만든 관심사는 아직 구독 레코드와 연결된 적이 없으므로, 구독자 수와
     * 구독 여부는 항상 0/false로 응답한다.</p>
     *
     * <p>사전 중복 확인과 실제 저장 사이에는 경쟁 구간이 존재할 수 있다. 두 요청이
     * 동시에 같은 이름으로 등록을 시도하면 둘 다 사전 확인을 통과할 수 있는데,
     * {@code interests.name}에 걸린 유니크 제약이 마지막 방어선 역할을 한다.
     * 저장을 {@code saveAndFlush}로 즉시 반영해 제약 위반을 이 메서드 안에서
     * 곧바로 잡아내고, {@link #isNameUniqueViolation}으로 원인이 이름 중복인지
     * 확인해 {@link InterestNameDuplicatedException}으로 변환한다.</p>
     */
    @Override
    public InterestResponse create(InterestCreateCommand command) {
        if (interestRepository.existsByName(command.name())) {
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
}
