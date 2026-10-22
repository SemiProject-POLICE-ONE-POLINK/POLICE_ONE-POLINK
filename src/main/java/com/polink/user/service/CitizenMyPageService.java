package com.polink.user.service;

import com.polink.casefile.entity.CaseClose;
import com.polink.casefile.entity.CaseFile;
import com.polink.casefile.entity.CaseStatus;
import com.polink.casefile.repository.CaseCloseRepository;
import com.polink.casefile.repository.CaseRepository;
import com.polink.item.entity.Item;
import com.polink.item.entity.ItemType;
import com.polink.item.repository.ItemRepository;
import com.polink.item.returnrequest.entity.ReturnRequest;
import com.polink.item.returnrequest.repository.ReturnRequestRepository;
import com.polink.user.dto.response.CitizenMyPageResponse;
import com.polink.user.dto.response.MyCaseSummaryResponse;
import com.polink.user.dto.response.MyItemSummaryResponse;
import com.polink.user.entity.User;
import com.polink.user.entity.UserRole;
import com.polink.user.repository.UserRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class CitizenMyPageService {

    private final UserRepository userRepository;
    private final CaseRepository caseRepository;
    private final CaseCloseRepository caseCloseRepository;
    private final ItemRepository itemRepository;
    private final ReturnRequestRepository returnRequestRepository;

    public CitizenMyPageService(UserRepository userRepository, CaseRepository caseRepository, CaseCloseRepository caseCloseRepository, ItemRepository itemRepository, ReturnRequestRepository returnRequestRepository) {
        this.userRepository = userRepository;
        this.caseRepository = caseRepository;
        this.caseCloseRepository = caseCloseRepository;
        this.itemRepository = itemRepository;
        this.returnRequestRepository = returnRequestRepository;
    }

    @Transactional(readOnly = true)
    public CitizenMyPageResponse getCitizenMyPage(Integer loggedInUserId) {

        // 회원 번호가 없을 때(액세스 토큰이 없음)
        if (loggedInUserId == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."
            );
        }

        // 회원 번호는 있으나 DB에 일치하는 회원이 없음
        User user = userRepository.findById(loggedInUserId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "로그인 사용자 정보를 확인할 수 없습니다."
                ));

        // 시민인지 아닌지 검증
        if (user.getRole() != UserRole.CITIZEN) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "시민 전용 마이페이지에는 접근할 수 없습니다."
            );
        }

        // 내가 접수한 사건 전체를 최신 등록순으로 조회
        List<CaseFile> caseFiles =
                caseRepository.findMyCasesForMyPage(loggedInUserId);

        // 종결된 사건들의 종결일을 한 번에 조회해 사건번호별로 정리(타임라인 용)
        List<String> closedCaseIds = caseFiles.stream()
                .filter(caseFile -> caseFile.getStatus() == CaseStatus.CLOSED)
                .map(CaseFile::getCaseId)
                .toList();

        Map<String, LocalDateTime> closedAtByCaseId = new HashMap<>();
        for (CaseClose caseClose
                : caseCloseRepository.findAllById(closedCaseIds)) {
            closedAtByCaseId.put(
                    caseClose.getCaseId(),
                    caseClose.getClosedAt()
            );
        }

        List<MyCaseSummaryResponse> cases = new ArrayList<>();
        for (CaseFile caseFile : caseFiles) {
            cases.add(new MyCaseSummaryResponse(
                    caseFile,
                    closedAtByCaseId.get(caseFile.getCaseId())
            ));
        }

        // 내가 등록한 분실물·습득물 전체를 최신 등록순으로 조회
        List<Item> myItems = itemRepository
                .findByRegistrantIdOrderByCreatedAtDescItemIdDesc(
                        loggedInUserId,
                        Pageable.unpaged()
                )
                .getContent();

        // 내가 신청한 반환 내역을 최신순으로 조회
        List<ReturnRequest> returnRequests = returnRequestRepository
                .findByRequesterIdOrderByCreatedAtDesc(
                        loggedInUserId,
                        Pageable.unpaged()
                )
                .getContent();

        // 같은 분실물에 신청이 여러 건이면 가장 최근 신청을 사용
        Map<String, ReturnRequest> latestRequestByLostItemId =
                new HashMap<>();
        for (ReturnRequest request : returnRequests) {
            latestRequestByLostItemId.putIfAbsent(
                    request.getLostItemId(),
                    request
            );
        }

        List<MyItemSummaryResponse> items = new ArrayList<>();
        for (Item item : myItems) {
            // 반환 신청 정보는 내가 등록한 분실물 카드에만 연결
            ReturnRequest returnRequest =
                    item.getItemType() == ItemType.LOST
                            ? latestRequestByLostItemId.get(item.getItemId())
                            : null;

            items.add(new MyItemSummaryResponse(item, returnRequest));
        }

        return new CitizenMyPageResponse(
                cases.size(),
                cases,
                items.size(),
                items
        );
    }
}
