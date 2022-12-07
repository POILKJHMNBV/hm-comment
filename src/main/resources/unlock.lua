-- 锁的key
-- 获取锁中的线程标识
-- 比较线程标识与锁中的标识是否一致
-- 一致，则释放锁
if (redis.call('GET', KEYS[1]) == ARGV[1]) then
    return redis.call('DEL', KEYS[1])
end
return 0