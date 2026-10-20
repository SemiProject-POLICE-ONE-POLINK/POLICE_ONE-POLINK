package com.polink.item.dev;
import com.polink.user.entity.User;
import com.polink.user.entity.UserRole;
import com.polink.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * 실제 로그인 기능이 연결되기 전,
 * application.yaml의 더미 사용자 ID를 현재 사용자로 사용하는 로컬 전용 클래스다.
 */
// 반환 신청 모듈의 동명 Bean과 충돌하지 않도록 물품 모듈 전용 이름을 지정한다.
@Component("itemLocalCurrentUserProvider")
@Profile("local")
@RequiredArgsConstructor
public class LocalCurrentUserProvider {
    private final UserRepository userRepository;

    @Value("${app.dev-user-id}")
    private Integer devUserId;

    public User getCurrentUser() {
        return userRepository.findById(devUserId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "로컬 테스트 사용자를 찾을 수 없습니다."
                ));
    }


    public Integer getCurrentUserId() {
        return getCurrentUser().getUserId();
    }

    public void requireCitizen() {
        if (getCurrentUserRole() != UserRole.CITIZEN) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "시민 계정만 사용할 수 있는 기능입니다."
            );
        }
    }

    /**
     * 현재 로컬 테스트 사용자의 역할을 반환한다.
     * 이후 시민·경찰별 기능 권한을 구분할 때 사용한다.
     */
    public UserRole getCurrentUserRole() {
        return getCurrentUser().getRole();
    }

    /**
     * 현재 사용자가 경찰 계정인지 확인한다.
     * 경찰이 아닌 계정이면 403 Forbidden을 반환한다.
     */
    public void requirePolice() {
        if (getCurrentUserRole() != UserRole.POLICE) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "경찰 계정만 사용할 수 있는 기능입니다."
            );
        }
    }
}
