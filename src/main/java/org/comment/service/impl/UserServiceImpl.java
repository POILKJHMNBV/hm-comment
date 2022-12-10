package org.comment.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.comment.dto.Result;
import org.comment.entity.User;
import org.comment.mapper.UserMapper;
import org.comment.service.IUserService;
import org.comment.utils.RegexUtils;
import org.comment.utils.SystemConstants;
import org.comment.utils.UserHolder;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.comment.utils.RedisConstants.USER_SIGN_KEY;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public String code(String phone) {
        // 1.校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            // 手机号无效
            return null;
        }
        // 2.生成并返回验证码
        return RandomUtil.randomNumbers(6);
    }

    @Override
    public User login(String phone) {
        // 1.查询用户信息
        User user = query().eq("phone", phone).one();

        // 2.判断用户是否存在
        if (user == null) {
            // 用户不存在，创建新用户
            user = createUserWithPhone(phone);
        }

        // 返回用户信息
        return user;
    }

    @Override
    public Result sign() {
        // 1.获取当前登录用户
        Long userId = UserHolder.getUser().getId();

        // 2.获取当前日期
        LocalDateTime now = LocalDateTime.now();

        // 3.拼接key
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + userId + keySuffix;

        // 4.获取今天是本月的第几天
        int today = now.getDayOfMonth();

        // 5.签到
        Boolean isSuccess = stringRedisTemplate.opsForValue().setBit(key, today - 1, true);
        if (Boolean.FALSE.equals(isSuccess)) {
            return Result.ok("签到成功！");
        } else {
            return Result.ok("签到失败！");
        }
    }

    @Override
    public Result signCount() {
        // 1.获取当前登录用户
        Long userId = UserHolder.getUser().getId();

        // 2.获取当前日期
        LocalDateTime now = LocalDateTime.now();

        // 3.拼接key
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + userId + keySuffix;

        // 4.获取今天是本月的第几天
        int today = now.getDayOfMonth();

        // 5.获取用户到当前时间的签到记录
        List<Long> result = stringRedisTemplate.opsForValue().bitField(
                key,
                BitFieldSubCommands.create().get(BitFieldSubCommands.BitFieldType.unsigned(today)).valueAt(0)
        );
        if (result == null || result.isEmpty()) {
            return Result.ok(0);
        }
        Long num = result.get(0);
        if (num == null || num == 0) {
            return Result.ok(0);
        }

        // 6.循环遍历统计用户的连续签到天数
        int count = 0;
        while (true) {
            if ((num & 1) == 0) {
                break;
            } else {
                count++;
            }
            num >>>= 1;
        }
        return Result.ok(count);
    }

    private User createUserWithPhone(String phone) {
        // 1.创建用户
        User user = new User();
        user.setPhone(phone);
        user.setNickName(SystemConstants.USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));

        // 2.保存用户信息
        save(user);

        return user;
    }
}
