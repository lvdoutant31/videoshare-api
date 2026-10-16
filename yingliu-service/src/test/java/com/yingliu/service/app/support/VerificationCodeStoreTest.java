package com.yingliu.service.app.support;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import java.util.concurrent.*;
import java.util.stream.IntStream;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class VerificationCodeStoreTest {
    @Test void stateCanBeConsumedOnlyOnceEvenConcurrently() throws Exception {
        var store = new InMemoryVerificationCodeStore();
        store.set("state", "pending", 300);
        var executor = Executors.newFixedThreadPool(8);
        try {
            var futures = executor.invokeAll(IntStream.range(0, 32)
                    .<Callable<String>>mapToObj(i -> () -> store.consume("state")).toList());
            int successes = 0;
            for (var future : futures) if (future.get() != null) successes++;
            assertThat(successes).isEqualTo(1);
        } finally { executor.shutdownNow(); }
    }

    @Test void expiredStateIsRejected() {
        var store = new InMemoryVerificationCodeStore();
        store.set("expired", "pending", 0);
        assertThat(store.consume("expired")).isNull();
    }

    @SuppressWarnings("unchecked")
    @Test void redisUsesAtomicGetAndDelete() {
        var redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.getAndDelete("state")).thenReturn("pending");
        assertThat(new RedisVerificationCodeStore(redis).consume("state")).isEqualTo("pending");
        verify(values).getAndDelete("state");
        verifyNoMoreInteractions(values);
    }
}
