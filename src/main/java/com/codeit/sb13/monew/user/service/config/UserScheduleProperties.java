package com.codeit.sb13.monew.user.service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 사용자 자동 삭제 스케줄러({@code UserAutoDeleteScheduler})의 활성화 여부와 실행 주기를 담는다.
 *
 * <p>기사 수집/알림 정리 스케줄러와 같은 방식으로, {@code monew.user.schedule} 프리픽스 아래
 * 설정을 바인딩한다.</p>
 *
 * @param enabled 사용자 자동 삭제 스케줄러 활성화 여부
 * @param cron 사용자 자동 삭제를 실행할 cron 표현식
 */
@ConfigurationProperties(prefix = "monew.user.schedule")
public record UserScheduleProperties(
        boolean enabled,
        String cron
) {
}
