package com.polink.item.controller;

// Mockito: Service의 가짜 객체를 만들고, 호출 여부를 확인할 때 사용한다.
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
// MockMvc: 실제 서버를 띄우지 않고 HTTP GET 요청을 흉내 낼 때 사용한다.
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.junit.jupiter.api.Assertions.assertEquals;

// HTTP 상태 코드와 JSON 응답값을 확인할 때 사용한다.
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static java.util.Objects.requireNonNull;

import com.polink.item.dto.ItemDetailResponse;
import com.polink.item.dto.ItemListResponse;
import com.polink.item.dto.ItemPageResponse;
import com.polink.item.dto.ItemUpdateRequest;
import com.polink.item.entity.ItemStatus;
import com.polink.item.entity.ItemType;
import com.polink.item.exception.ItemExceptionHandler;
import com.polink.item.exception.ItemNotFoundException;
import com.polink.item.service.ItemService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.polink.user.entity.UserRole;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;


// Mockito 기능을 JUnit 테스트에서 사용할 수 있게 연결한다.
@ExtendWith(MockitoExtension.class)
class ItemControllerTest {

    // 실제 DB를 조회하는 ItemService 대신 가짜 ItemService를 만든다.
    @Mock
    private ItemService itemService;

    // 실제 로그인 토큰 대신 sub와 role Claim 값을 정할 수 있는 가짜 JWT다.
    @Mock
    private Jwt jwt;

    // HTTP 요청을 가짜로 보내고 응답을 검사하는 테스트 도구다.
    private MockMvc mockMvc;

