package com.polink.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SignupRequest {

    @NotBlank(message = "아이디는 필수입니다.")
    @Size(max = 25, message = "아이디는 25자 이하여야 합니다.")
    private String userName;

    @NotBlank(message = "비밀번호는 필수입니다.")
    private String password;

    @NotBlank(message = "이름은 필수입니다.")
    @Size(max = 25, message = "이름은 25자 이하여야 합니다.")
    private String name;

    @NotBlank(message = "연락처는 필수입니다.")
    @Size(max = 25, message = "연락처는 25자 이하여야 합니다.")
    private String phone;
}
