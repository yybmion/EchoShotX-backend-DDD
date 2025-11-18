package com.example.echoshotx.shared.security.webhook;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Webhook 요청의 서명을 검증하는 유틸리티 클래스.
 *
 * <p>HMAC-SHA256 알고리즘을 사용하여 AI 서버로부터 받은 웹훅 요청의 무결성을 검증합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebhookSignatureValidator {

  @Value("${webhook.secret-key:default-secret-key-change-in-production}")
  private String secretKey;

  private static final String HMAC_SHA256_ALGORITHM = "HmacSHA256";

  /**
   * 웹훅 요청의 서명을 검증합니다.
   *
   * @param payload 요청 본문 (JSON 문자열)
   * @param receivedSignature AI 서버로부터 받은 서명 (헤더: X-Webhook-Signature)
   * @return 서명이 유효하면 true, 그렇지 않으면 false
   */
  public boolean validateSignature(String payload, String receivedSignature) {
    if (payload == null || receivedSignature == null) {
      log.warn("Payload or signature is null");
      return false;
    }

    try {
      String expectedSignature = generateSignature(payload);
      boolean isValid = constantTimeEquals(expectedSignature, receivedSignature);

      if (!isValid) {
        log.warn(
            "Webhook signature validation failed: expected={}, received={}",
            expectedSignature,
            receivedSignature);
      }

      return isValid;

    } catch (Exception e) {
      log.error("Error validating webhook signature", e);
      return false;
    }
  }

  /**
   * 주어진 페이로드에 대한 HMAC-SHA256 서명을 생성합니다.
   *
   * @param payload 서명할 데이터 (JSON 문자열)
   * @return 16진수 형식의 서명 문자열
   */
  public String generateSignature(String payload) {
    try {
      Mac mac = Mac.getInstance(HMAC_SHA256_ALGORITHM);
      SecretKeySpec secretKeySpec =
          new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), HMAC_SHA256_ALGORITHM);
      mac.init(secretKeySpec);

      byte[] signatureBytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(signatureBytes);

    } catch (NoSuchAlgorithmException | InvalidKeyException e) {
      log.error("Error generating signature", e);
      throw new WebhookSignatureException("Failed to generate signature", e);
    }
  }

  /**
   * Timing attack를 방지하기 위한 constant-time 문자열 비교.
   *
   * @param a 비교할 첫 번째 문자열
   * @param b 비교할 두 번째 문자열
   * @return 두 문자열이 같으면 true
   */
  private boolean constantTimeEquals(String a, String b) {
    if (a == null || b == null) {
      return false;
    }

    byte[] aBytes = a.getBytes(StandardCharsets.UTF_8);
    byte[] bBytes = b.getBytes(StandardCharsets.UTF_8);

    if (aBytes.length != bBytes.length) {
      return false;
    }

    int result = 0;
    for (int i = 0; i < aBytes.length; i++) {
      result |= aBytes[i] ^ bBytes[i];
    }

    return result == 0;
  }

  /** Webhook 서명 생성/검증 실패 예외 */
  public static class WebhookSignatureException extends RuntimeException {
    public WebhookSignatureException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
