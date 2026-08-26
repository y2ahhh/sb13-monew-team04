package com.codeit.sb13.monew.notification.domain;

import com.fasterxml.jackson.annotation.JsonValue;

public enum ResourceType {
    INTEREST,
    COMMENT;

    @JsonValue
    public String toJson() {
        return name().toLowerCase();
    }
}
