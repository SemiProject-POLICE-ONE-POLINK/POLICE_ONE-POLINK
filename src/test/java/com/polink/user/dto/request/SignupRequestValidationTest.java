package com.polink.user.dto.request;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SignupRequestValidationTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory =
                Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidatorFactory() {
        validatorFactory.close();
    }

    @Test
    void validPhonePassesValidation() {
        SignupRequest request =
                createRequest("010-1234-5678");

        boolean hasPhoneViolation = validator.validate(request)
                .stream()
                .anyMatch(violation ->
                        violation.getPropertyPath()
                                .toString()
                                .equals("phone")
                );

        assertFalse(hasPhoneViolation);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "01012345678",
            "010-123-4567",
            "010-12345-6789",
            "010-abcd-5678",
            "011-1234-5678"
    })
    void invalidPhoneFailsValidation(String phone) {
        SignupRequest request = createRequest(phone);

        boolean hasPhoneViolation = validator.validate(request)
                .stream()
                .anyMatch(violation ->
                        violation.getPropertyPath()
                                .toString()
                                .equals("phone")
                                && violation.getMessage().equals(
                                "연락처는 010-0000-0000 형식으로 입력해주세요."
                        )
                );

        assertTrue(hasPhoneViolation);
    }

    private SignupRequest createRequest(String phone) {
        SignupRequest request = new SignupRequest();

        ReflectionTestUtils.setField(
                request,
                "userName",
                "signup_test"
        );
        ReflectionTestUtils.setField(
                request,
                "password",
                "Test1234!"
        );
        ReflectionTestUtils.setField(
                request,
                "name",
                "가입테스트"
        );
        ReflectionTestUtils.setField(
                request,
                "phone",
                phone
        );

        return request;
    }
}