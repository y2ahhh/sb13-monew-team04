package com.codeit.sb13.monew.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.codeit.sb13.monew.global.service.AdvisoryLockService;
import com.codeit.sb13.monew.user.service.config.UserScheduleProperties;
import java.lang.reflect.Method;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.annotation.Scheduled;

@ExtendWith(MockitoExtension.class)
class UserAutoDeleteSchedulerTest {

  private static final String CRON = "0 0 1 * * *";

  @Mock
  UserService userService;
  @Mock
  AdvisoryLockService advisoryLockService;

  UserAutoDeleteScheduler userAutoDeleteScheduler;

  private UserAutoDeleteScheduler scheduler(boolean enabled) {
    return new UserAutoDeleteScheduler(
        userService,
        new UserScheduleProperties(enabled, CRON),
        advisoryLockService
    );
  }

  @BeforeEach
  void setUp() {
    userAutoDeleteScheduler = scheduler(true);
  }

  @Test
  @DisplayName("스케줄러가 비활성화되어 있으면 삭제 작업을 수행하지 않는다")
  void 비활성화시_삭제_미수행() {
    UserAutoDeleteScheduler disabled = scheduler(false);

    disabled.userAutoDeleteScheduler();

    verifyNoInteractions(advisoryLockService, userService);
  }

  @Test
  @DisplayName("락 획득에 성공하면 배치가 실행된다")
  void 락_획득에_성공하면_배치가_실행된다() {
    // given
    when(advisoryLockService.executeWithLock(eq("user-auto-delete"), any(Runnable.class)))
        .thenAnswer(invocation -> {
          Runnable task = invocation.getArgument(1);
          task.run();
          return true;
        });
    // when
    userAutoDeleteScheduler.userAutoDeleteScheduler();

    // then
    verify(advisoryLockService).executeWithLock(eq("user-auto-delete"), any(Runnable.class));
    verify(userService).autoDeleteExpiredUsers();
  }

  @Test
  @DisplayName("락 획득에 실패하면 배치가 실행되지 않는다")
  void 락_획득에_실페하면_배치가_실행되지_않는다() {
    // given
    when(advisoryLockService.executeWithLock(eq("user-auto-delete"), any(Runnable.class)))
        .thenReturn(false);

    // when & then
    assertThatCode(() -> userAutoDeleteScheduler.userAutoDeleteScheduler())
        .doesNotThrowAnyException();
    verify(advisoryLockService).executeWithLock(eq("user-auto-delete"), any(Runnable.class));
    verify(userService, never()).autoDeleteExpiredUsers();

  }

  @Test
  @DisplayName("userAutoDeleteScheduler()는 매일 01:00(Asia/Seoul)에 실행되도록 스케줄링되어 있다")
  void 스케줄_메타데이터_검증() throws NoSuchMethodException {
    Method method = UserAutoDeleteScheduler.class.getDeclaredMethod("userAutoDeleteScheduler");

    Scheduled scheduled = method.getAnnotation(Scheduled.class);

    assertThat(scheduled).isNotNull();
    assertThat(scheduled.cron()).isEqualTo("${monew.user.schedule.cron}");
    assertThat(scheduled.zone()).isEqualTo("Asia/Seoul");
  }
}
