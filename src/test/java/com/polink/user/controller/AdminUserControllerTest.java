package com.polink.user.controller;

import com.polink.user.dto.response.AdminUserListResponse;
import com.polink.user.dto.response.AdminUserResponse;
import com.polink.user.dto.response.AssignablePoliceListResponse;
import com.polink.user.dto.response.AssignablePoliceResponse;
import com.polink.user.entity.UserRole;
import com.polink.user.exception.UserExceptionHandler;
import com.polink.user.service.AdminUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminUserControllerTest {

    @Mock
    private AdminUserService adminUserService;

    private MockMvc mockMvc;
    private Jwt requestJwt;

    @Test
    void getAssignablePolice_withoutLogin_returnsUnauthorized()
            throws Exception {

        requestJwt = null;

        when(adminUserService.getAssignablePolice(null))
                .thenThrow(new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "로그인 사용자 정보가 필요합니다."
                ));

        mockMvc.perform(
                        get("/api/v1/users/police-officers")
                )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status")
                        .value(401))
                .andExpect(jsonPath("$.message")
                        .value("로그인 사용자 정보가 필요합니다."));

        verify(adminUserService).getAssignablePolice(null);
    }

    @Test
    void getAllUsers_success_returnsOk() throws Exception {
        AdminUserListResponse response =
                new AdminUserListResponse(
                        List.of(
                                new AdminUserResponse(
                                        1,
                                        "citizen01",
                                        "테스트시민",
                                        "010-1111-1111",
                                        UserRole.CITIZEN
                                ),
                                new AdminUserResponse(
                                        7,
                                        "police01",
                                        "테스트경찰",
                                        "010-2222-2222",
                                        UserRole.POLICE
                                ),
                                new AdminUserResponse(
                                        11,
                                        "admin01",
                                        "테스트관리자",
                                        "010-3333-3333",
                                        UserRole.ADMIN
                                )
                        )
                );

        when(adminUserService.getAllUsers(11))
                .thenReturn(response);

        mockMvc.perform(
                        get("/api/v1/users")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users.length()")
                        .value(3))
                .andExpect(jsonPath("$.users[0].userId")
                        .value(1))
                .andExpect(jsonPath("$.users[0].userName")
                        .value("citizen01"))
                .andExpect(jsonPath("$.users[0].name")
                        .value("테스트시민"))
                .andExpect(jsonPath("$.users[0].phone")
                        .value("010-1111-1111"))
                .andExpect(jsonPath("$.users[0].role")
                        .value("CITIZEN"))
                .andExpect(jsonPath("$.users[1].role")
                        .value("POLICE"))
                .andExpect(jsonPath("$.users[2].role")
                        .value("ADMIN"));

        verify(adminUserService).getAllUsers(11);
    }

    @Test
    void getAllUsers_withoutLogin_returnsUnauthorized()
            throws Exception {

        requestJwt = null;

        when(adminUserService.getAllUsers(null))
                .thenThrow(new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "로그인 사용자 정보가 필요합니다."
                ));

        mockMvc.perform(
                        get("/api/v1/users")
                )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status")
                        .value(401))
                .andExpect(jsonPath("$.message")
                        .value("로그인 사용자 정보가 필요합니다."));

        verify(adminUserService).getAllUsers(null);
    }

    @BeforeEach
    void setUp() {
        requestJwt = createJwt("11", "ADMIN");

        HandlerMethodArgumentResolver jwtResolver =
                new HandlerMethodArgumentResolver() {
                    @Override
                    public boolean supportsParameter(
                            MethodParameter parameter
                    ) {
                        return parameter.hasParameterAnnotation(
                                AuthenticationPrincipal.class
                        ) && parameter.getParameterType().equals(Jwt.class);
                    }

                    @Override
                    public Object resolveArgument(
                            MethodParameter parameter,
                            ModelAndViewContainer mavContainer,
                            NativeWebRequest webRequest,
                            WebDataBinderFactory binderFactory
                    ) {
                        return requestJwt;
                    }
                };

        mockMvc = MockMvcBuilders
                .standaloneSetup(
                        new AdminUserController(adminUserService)
                )
                .setControllerAdvice(new UserExceptionHandler())
                .setCustomArgumentResolvers(jwtResolver)
                .build();
    }

    @Test
    void getAssignablePolice_success_returnsOk() throws Exception {
        AssignablePoliceListResponse response =
                new AssignablePoliceListResponse(
                        List.of(
                                new AssignablePoliceResponse(
                                        6,
                                        "골롬보",
                                        2L
                                ),
                                new AssignablePoliceResponse(
                                        7,
                                        "신형선",
                                        0L
                                )
                        )
                );

        when(adminUserService.getAssignablePolice(11))
                .thenReturn(response);

        mockMvc.perform(
                        get("/api/v1/users/police-officers")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.officers.length()")
                        .value(2))
                .andExpect(jsonPath("$.officers[0].userId")
                        .value(6))
                .andExpect(jsonPath("$.officers[0].name")
                        .value("골롬보"))
                .andExpect(jsonPath(
                        "$.officers[0].investigatingCaseCount"
                ).value(2))
                .andExpect(jsonPath("$.officers[1].userId")
                        .value(7))
                .andExpect(jsonPath("$.officers[1].name")
                        .value("신형선"))
                .andExpect(jsonPath(
                        "$.officers[1].investigatingCaseCount"
                ).value(0));

        verify(adminUserService).getAssignablePolice(11);
    }

    private Jwt createJwt(String subject, String role) {
        return Jwt.withTokenValue("test-access-token")
                .header("alg", "none")
                .subject(subject)
                .claim("role", role)
                .build();
    }
}