package org.comment;

import org.comment.dto.UserDTO;
import org.comment.utils.UserHolder;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import javax.annotation.Resource;
import java.util.Collections;

import static org.comment.utils.RedisConstants.BLOG_LIKED_KEY;

class LuaTest extends CommentApplicationTests {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Test
    void testTonumber() {
        DefaultRedisScript<Long> SCRIPT = new DefaultRedisScript<>();
        SCRIPT.setLocation(new ClassPathResource("tonumber.lua"));
        SCRIPT.setResultType(Long.class);
        Long result = stringRedisTemplate.execute(SCRIPT, Collections.emptyList());
        System.out.println("result = " + result);

        String value = stringRedisTemplate.opsForValue().get("secKill:stock:12");
        System.out.println("value = " + value);
    }

    @Test
    void testSetStock() {
        ThreadLocal<UserDTO> tl = new ThreadLocal<>();
        System.out.println(tl.get());
    }
}
