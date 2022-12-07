package org.comment.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class LoginFormDTO {

    @NotBlank(message = "手机号码不能为空")
    private String phone;
    private String code;
    private String password;
}
