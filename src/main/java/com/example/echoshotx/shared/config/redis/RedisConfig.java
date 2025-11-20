package com.example.echoshotx.shared.config.redis;

import com.example.echoshotx.video.infrastructure.redis.VideoProgressRedisListener;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;
import org.springframework.data.redis.repository.configuration.EnableRedisRepositories;
import org.springframework.data.redis.serializer.GenericToStringSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@RequiredArgsConstructor
@EnableRedisRepositories
@Configuration
public class RedisConfig {
    @Value("${spring.data.redis.host}")
    private String redisHost;

    @Value("${spring.data.redis.port}")
    private int redisPort;

    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        return new LettuceConnectionFactory(redisHost, redisPort);
    }

    @Primary
    @Bean
    public RedisTemplate<?, ?> redisTemplate() {
        RedisTemplate<byte[], byte[]> redisTemplate = new RedisTemplate<>();
        redisTemplate.setKeySerializer(new StringRedisSerializer());
        redisTemplate.setValueSerializer(new StringRedisSerializer());
        redisTemplate.setHashKeySerializer(new StringRedisSerializer());
        redisTemplate.setHashValueSerializer(new GenericToStringSerializer<Long>(Long.class));
        redisTemplate.setConnectionFactory(redisConnectionFactory());
        return redisTemplate;
    }

    // == Redis Pub/Sub 설정 ==

    /**
     * 비디오 진행률 업데이트를 위한 Redis 채널 토픽.
     */
    @Bean
    public ChannelTopic videoProgressTopic() {
        return new ChannelTopic("video:progress:updates");
    }

    /**
     * Redis 메시지 리스너 어댑터.
     * VideoProgressRedisListener의 onMessage 메서드를 호출합니다.
     */
    @Bean
    public MessageListenerAdapter videoProgressListenerAdapter(VideoProgressRedisListener listener) {
        return new MessageListenerAdapter(listener, "onMessage");
    }

    /**
     * Redis 메시지 리스너 컨테이너.
     * Redis Pub/Sub 메시지를 수신하여 리스너에게 전달합니다.
     */
    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            MessageListenerAdapter videoProgressListenerAdapter,
            ChannelTopic videoProgressTopic) {

        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(videoProgressListenerAdapter, videoProgressTopic);
        return container;
    }
}