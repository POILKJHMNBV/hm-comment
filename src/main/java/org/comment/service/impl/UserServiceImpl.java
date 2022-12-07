package org.comment.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.comment.entity.User;
import org.comment.mapper.UserMapper;
import org.comment.service.IUserService;
import org.comment.utils.RegexUtils;
import org.comment.utils.SystemConstants;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
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
