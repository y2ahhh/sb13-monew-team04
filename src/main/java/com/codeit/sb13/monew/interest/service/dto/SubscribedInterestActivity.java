package com.codeit.sb13.monew.interest.service.dto;

import com.codeit.sb13.monew.interest.domain.Interest;
import com.codeit.sb13.monew.interest.domain.Keyword;
import com.codeit.sb13.monew.interest.repository.dto.SubscribedInterestActivityProjection;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record SubscribedInterestActivity(
        UUID id,
        UUID interestId,
        String interestName,
        List<String> interestKeywords,
        Long interestSubscriberCount,
        LocalDateTime createdAt
) {

    public static SubscribedInterestActivity from(SubscribedInterestActivityProjection projection) {
        Interest interest = projection.interest();
        return new SubscribedInterestActivity(
                projection.id(),
                interest.getId(),
                interest.getName(),
                getKeywords(interest),
                projection.interestSubscriberCount(),
                interest.getCreatedAt()
        );
    }

    private static List<String> getKeywords(Interest interest) {
        return interest.getKeywords()
                .stream()
                .map(Keyword::getKeyword)
                .toList();
    }
}
