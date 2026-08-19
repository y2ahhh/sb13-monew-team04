package com.codeit.sb13.monew.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.retry.annotation.EnableRetry;

/**
 * {@code @Retryable}을 프록시 기반 AOP로 활성화한다.
 *
 * <p>{@code @Retryable}과 {@code @Transactional}을 같은 메서드에 함께 적용하면, 두
 * 어드바이저 중 어느 쪽이 바깥에서 감싸는지에 따라 동작이 크게 달라진다. 재시도
 * 어드바이저가 바깥에 있어야 재시도할 때마다 트랜잭션 어드바이저를 다시 통과해
 * 새 트랜잭션을 시작한다. 반대로 트랜잭션 어드바이저가 바깥에 있으면, 직렬화
 * 실패로 이미 무효화된 트랜잭션 안에서 메서드 본문만 다시 실행하게 되어 재시도가
 * 사실상 무의미해진다.</p>
 *
 * <p>{@code @EnableTransactionManagement}가 등록하는 트랜잭션 어드바이저는 별도
 * 설정이 없으면 {@link Ordered#LOWEST_PRECEDENCE}(가장 낮은 우선순위, 가장 안쪽)로
 * 동작한다. 여기서 재시도 어드바이저의 순서를 {@link Ordered#HIGHEST_PRECEDENCE}로
 * 명시해, 어느 쪽 설정이 먼저 로딩되는지와 무관하게 재시도가 항상 트랜잭션보다
 * 바깥에서 감싸도록 고정한다.</p>
 */
@Configuration
@EnableRetry(order = Ordered.HIGHEST_PRECEDENCE)
public class RetryConfig {
}
