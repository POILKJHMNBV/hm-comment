package org.comment;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;

@Slf4j
class BitMapTest extends CommentApplicationTests {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Test
    void testAdd() {
        Boolean isSuccess = stringRedisTemplate.opsForValue().setBit("sign:848:202212", 0, true);
        log.info("isSuccess = {}", isSuccess);
    }
}
