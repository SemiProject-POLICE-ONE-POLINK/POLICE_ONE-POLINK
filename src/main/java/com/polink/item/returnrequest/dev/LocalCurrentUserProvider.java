package com.polink.item.returnrequest.dev;
import com.polink.user.entity.User;
import com.polink.user.entity.UserRole;
import com.polink.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
@Profile("local")
@RequiredArgsConstructor
public class LocalCurrentUserProvider {

    private final UserRepository userRepository;

    @Value("${app.dev-user-id}")
    private Integer devUserId;

    // application.yaml의 dev-user-id에 해당하는 더미 사용자를 조회한다.
    public User getCurrentUser() {
        return userRepository.findById(devUserId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "로컬 테스트 사용자를 찾을 수 없습니다."
                ));
    }

    // 현재 로컬 테스트 사용자의 ID를 반환한다.
    public Integer getCurrentUserId() {
        return getCurrentUser().getUserId();
    }

    // 시민만 사용할 수 있는 기능인지 확인한다.
    public void requireCitizen() {
        if (getCurrentUser().getRole() != UserRole.CITIZEN) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "시민 계정만 사용할 수 있는 기능입니다."
            );
        }
    }

    // 경찰관만 사용할 수 있는 기능인지 확인한다.
    public void requirePolice() {
        if (getCurrentUser().getRole() != UserRole.POLICE) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "경찰관 계정만 사용할 수 있는 기능입니다."
            );
        }
    }
}
