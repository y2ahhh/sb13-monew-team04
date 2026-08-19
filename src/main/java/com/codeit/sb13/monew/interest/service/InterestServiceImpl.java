package com.codeit.sb13.monew.interest.service;

import com.codeit.sb13.monew.global.exception.interest.InterestNameDuplicatedException;
import com.codeit.sb13.monew.interest.controller.dto.InterestResponse;
import com.codeit.sb13.monew.interest.domain.Interest;
import com.codeit.sb13.monew.interest.repository.InterestRepository;
import com.codeit.sb13.monew.interest.repository.SubscribeRepository;
import com.codeit.sb13.monew.interest.service.dto.InterestCreateCommand;
import lombok.RequiredArgsConstructor;
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
     */
    @Override
    public InterestResponse create(InterestCreateCommand command) {
        if (interestRepository.existsByName(command.name())) {
            throw new InterestNameDuplicatedException(command.name());
        }

        Interest interest = Interest.create(command.name());
        command.keywords().forEach(interest::addKeyword);

        Interest saved = interestRepository.save(interest);

        return InterestResponse.of(saved, 0L, false);
    }
}
