package com.codeit.sb13.monew.global.config;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class IpAddress {

    private final ObjectProvider<HttpServletRequest> requestProvider;

    public String getClientIp() {

        return getClientIp(requestProvider.getObject());
    }

    public String getClientIp(HttpServletRequest request) {

        String ip = request.getHeader("X-FORWARDED-FOR");

        if(StringUtils.hasText(ip) && !"unknown".equalsIgnoreCase(ip)){
            return ip.split(",")[0].trim();
        }

        ip = request.getHeader("X-Real-IP");

        if(StringUtils.hasText(ip) && !"unknown".equalsIgnoreCase(ip)){
            return ip;
        }

        return request.getRemoteAddr();
    }
}
