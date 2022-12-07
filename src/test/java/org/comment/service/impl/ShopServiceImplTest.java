package org.comment.service.impl;

import org.comment.CommentApplicationTests;
import org.comment.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

import static org.comment.utils.RedisConstants.LOCK_DEFAULT_TTL;


class ShopServiceImplTest extends CommentApplicationTests {

    @Resource
    private ShopServiceImpl shopService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Test
    void queryShopById() {
        long id = redisIdWorker.nextId("voucherOrder");
        System.out.println("id = " + id);
    }

    @Test
    void saveShop() {
        shopService.saveShop(4L, 1800);
    }

    @Test
    void update() {
    }

    @Test
    void testRedis() {
        stringRedisTemplate.opsForValue().setIfAbsent("LOCK_SHOP_KEY_1", "1", LOCK_DEFAULT_TTL, TimeUnit.MINUTES);
    }
}