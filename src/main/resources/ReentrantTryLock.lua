local key = KEYS[1]; -- 锁的key
local threadId = ARGV[1]; -- 线程唯一标识
local releaseTime = ARGV[2]; -- 锁的自动释放时间
-- 判断锁是否存在
if (redis.call('EXISTS', key) == 0) then
    -- 不存在，获取锁
    redis.call('HSET', key, threadId, '1');
    -- 设置有效期
    redis.call('EXPIRE', key, releaseTime);
    return 1; -- 返回结果
end;
-- 锁已经存在，判断threadId是否是自己
if (redis.call('HEXISTS', key, threadId) == 1) then
    -- 存在，获取锁.重入次数+1
    redis.call('HINCRBY', key, threadId, 1);
    -- 设置有效期
    redis.call('EXPIRE', key, releaseTime);
    return 1; -- 返回结果
end;
return 0;   -- 获取锁失败