package com.polink.user.repository;


import com.polink.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Integer> {

    boolean existsByUserName(String userName);

    boolean existsByPhone(String phone);
}