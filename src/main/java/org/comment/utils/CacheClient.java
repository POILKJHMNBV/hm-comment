package org.comment.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static org.comment.utils.RedisConstants.*;

/**
 * Redis工具类，用于解决缓存缓存穿透、缓存击穿
 */
@Slf4j
@Component
public class CacheClient {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 设置过期时间
     * @param key   键
     * @param value 值
     * @param time  过期时间
     * @param timeUnit  时间单位
     */
    private void set(String key, Object value, Long time, TimeUnit timeUnit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, timeUnit);
    }

    /**
     * 设置逻辑过期时间
     * @param key   键
     * @param value 值
     * @param time  逻辑过期时间
     * @param timeUnit  时间单位
     */
    private void setWithLogicalExpire(String key, Object value, Long time, TimeUnit timeUnit) {
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(timeUnit.toSeconds(time)));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData), time, timeUnit);
    }

    /**
     * 向缓存中存入空值解决缓存穿透
     * @param keyPrefix 业务前缀
     * @param id 具体业务id
     * @param type 要查询的实体类
     * @param dbFallback 查询数据库的函数
     * @param time 超时时间
     * @param timeUnit 时间单位
     * @param <R> 实体类
     * @param <ID> id
     * @return 实体类
     */
    public <R, ID> R queryWithPassThrough(String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit timeUnit) {

        String key = keyPrefix + id;

        // 1.从redis中查询数据
        String json = stringRedisTemplate.opsForValue().get(key);

        // 2.判断redis中是否存在数据
        if (StrUtil.isNotBlank(json)) {
            // redis中存在，直接返回
            return JSONUtil.toBean(json, type);
        }

        if (json != null) {
            // 缓存中存在空值
            return null;
        }

        // 3.不存在，查询数据库
        R r = dbFallback.apply(id);
        if (r == null) {
            // 数据库中不存在，向缓存中写入空值，避免缓存穿透
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }

        // 4.数据库中存在，将数据写入redis
        this.set(key, r, time, timeUnit);

        // 5.返回数据
        return r;
    }

    /**
     * 利用互斥锁解决缓存击穿
     * @param keyPrefix 业务前缀
     * @param id 具体业务id
     * @param type 要查询的实体类
     * @param dbFallback 查询数据库的函数
     * @param time 超时时间
     * @param timeUnit 时间单位
     * @param <R> 实体类
     * @param <ID> id
     * @return 实体类
     */
    public <R, ID> R queryWithMutex(String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit timeUnit) {
        String key = keyPrefix + id;

        // 1.从redis中查询数据
        String json = stringRedisTemplate.opsForValue().get(key);

        // 2.判断redis中是否存在数据
        if (StrUtil.isNotBlank(json)) {
            // redis中存在，直接返回
            return JSONUtil.toBean(json, type);
        }

        if (json != null) {
            // 缓存中存在空值
            return null;
        }

        // 3.不存在，查询数据库，开始缓存重建
        String lockKey = "LOCK_" + type.getSimpleName().toUpperCase() + "_KEY_" + id;
        R r;
        try {
            // 3.1 获取互斥锁
            boolean lock = tryLock(lockKey);

            // 3.2 判断获取互斥锁是否成功
            if (!lock) {
                // 获取互斥锁失败，线程休眠一会儿
                Thread.sleep(50);
                return queryWithMutex(keyPrefix, id, type, dbFallback, time, timeUnit);
            }

            // 3.3 成功获取到互斥锁，查询数据库，重建缓存
            // 重新检查redis中是否存在数据，防止重复更新缓存
            json = stringRedisTemplate.opsForValue().get(key);
            if (StrUtil.isNotBlank(json)) {
                // redis中存在，直接返回
                return JSONUtil.toBean(json, type);
            }

            r = dbFallback.apply(id);
            if (r == null) {
                // 数据库中不存在，向缓存中写入空值，避免缓存穿透
                this.set(key, "", CACHE_NULL_TTL, timeUnit);
                return null;
            }

            // 数据库中存在，将数据写入redis
            this.set(key, r, time, timeUnit);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            // 3.4 释放互斥锁，保证互斥锁在任何时候都能够释放
            unLock(lockKey);
        }
        // 5.返回商铺数据
        return r;
    }

    private boolean tryLock(String key) {
        // 利用setnx命令模拟互斥锁
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", LOCK_DEFAULT_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    private void unLock(String key) {
        stringRedisTemplate.delete(key);
    }

    // 缓存重建线程池
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    /**
     * 利用逻辑过期解决缓存击穿
     * @param keyPrefix 业务前缀
     * @param id 具体业务id
     * @param type 要查询的实体类
     * @param dbFallback 查询数据库的函数
     * @param time 逻辑过期时间
     * @param timeUnit 时间单位
     * @param <R> 实体类
     * @param <ID> id
     * @return 实体类
     */
    public <R, ID> R queryWithLogicalExpire(String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit timeUnit) {
        String key = keyPrefix + id;

        // 1.从redis中查询数据
        String redisDataJson = stringRedisTemplate.opsForValue().get(key);

        // 2.判断redis中是否存在数据
        if (redisDataJson == null) {
            // redis中不存在，直接返回
            return null;
        }

        // 3.存在
        // 3.1 判断缓存是否过期
        RedisData redisData = JSONUtil.toBean(redisDataJson, RedisData.class);
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        if (redisData.getExpireTime().isAfter(LocalDateTime.now())) {
            // 未过期，直接返回
            return r;
        }

        // 已过期
        // 3.2 尝试获取互斥锁
        String lockKey = "LOCK_" + type.getSimpleName().toUpperCase() + "_KEY_" + id;;
        boolean lock = tryLock(lockKey);

        if (lock) {
            // 成功获取互斥锁
            // 重新检查缓存是否过期，防止重复更新缓存
            redisDataJson = stringRedisTemplate.opsForValue().get(key);
            redisData = JSONUtil.toBean(redisDataJson, RedisData.class);
            if (redisData.getExpireTime().isAfter(LocalDateTime.now())) {
                // 缓存已经更新，直接返回
                return JSONUtil.toBean((JSONObject) redisData.getData(), type);
            }

            // 3.3 开启独立线程更新缓存数据
            try {
                CACHE_REBUILD_EXECUTOR.submit(() -> this.save(key, id, dbFallback, time, timeUnit));
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                // 释放锁
                unLock(lockKey);
            }
        }

        // 4.返回过期数据
        return r;
    }

    // 提前将数据导入redis，模拟缓存击穿
    private <R, ID> void save(String key, ID id, Function<ID, R> dbFallback, Long time, TimeUnit timeUnit) {
        // 1.查询店铺数据
        R r = dbFallback.apply(id);

        // 2.封装逻辑过期时间
        // 3.写入redis
        this.setWithLogicalExpire(key, r, time, timeUnit);
    }
}
