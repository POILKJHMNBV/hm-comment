package org.comment.utils;

import org.comment.dto.UserDTO;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 登录拦截器
 */
public class LoginInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {

        /*// 1.从session中获取用户信息
        UserDTO userDTO = (UserDTO) request.getSession().getAttribute("user");
        if (userDTO == null) {
            // 用户不存在
            response.setStatus(401);
            return false;
        }
        // 2.保存用户信息到ThreadLocal
        UserHolder.saveUser(userDTO);
        return true;*/

        // 获取用户信息
        UserDTO userDTO = UserHolder.getUser();
        if (userDTO == null) {
            // 用户不存在
            response.setStatus(401);
            return false;
        }
        return true;
    }

}
