package com.codeit.sb13.monew.interest.repository.dto;

import com.codeit.sb13.monew.interest.domain.Interest;

import java.util.UUID;

public record SubscribedInterestActivityProjection(
        UUID id,
        Interest interest,
        Long interestSubscriberCount
) {

}
