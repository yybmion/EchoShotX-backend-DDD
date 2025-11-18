package com.example.echoshotx.shared.config;

import com.example.echoshotx.shared.security.webhook.WebhookSignatureInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.util.ContentCachingRequestWrapper;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;

/**
 * Webhook 보안 설정.
 *
 * <p>Webhook 요청의 서명 검증을 위한 인터셉터 등록 및 요청 본문 캐싱 필터 설정.
 */
@Configuration
@RequiredArgsConstructor
public class WebhookSecurityConfig implements WebMvcConfigurer {

  private final WebhookSignatureInterceptor webhookSignatureInterceptor;

  /**
   * Webhook 서명 검증 인터셉터를 등록합니다.
   */
  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry
        .addInterceptor(webhookSignatureInterceptor)
        .addPathPatterns("/videos/webhook/**"); // Webhook 경로에만 적용
  }

  /**
   * 요청 본문을 여러 번 읽을 수 있도록 ContentCachingRequestWrapper를 적용하는 필터.
   *
   * <p>인터셉터에서 서명 검증을 위해 요청 본문을 읽은 후, 컨트롤러에서도 읽을 수 있도록 합니다.
   */
  @Bean
  public FilterRegistrationBean<ContentCachingFilter> contentCachingFilter() {
    FilterRegistrationBean<ContentCachingFilter> registrationBean = new FilterRegistrationBean<>();
    registrationBean.setFilter(new ContentCachingFilter());
    registrationBean.addUrlPatterns("/videos/webhook/*");
    registrationBean.setOrder(1); // 가장 먼저 실행
    return registrationBean;
  }

  /**
   * 요청 본문 캐싱 필터.
   */
  public static class ContentCachingFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
        throws IOException, ServletException {

      if (request instanceof HttpServletRequest httpRequest) {
        ContentCachingRequestWrapper wrappedRequest =
            new ContentCachingRequestWrapper(httpRequest);
        chain.doFilter(wrappedRequest, response);
      } else {
        chain.doFilter(request, response);
      }
    }
  }
}
