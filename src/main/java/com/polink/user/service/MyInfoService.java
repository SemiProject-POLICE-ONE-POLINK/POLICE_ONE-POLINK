package com.polink.user.service;

import com.polink.user.dto.response.MyInfoResponse;
import com.polink.user.entity.User;
import com.polink.user.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MyInfoService {

    private final UserRepository userRepository;

    public MyInfoService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    // 로그인한 회원 본인의 정보 조회
    @Transactional(readOnly = true)
    public MyInfoResponse findMyInfo(Integer loggedInUserId) {

        // 1. 로그인 정보가 없으면 조회 거부
        if (loggedInUserId == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "로그인 후 이용해주세요."
            );
        }

        // 2. JWT에서 전달받은 회원 번호로 DB 조회
        User user = userRepository.findById(loggedInUserId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "회원 정보를 확인할 수 없습니다. 다시 로그인해주세요."
                ));

        // 3. 비밀번호를 제외한 회원 본인의 정보를 응답 DTO로 변환
        return MyInfoResponse.from(user);
    }
}