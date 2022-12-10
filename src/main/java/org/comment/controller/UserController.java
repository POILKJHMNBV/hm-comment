package org.comment.controller;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.comment.dto.LoginFormDTO;
import org.comment.dto.Result;
import org.comment.dto.UserDTO;
import org.comment.entity.User;
import org.comment.entity.UserInfo;
import org.comment.service.IUserInfoService;
import org.comment.service.IUserService;
import org.comment.utils.RegexUtils;
import org.comment.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import javax.validation.Valid;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.comment.utils.RedisConstants.*;

@Slf4j
@RestController
@RequestMapping("/user")
@Tag(name = "UserController", description = "用户Web接口")
public class UserController {

    @Resource
    private IUserService userService;

    @Resource
    private IUserInfoService userInfoService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private String token;

    private final ArrayList<String> tokenList = new ArrayList<>();

    /**
     * 发送验证码
     * @param phone 手机号
     * @param session session
     * @return 验证码
     */
    @PostMapping("/code")
    @Operation(summary = "向用户发送验证码")
    public Result sendCode(@RequestParam("phone") String phone, HttpSession session) {
        // 1.生成验证码
        String code = userService.code(phone);
        if (code == null) {
            return Result.fail("手机号码有误，请重新输入！");
        }

        // 2.保存手机号和验证码到session
        /*session.setAttribute(SystemConstants.CODE, code);
        session.setAttribute(SystemConstants.PHONE, phone);
        session.setMaxInactiveInterval(300);*/     // 设置session的失效时间，5分钟

        // 2.保存手机号和验证码到redis
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);  // 设置验证码失效时间（2分钟）

        // 3.发送验证码
        // TODO
        log.info("短信验证码已发送，验证码：code={}", code);
        return Result.ok();
    }

    /**
     * 登录
     * @param loginForm 登录参数：包括手机号、验证码或手机号、密码
     * @return 登录结果
     */
    @PostMapping("/login")
    @Operation(summary = "用户登录")
    public Result login(@Valid @RequestBody LoginFormDTO loginForm) {
        // 1.从redis中获取验证码并校验手机号和验证码
        String phone = loginForm.getPhone();
        /*String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        if (RegexUtils.isPhoneInvalid(phone)
                || cacheCode == null
                || !cacheCode.equals(loginForm.getCode())) {
            return Result.fail("手机号码或验证码有误，请重新输入！");
        }

        // 2.验证通过，删除redis中的验证码
        stringRedisTemplate.delete(LOGIN_CODE_KEY + phone);*/

        // 3.登录
        User user = userService.login(phone);

        // 4.保存用户的部分信息redis
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);

        // 4.1 生成token
        token = UUID.randomUUID(false).toString();

        tokenList.add(token);

        // 4.2 将userDTO转为Map对象存入redis
        Map<String, Object> map = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create().
                        setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));
        stringRedisTemplate.opsForHash().putAll(LOGIN_USER_KEY + token, map);

        // 4.3 设置token的有效期（30分钟）
        // stringRedisTemplate.expire(LOGIN_USER_KEY + token, LOGIN_USER_TTL, TimeUnit.SECONDS);

        // 5.返回token
        return Result.ok(token);
    }
    /*@PostMapping("/login")
    public Result login(@Valid @RequestBody LoginFormDTO loginForm, HttpSession session) {
        // 1.校验手机号
        // 校验格式
        // 校验与发送短信验证码的手机号码是否一致
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(loginForm.getPhone()) || !phone.equals(session.getAttribute(SystemConstants.PHONE))) {
            return Result.fail("手机号码有误，请重新输入！");
        }

        // 2.校验验证码
        String cacheCode = (String) session.getAttribute(SystemConstants.CODE);
        String code = loginForm.getCode();
        if (cacheCode == null || !cacheCode.equals(code)) {
            return Result.fail("验证码有误，请重新输入！");
        }

        // 3.登录
        User user = userService.login(phone);

        // 4.保存用户的部分信息到session
        session.setAttribute("user", BeanUtil.copyProperties(user, UserDTO.class));
        return Result.ok();
    }*/

    /**
     * 获取用户登录信息
     * @return 用户登录信息
     */
    @GetMapping("/me")
    @Operation(summary = "获取用户登录信息")
    public Result me() {
        return Result.ok(UserHolder.getUser());
    }

    /**
     * 用户退出登录
     */
    @PostMapping("/logout")
    @Operation(summary = "用户退出登录")
    public Result logout() {
        stringRedisTemplate.delete(LOGIN_USER_KEY + token);
        return Result.ok();
    }

    @GetMapping("/{id}")
    @Operation(summary = "根据id查询用户信息")
    public Result queryUserById(@PathVariable("id") Long id) {
        User user = userService.getById(id);
        if (user == null) {
            return Result.ok();
        }
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        return Result.ok(userDTO);
    }

    @GetMapping("/info/{id}")
    @Operation(summary = "根据id查询用户的详情信息")
    public Result queryUserInfoById(@PathVariable("id") Long id) {
        UserInfo userInfo = userInfoService.getById(id);
        if (userInfo == null) {
            return Result.ok();
        }
        return Result.ok(userInfo);
    }

    @PostMapping("/sign")
    @Operation(summary = "用户签到")
    public Result sign() {
        return userService.sign();
    }

    @GetMapping("/sign/count")
    @Operation(summary = "用户连续签到统计")
    public Result signCount() {
        return userService.signCount();
    }

    @GetMapping("/write")
    public Result write() throws IOException {
        BufferedWriter br = new BufferedWriter(new FileWriter("token.txt"));
        br.write("authorization");
        br.newLine();
        tokenList.forEach(token -> {
           try {
               br.write(token);
               br.newLine();
           } catch (IOException e) {
               log.error("IO异常！");
               e.printStackTrace();
           }
        });
       br.close();
       return Result.ok();
    }
}
