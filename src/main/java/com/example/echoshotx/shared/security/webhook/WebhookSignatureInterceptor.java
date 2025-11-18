package com.example.echoshotx.shared.security.webhook;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.nio.charset.StandardCharsets;

/**
 * Webhook 요청의 서명을 검증하는 인터셉터.
 *
 * <p>AI 서버로부터 받은 웹훅 요청의 무결성을 HMAC-SHA256 서명으로 검증합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebhookSignatureInterceptor implements HandlerInterceptor {

  private final WebhookSignatureValidator signatureValidator;

  private static final String SIGNATURE_HEADER = "X-Webhook-Signature";

  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler)
      throws Exception {

    // Webhook 요청이 아니면 검증 스킵
    if (!isWebhookRequest(request)) {
      return true;
    }

    // 서명 헤더 확인
    String receivedSignature = request.getHeader(SIGNATURE_HEADER);
    if (receivedSignature == null || receivedSignature.isEmpty()) {
      log.warn("Webhook signature header is missing: path={}", request.getRequestURI());
      response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      response.getWriter().write("Missing webhook signature");
      return false;
    }

    // 요청 본문 읽기 (ContentCachingRequestWrapper 사용)
    if (!(request instanceof ContentCachingRequestWrapper)) {
      log.error("Request is not wrapped with ContentCachingRequestWrapper");
      response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
      return false;
    }

    ContentCachingRequestWrapper wrappedRequest = (ContentCachingRequestWrapper) request;
    String payload = new String(wrappedRequest.getContentAsByteArray(), StandardCharsets.UTF_8);

    // 서명 검증
    boolean isValid = signatureValidator.validateSignature(payload, receivedSignature);

    if (!isValid) {
      log.warn(
          "Invalid webhook signature: path={}, signature={}",
          request.getRequestURI(),
          receivedSignature);
      response.setStatus(HttpServletResponse.SC_FORBIDDEN);
      response.getWriter().write("Invalid webhook signature");
      return false;
    }

    log.debug("Webhook signature validated successfully: path={}", request.getRequestURI());
    return true;
  }

  /**
   * Webhook 요청인지 확인합니다.
   */
  private boolean isWebhookRequest(HttpServletRequest request) {
    String uri = request.getRequestURI();
    return uri != null && uri.contains("/webhook");
  }
}
