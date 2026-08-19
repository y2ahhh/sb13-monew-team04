package com.codeit.sb13.monew.interest.service;

import com.codeit.sb13.monew.global.exception.interest.InterestNameDuplicatedException;
import com.codeit.sb13.monew.interest.controller.dto.InterestResponse;
import com.codeit.sb13.monew.interest.domain.Interest;
import com.codeit.sb13.monew.interest.repository.InterestRepository;
import com.codeit.sb13.monew.interest.repository.SubscribeRepository;
import com.codeit.sb13.monew.interest.service.dto.InterestCreateCommand;
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

    private boolean isNameUniqueViolation(DataIntegrityViolationException e) {
        String message = e.getMostSpecificCause().getMessage();
        return message != null && message.contains("uk_interests_name");
    }
}
