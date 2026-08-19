package com.codeit.sb13.monew.interest.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codeit.sb13.monew.global.exception.interest.InterestNameDuplicatedException;
import com.codeit.sb13.monew.interest.controller.dto.InterestResponse;
import com.codeit.sb13.monew.interest.domain.Interest;
import com.codeit.sb13.monew.interest.repository.InterestRepository;
import com.codeit.sb13.monew.interest.repository.SubscribeRepository;
import com.codeit.sb13.monew.interest.service.dto.InterestCreateCommand;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class InterestServiceTest {

    @Mock
    InterestRepository interestRepository;

    @Mock
    SubscribeRepository subscribeRepository;

    @Captor
    ArgumentCaptor<Interest> interestCaptor;

    @InjectMocks
    InterestServiceImpl interestServiceImpl;

    @Test
    @DisplayName("사전 검사에서 이름이 이미 존재하면 InterestNameDuplicatedException을 던지고 저장하지 않는다")
    void create_duplicateName_throwsException() {
        // given
        InterestCreateCommand command = new InterestCreateCommand("스포츠", List.of("축구"));
        when(interestRepository.existsByName(command.name())).thenReturn(true);

        // when & then
        assertThatThrownBy(() -> interestServiceImpl.create(command))
                .isInstanceOf(InterestNameDuplicatedException.class);

        verify(interestRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("정상 요청이면 관심사와 키워드를 저장하고, 구독자 0명/미구독 상태로 응답한다")
    void create_validRequest_savesAndReturnsResponse() {
        // given
        InterestCreateCommand command = new InterestCreateCommand("스포츠", List.of("축구", "야구"));
        UUID generatedId = UUID.randomUUID();

        when(interestRepository.existsByName(command.name())).thenReturn(false);
        when(interestRepository.saveAndFlush(any(Interest.class))).thenAnswer(invocation -> {
            Interest saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", generatedId);
            return saved;
        });

        // when
        InterestResponse response = interestServiceImpl.create(command);

        // then
        assertThat(response.id()).isEqualTo(generatedId);
        assertThat(response.name()).isEqualTo("스포츠");
        assertThat(response.keywords()).containsExactly("축구", "야구");
        assertThat(response.subscriberCount()).isEqualTo(0L);
        assertThat(response.subscribedByMe()).isFalse();

        verify(interestRepository).saveAndFlush(interestCaptor.capture());
        Interest captured = interestCaptor.getValue();
        assertThat(captured.getName()).isEqualTo("스포츠");
        assertThat(captured.getKeywords()).hasSize(2);
    }

    @Test
    @DisplayName("사전 검사를 통과했지만 저장 시점에 이름 중복 제약 위반이 발생하면 InterestNameDuplicatedException을 던진다")
    void create_uniqueViolationDetectedAtSaveTime_throwsException() {
        // given
        InterestCreateCommand command = new InterestCreateCommand("스포츠", List.of("축구"));

        DataIntegrityViolationException original = new DataIntegrityViolationException(
                "duplicate key value violates unique constraint \"uk_interests_name\"");

        when(interestRepository.existsByName(command.name())).thenReturn(false);
        when(interestRepository.saveAndFlush(any(Interest.class))).thenThrow(original);

        // when & then
        assertThatThrownBy(() -> interestServiceImpl.create(command))
                .isInstanceOf(InterestNameDuplicatedException.class)
                .hasCause(original);

        verify(interestRepository).existsByName(command.name());
    }

    @Test
    @DisplayName("이름과 무관한 제약 위반이면 원래 예외를 그대로 던진다")
    void create_unrelatedConstraintViolation_rethrowsOriginalException() {
        // given
        InterestCreateCommand command = new InterestCreateCommand("스포츠", List.of("축구"));

        when(interestRepository.existsByName(command.name())).thenReturn(false);
        when(interestRepository.saveAndFlush(any(Interest.class)))
                .thenThrow(new DataIntegrityViolationException(
                        "null value in column \"name\" violates not-null constraint"));

        // when & then
        assertThatThrownBy(() -> interestServiceImpl.create(command))
                .isInstanceOf(DataIntegrityViolationException.class)
                .isNotInstanceOf(InterestNameDuplicatedException.class);
    }
}
