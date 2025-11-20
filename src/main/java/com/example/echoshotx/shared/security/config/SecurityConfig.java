package com.example.echoshotx.shared.security.config;

import com.example.echoshotx.member.infrastructure.auth.handler.CustomOAuth2LoginFailureHandler;
import com.example.echoshotx.member.infrastructure.auth.handler.CustomOAuth2LoginSuccessHandler;
import com.example.echoshotx.member.infrastructure.auth.service.CustomOAuth2UserService;
import com.example.echoshotx.shared.security.exception.JwtAccessDeniedHandler;
import com.example.echoshotx.shared.security.exception.JwtAuthenticationEntryPoint;
import com.example.echoshotx.shared.security.filter.JwtAuthenticationFilter;
import com.example.echoshotx.shared.security.filter.JwtExceptionFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.annotation.web.configurers.HttpBasicConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.util.List;

import static org.springframework.security.web.util.matcher.AntPathRequestMatcher.antMatcher;

@RequiredArgsConstructor
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    private final CustomOAuth2UserService customOAuth2UserService;
    private final CustomOAuth2LoginSuccessHandler customOAuth2LoginSuccessHandler;
    private final CustomOAuth2LoginFailureHandler customOAuth2LoginFailureHandler;

    private final JwtAccessDeniedHandler jwtAccessDeniedHandler;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtExceptionFilter jwtExceptionFilter;
    private final com.example.echoshotx.shared.security.filter.ApiKeyAuthenticationFilter apiKeyAuthenticationFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity httpSecurity) throws Exception {
        configureCorsAndSecurity(httpSecurity);
        configureAuth(httpSecurity);
        configureOAuth2(httpSecurity);
        configureExceptionHandling(httpSecurity);
        addFilter(httpSecurity);

        return httpSecurity.build();
    }

    private void configureExceptionHandling(HttpSecurity httpSecurity) throws Exception {
        httpSecurity
                .exceptionHandling(httpSecurityExceptionHandlingConfigurer -> httpSecurityExceptionHandlingConfigurer
                        .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                        .accessDeniedHandler(jwtAccessDeniedHandler));
    }


    private void configureCorsAndSecurity(HttpSecurity httpSecurity) throws Exception {
        httpSecurity
                .headers(
                        httpSecurityHeadersConfigurer ->
                                httpSecurityHeadersConfigurer.frameOptions(
                                        HeadersConfigurer.FrameOptionsConfig::disable
                                )
                )
                // stateless한 rest api 이므로 csrf 공격 옵션 비활성화
                .csrf(AbstractHttpConfigurer::disable)
                // 로그인 폼 기본 설정(커스텀 x)
                .formLogin(Customizer.withDefaults())
                // 팝업 로그인 x
                .httpBasic(HttpBasicConfigurer::disable)
                // 프론트 혹은 다른 외부 api와의 통신 허용
                .cors(Customizer.withDefaults())
                // rest api를 위한 stateless 설정
                .sessionManagement(configurer -> configurer
                        .sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                );
    }

    private void configureAuth(HttpSecurity httpSecurity) throws Exception {
        httpSecurity
                .authorizeHttpRequests(Customizer.withDefaults())
                .authorizeHttpRequests(authorizeRequest -> {
                    authorizeRequest
                            .requestMatchers("/", "/.well-known/**", "/css/**",
                                    "/*.ico", "/error", "/images/**").permitAll()
                            .requestMatchers(permitAllRequest()).permitAll()        //비인증 api 허용 처리
                            .requestMatchers(authRelatedEndpoints()).permitAll()
                            .requestMatchers(additionalSwaggerRequests()).permitAll()
                            .anyRequest().permitAll();      //지정하지 않은 url의 경우 인증 처리
                });
    }

    private RequestMatcher[] permitAllRequest() {
        List<RequestMatcher> requestMatchers = List.of(
                antMatcher(HttpMethod.GET, "/")     //list of 에 permit url 추가
        );
        return requestMatchers.toArray(RequestMatcher[]::new);
    }

    private RequestMatcher[] authRelatedEndpoints() {
        List<RequestMatcher> requestMatchers = List.of(
                antMatcher("/api/tokens/**")
        );
        return requestMatchers.toArray(RequestMatcher[]::new);
    }

    private RequestMatcher[] additionalSwaggerRequests() {
        List<RequestMatcher> requestMatchers = List.of(
                antMatcher("/swagger-ui/**"),
                antMatcher("/swagger-ui"),
                antMatcher("/swagger-ui.html"),
                antMatcher("/swagger/**"),
                antMatcher("/swagger-resources/**"),
                antMatcher("/v3/api-docs/**"),
                antMatcher("/profile")

        );
        return requestMatchers.toArray(RequestMatcher[]::new);
    }

    private void configureOAuth2(HttpSecurity httpSecurity) throws Exception {
        httpSecurity
                .oauth2Login((oauth2) -> oauth2
                        .userInfoEndpoint((userInfoEndpointConfig -> userInfoEndpointConfig
                                .userService(customOAuth2UserService)))
                        .successHandler(customOAuth2LoginSuccessHandler::onAuthenticationSuccess)
                        .failureHandler(customOAuth2LoginFailureHandler::onAuthenticationFailure)
                );
    }

    private void addFilter(HttpSecurity httpSecurity) {
        httpSecurity
                .addFilterBefore(apiKeyAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtExceptionFilter, JwtAuthenticationFilter.class);
    }

}