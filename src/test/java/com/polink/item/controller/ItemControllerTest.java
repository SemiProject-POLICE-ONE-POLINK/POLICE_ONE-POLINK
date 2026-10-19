package com.polink.item.controller;

// Mockito: Service의 가짜 객체를 만들고, 호출 여부를 확인할 때 사용한다.
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

// MockMvc: 실제 서버를 띄우지 않고 HTTP GET 요청을 흉내 낼 때 사용한다.
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

// HTTP 상태 코드와 JSON 응답값을 확인할 때 사용한다.
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.polink.item.dto.ItemDetailResponse;
import com.polink.item.dto.ItemListResponse;
import com.polink.item.dto.ItemPageResponse;
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

// Mockito 기능을 JUnit 테스트에서 사용할 수 있게 연결한다.
@ExtendWith(MockitoExtension.class)
class ItemControllerTest {

    // 실제 DB를 조회하는 ItemService 대신 가짜 ItemService를 만든다.
    @Mock
    private ItemService itemService;

    // HTTP 요청을 가짜로 보내고 응답을 검사하는 테스트 도구다.
    private MockMvc mockMvc;

    // 각 테스트 메서드가 실행되기 전에 매번 한 번씩 실행된다.
    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ItemController(itemService))
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
}
