package com.polink.user.repository;

import com.polink.user.entity.User;
import com.polink.user.entity.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Integer> {

    // 회원가입 시 로그인 아이디 중복 여부 확인
    boolean existsByUserName(String userName);

    // 회원가입 시 연락처 중복 여부 확인
    boolean existsByPhone(String phone);

    // 로그인 아이디로 회원 조회
    Optional<User> findByUserName(String userName);

    // 담당 경찰 배정 목록용 - 경찰 역할 회원을 이름순으로 조회
    List<User> findByRoleOrderByNameAsc(UserRole role);
}