    // 각 테스트 메서드가 실행되기 전에 매번 한 번씩 실행된다.
    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(
                        new ItemController(itemService)
                )
                .setControllerAdvice(new ItemExceptionHandler())
                .build();
    }

    // GET /api/v1/items 요청이 정상적으로 200 OK를 반환하는지 검사한다.
    @Test
    void getItemsReturnsOk() throws Exception {
        // Service가 Controller에 돌려줄 가짜 목록 물품 데이터 한 건을 만든다.
        ItemListResponse item = new ItemListResponse(
                "L-0001",
                ItemType.LOST,
                "black wallet",
                "WALLET_CARD",
                "Seoul Station",
                LocalDate.of(2026, 10, 17),
                ItemStatus.LOST,
                null
        );

        // Controller가 조건 없이 목록을 요청하면,
        // 가짜 Service가 아래 페이지 응답을 반환하도록 미리 설정한다.
        given(itemService.getItems(null, null, null, null, 0, 10))
                .willReturn(new ItemPageResponse(
                        List.of(item),
                        0,
                        10,
                        1,
                        1,
                        true,
                        true
                ));

        // 실제 서버 대신 MockMvc로 GET /api/v1/items 요청을 보낸다.
        mockMvc.perform(get("/api/v1/items"))
                // HTTP 상태가 200 OK인지 확인한다.
                .andExpect(status().isOk())
                // JSON 응답의 첫 번째 물품 번호가 L-0001인지 확인한다.
                .andExpect(jsonPath("$.items[0].itemId").value("L-0001"))
                // JSON 응답의 물품 유형이 LOST인지 확인한다.
                .andExpect(jsonPath("$.items[0].itemType").value("LOST"))
                // 기본 페이지 번호가 0인지 확인한다.
                .andExpect(jsonPath("$.page").value(0))
                // 전체 물품 수가 1인지 확인한다.
                .andExpect(jsonPath("$.totalElements").value(1));

        // Controller가 Service에 기본 검색 조건과 page=0, size=10을 전달했는지 확인한다.
        verify(itemService).getItems(null, null, null, null, 0, 10);
    }

    // GET /api/v1/items/{itemId} 요청이 정상적으로 200 OK를 반환하는지 검사한다.
    @Test
    void getItemDetailReturnsOk() throws Exception {
        // Service가 반환할 가짜 상세 물품 데이터를 만든다.
        given(itemService.getItemDetail("L-0001"))
                .willReturn(new ItemDetailResponse(
                        "L-0001",
                        1,
                        ItemType.LOST,
                        "black wallet",
                        "WALLET_CARD",
                        "wallet containing cards",
                        "Seoul Station",
                        LocalDate.of(2026, 10, 17),
                        ItemStatus.LOST,
                        LocalDateTime.of(2026, 10, 18, 10, 0),
                        null,
                        null
                ));

        // 물품 번호 L-0001의 상세 조회 요청을 보낸다.
        mockMvc.perform(get("/api/v1/items/L-0001"))
                // HTTP 상태가 200 OK인지 확인한다.
                .andExpect(status().isOk())
                // 응답 JSON의 물품 번호를 확인한다.
                .andExpect(jsonPath("$.itemId").value("L-0001"))
                // 응답 JSON의 물품명을 확인한다.
                .andExpect(jsonPath("$.itemName").value("black wallet"))
                // 응답 JSON의 물품 유형을 확인한다.
                .andExpect(jsonPath("$.itemType").value("LOST"));

        // Controller가 정확한 물품 번호를 Service로 전달했는지 확인한다.
        verify(itemService).getItemDetail("L-0001");
    }

    // 존재하지 않는 물품 번호 요청 시 404 Not Found가 반환되는지 검사한다.
    @Test
    void getUnknownItemReturnsNotFound() throws Exception {
        // Service가 존재하지 않는 물품 예외를 발생시키도록 설정한다.
        given(itemService.getItemDetail("L-9999"))
                .willThrow(new ItemNotFoundException("L-9999"));

        // 존재하지 않는 번호로 상세 조회 요청을 보낸다.
        mockMvc.perform(get("/api/v1/items/L-9999"))
                // ItemExceptionHandler가 예외를 404로 변환하는지 확인한다.
                .andExpect(status().isNotFound())
                // 응답 JSON 내부 status 값도 404인지 확인한다.
                .andExpect(jsonPath("$.status").value(404));
    }

    // L-0001/F-0001 형식이 아닌 번호는 Service 호출 전 400 처리되는지 검사한다.
    @Test
    void getItemWithInvalidIdReturnsBadRequest() throws Exception {
        // 잘못된 형식 ABC로 상세 조회 요청을 보낸다.
        mockMvc.perform(get("/api/v1/items/ABC"))
                // Controller의 번호 형식 검증 결과가 400인지 확인한다.
                .andExpect(status().isBadRequest())
                // 오류 응답 JSON의 status 값이 400인지 확인한다.
                .andExpect(jsonPath("$.status").value(400));

        // 번호 형식이 틀리면 Service까지 요청이 전달되면 안 된다.
        verify(itemService, never()).getItemDetail("ABC");
    }

    // JWT role=CITIZEN인 사용자는 분실물 수정 Service를 호출하는지 검사한다.
    @Test
    void citizenUpdatesLostItem() {
        // JWT의 sub Claim은 로그인한 회원 번호를 문자열로 저장한다.
        given(jwt.getSubject()).willReturn("1");

        // JWT의 role Claim은 시민 계정을 뜻하는 CITIZEN으로 설정한다.
        given(jwt.getClaimAsString("role"))
                .willReturn(UserRole.CITIZEN.name());

        // 시민이 수정한 뒤 받게 될 가짜 상세 응답을 준비한다.
        ItemDetailResponse response = new ItemDetailResponse(
                "L-0001",
                1,
                ItemType.LOST,
                "수정된 카드지갑",
                "WALLET_CARD",
                "수정된 설명입니다.",
                "청솔역 2번 출구",
                LocalDate.of(2026, 10, 18),
                ItemStatus.LOST,
                LocalDateTime.of(2026, 10, 21, 10, 0),
                null,
                null
        );

        // Controller가 분실물 수정 Service를 호출하면 위 응답을 반환하도록 설정한다.
        given(itemService.updateLostItem(
                org.mockito.ArgumentMatchers.eq("L-0001"),
                org.mockito.ArgumentMatchers.eq(1),
                org.mockito.ArgumentMatchers.any(ItemUpdateRequest.class)
        )).willReturn(response);

        // 실제 HTTP 요청 대신 Controller 메서드에 JWT·물품 번호·수정 요청을 직접 전달한다.
        ResponseEntity<ItemDetailResponse> result = new ItemController(itemService)
                .updateItem(
                        jwt,
                        "L-0001",
                        new ItemUpdateRequest(
                                "수정된 카드지갑",
                                "WALLET_CARD",
                                "수정된 설명입니다.",
                                "청솔역 2번 출구",
                                LocalDate.of(2026, 10, 18)
                        )
                );

        // 시민의 분실물 수정 요청은 200 OK여야 한다.
        assertEquals(HttpStatus.OK, result.getStatusCode());

        // 응답 본문이 null이 아님을 확인하고, 수정 결과를 변수에 보관한다.
        ItemDetailResponse updatedItem = requireNonNull(result.getBody());
        assertEquals("수정된 카드지갑", updatedItem.itemName());

        // JWT sub의 사용자 ID=1이 분실물 수정 Service로 전달됐는지 확인한다.
        verify(itemService).updateLostItem(
                org.mockito.ArgumentMatchers.eq("L-0001"),
                org.mockito.ArgumentMatchers.eq(1),
                org.mockito.ArgumentMatchers.any(ItemUpdateRequest.class)
        );

        // 시민 요청은 습득물 수정 Service로 전달되면 안 된다.
        verify(itemService, never()).updateFoundItem(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.any(ItemUpdateRequest.class)
        );
    }

    // JWT role=POLICE인 사용자는 습득물 수정 Service를 호출하는지 검사한다.
    @Test
    void policeUpdatesFoundItem() {
        // 경찰 계정의 JWT subject를 회원 번호 6으로 설정한다.
        given(jwt.getSubject()).willReturn("6");

        // JWT role Claim을 경찰 계정 역할인 POLICE로 설정한다.
        given(jwt.getClaimAsString("role"))
                .willReturn(UserRole.POLICE.name());

        // 경찰이 수정한 뒤 받게 될 가짜 습득물 상세 응답을 준비한다.
        ItemDetailResponse response = new ItemDetailResponse(
                "F-0001",
                1,
                ItemType.FOUND,
                "수정된 파란 우산",
                "CLOTHING_GOODS",
                "안내데스크에 보관 중인 파란 우산입니다.",
                "청솔역 안내데스크",
                LocalDate.of(2026, 10, 18),
                ItemStatus.STORED,
                LocalDateTime.of(2026, 10, 21, 10, 0),
                null,
                null
        );

        // Controller가 습득물 수정 Service를 호출하면 위 응답을 반환하도록 설정한다.
        given(itemService.updateFoundItem(
                org.mockito.ArgumentMatchers.eq("F-0001"),
                org.mockito.ArgumentMatchers.eq(6),
                org.mockito.ArgumentMatchers.any(ItemUpdateRequest.class)
        )).willReturn(response);

        // 실제 HTTP 요청 대신 Controller 메서드에 경찰 JWT와 수정 요청을 직접 전달한다.
        ResponseEntity<ItemDetailResponse> result = new ItemController(itemService)
                .updateItem(
                        jwt,
                        "F-0001",
                        new ItemUpdateRequest(
                                "수정된 파란 우산",
                                "CLOTHING_GOODS",
                                "안내데스크에 보관 중인 파란 우산입니다.",
                                "청솔역 안내데스크",
                                LocalDate.of(2026, 10, 18)
                        )
                );

        // 경찰의 습득물 수정 요청은 200 OK여야 한다.
        assertEquals(HttpStatus.OK, result.getStatusCode());

// 응답 본문이 null이 아님을 확인하고, 수정 결과를 변수에 보관한다.
        ItemDetailResponse updatedFoundItem = requireNonNull(result.getBody());
        assertEquals("수정된 파란 우산", updatedFoundItem.itemName());

        // JWT sub의 사용자 ID=6이 습득물 수정 Service로 전달됐는지 확인한다.
        verify(itemService).updateFoundItem(
                org.mockito.ArgumentMatchers.eq("F-0001"),
                org.mockito.ArgumentMatchers.eq(6),
                org.mockito.ArgumentMatchers.any(ItemUpdateRequest.class)
        );

        // 경찰 요청은 분실물 수정 Service로 전달되면 안 된다.
        verify(itemService, never()).updateLostItem(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.any(ItemUpdateRequest.class)
        );
    }
}
