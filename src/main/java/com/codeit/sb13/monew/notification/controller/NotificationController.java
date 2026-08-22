package com.codeit.sb13.monew.notification.controller;

import com.codeit.sb13.monew.global.dto.CursorPageResponseDto;
import com.codeit.sb13.monew.notification.controller.dto.NotificationFindRequest;
import com.codeit.sb13.monew.notification.controller.dto.NotificationResponse;
import com.codeit.sb13.monew.notification.mapper.NotificationMapper;
import com.codeit.sb13.monew.notification.service.NotificationService;
import com.codeit.sb13.monew.notification.service.dto.NotificationFindDto;
import com.codeit.sb13.monew.notification.service.dto.NotificationResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final NotificationMapper mapper;

    @GetMapping
    public ResponseEntity<CursorPageResponseDto<NotificationResponse>> findAllNotifications(
            @ModelAttribute NotificationFindRequest request,
            @RequestHeader("Monew-Request-User-ID") UUID userId
    ) {
        NotificationFindDto command = new NotificationFindDto(
                request.cursor(), request.after(), request.limit(), userId);
        CursorPageResponseDto<NotificationResult> result = notificationService.findAllNotifications(command);

        List<NotificationResponse> content = result.content().stream()
                .map(mapper::toResponse)
                .toList();

        CursorPageResponseDto<NotificationResponse> response = new CursorPageResponseDto<>(
                content,
                result.nextCursor(),
                result.nextAfter(),
                null,
                result.size(),
                result.totalElements(),
                result.hasNext()
        );

        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{notificationId}")
    public ResponseEntity<NotificationResponse> confirmNotification(@PathVariable UUID notificationId,
                                                                    @RequestHeader("Monew-Request-User-ID") UUID userId) {
        NotificationResult notificationResult = notificationService.confirmNotification(notificationId, userId);
        return ResponseEntity.ok(mapper.toResponse(notificationResult));
    }

    @PatchMapping
    public ResponseEntity<List<NotificationResponse>> confirmAllNotifications(@RequestHeader("Monew-Request-User-ID") UUID userId) {
        List<NotificationResult> notificationResults = notificationService.confirmAllNotifications(userId);
        return ResponseEntity.ok(notificationResults.stream().map(mapper::toResponse).toList());
    }
}
