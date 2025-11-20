package com.example.echoshotx.shared.security.filter;

import com.example.echoshotx.shared.exception.payload.code.ErrorStatus;
import com.example.echoshotx.shared.exception.payload.dto.ApiResponseDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * AI 서버 웹훅 엔드포인트에 대한 API Key 인증 필터.
 *
 * <p>X-API-Key 헤더를 검증하여 AI 서버만 웹훅 엔드포인트에 접근할 수 있도록 제한합니다.
 *
 * <ul>
 *   <li>적용 경로: /videos/webhook/**</li>
 *   <li>인증 방식: X-API-Key 헤더 검증</li>
 *   <li>실패 시: 401 Unauthorized 응답</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

  private static final String API_KEY_HEADER = "X-API-Key";
  private static final String WEBHOOK_PATH_PREFIX = "/videos/webhook/";

  @Value("${ai.server.webhook.api-key:}")
  private String expectedApiKey;

  private final ObjectMapper objectMapper;

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    String requestUri = request.getRequestURI();

    // 웹훅 경로가 아니면 필터를 건너뜀
    if (!requestUri.startsWith(WEBHOOK_PATH_PREFIX)) {
      filterChain.doFilter(request, response);
      return;
    }

    // API Key 검증
    String apiKey = request.getHeader(API_KEY_HEADER);

    if (apiKey == null || apiKey.isBlank()) {
      log.warn("Missing API Key in webhook request: uri={}, ip={}", requestUri, getClientIp(request));
      sendErrorResponse(response, ErrorStatus._UNAUTHORIZED, "API Key is required");
      return;
    }

    if (expectedApiKey.isBlank()) {
      log.error("AI server webhook API Key is not configured in application.yml");
      sendErrorResponse(response, ErrorStatus._INTERNAL_SERVER_ERROR, "Server configuration error");
      return;
    }

    if (!apiKey.equals(expectedApiKey)) {
      log.warn(
          "Invalid API Key in webhook request: uri={}, ip={}, providedKey={}",
          requestUri,
          getClientIp(request),
          maskApiKey(apiKey));
      sendErrorResponse(response, ErrorStatus._UNAUTHORIZED, "Invalid API Key");
      return;
    }

    log.debug("API Key validated successfully for webhook: uri={}", requestUri);
    filterChain.doFilter(request, response);
  }

  /**
   * 에러 응답을 JSON 형식으로 전송합니다.
   */
  private void sendErrorResponse(HttpServletResponse response, ErrorStatus errorStatus, String message)
      throws IOException {
    response.setStatus(errorStatus.getHttpStatus().value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());

    ApiResponseDto<Void> errorResponse =
        ApiResponseDto.onFailure(errorStatus.getCode(), message, null);

    String jsonResponse = objectMapper.writeValueAsString(errorResponse);
    response.getWriter().write(jsonResponse);
  }

  /**
   * 클라이언트 IP 주소를 가져옵니다.
   */
  private String getClientIp(HttpServletRequest request) {
    String ip = request.getHeader("X-Forwarded-For");
    if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
      ip = request.getHeader("Proxy-Client-IP");
    }
    if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
      ip = request.getHeader("WL-Proxy-Client-IP");
    }
    if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
      ip = request.getRemoteAddr();
    }
    return ip;
  }

  /**
   * API Key를 마스킹하여 로그에 출력합니다.
   */
  private String maskApiKey(String apiKey) {
    if (apiKey == null || apiKey.length() < 8) {
      return "***";
    }
    return apiKey.substring(0, 4) + "****" + apiKey.substring(apiKey.length() - 4);
  }
}
