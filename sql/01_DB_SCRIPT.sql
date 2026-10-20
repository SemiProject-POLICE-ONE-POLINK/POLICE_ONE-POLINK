-- ==================================================================
-- POLINK 데이터베이스 테이블 생성 스크립트
-- ==================================================================
-- 이 파일은 최신 ERD의 테이블명, 컬럼명, 자료형을 기준으로 작성했다.
-- 사건번호(case_id), 증거물번호(evidence_id), 물품번호(item_id)는 VARCHAR(25)이다.
-- 따라서 Java 코드에서 UUID 또는 정해진 규칙의 문자열 번호를 만들어 저장해야 한다.
--
-- 실행 순서
--   1. 00_01_CREATE_DATABASE.sql을 root 계정으로 먼저 실행한다.
--   2. polink@polink 연결에서 이 파일 전체를 실행한다.
--   3. 더미 데이터가 필요하면 02_DUMMY_DATA.sql을 전체 실행한다.
--   4. Database 창을 Refresh하고 생성된 테이블을 확인한다.
-- ==================================================================

USE polink;

-- ------------------------------------------------------------------
-- [개발용 초기화]
-- 이미 이전 버전의 테이블을 만들었다면 아래 DROP 문의 주석을 제거한 뒤 실행한다.
-- 실제 데이터가 삭제되므로 팀원과 합의한 개발 환경에서만 사용한다.
-- 자식 테이블부터 역순으로 삭제해야 외래키 오류가 발생하지 않는다.
-- ------------------------------------------------------------------
-- DROP TABLE IF EXISTS tbl_return_request;
-- DROP TABLE IF EXISTS tbl_item;
-- DROP TABLE IF EXISTS tbl_investigation_log;
-- DROP TABLE IF EXISTS tbl_hypothesis;
-- DROP TABLE IF EXISTS tbl_statement;
-- DROP TABLE IF EXISTS tbl_witness;
-- DROP TABLE IF EXISTS tbl_evidence;
-- DROP TABLE IF EXISTS tbl_case_close;
-- DROP TABLE IF EXISTS tbl_case;
-- DROP TABLE IF EXISTS tbl_user;

-- ==================================================================
-- 1. 회원
-- ==================================================================
-- user_name은 로그인에 사용하는 아이디이며 중복될 수 없다.
CREATE TABLE IF NOT EXISTS tbl_user (
    user_id INT AUTO_INCREMENT NOT NULL COMMENT '회원 번호',
    user_name VARCHAR(25) NOT NULL COMMENT '아이디',
    password VARCHAR(255) NOT NULL COMMENT '비밀번호',
    name VARCHAR(25) NOT NULL COMMENT '이름',
    phone VARCHAR(25) NOT NULL COMMENT '연락처',
    -- 회원가입 INSERT에서 role을 생략하면 시민(CITIZEN)으로 자동 저장된다.
    -- 경찰관과 관리자는 일반 회원가입이 아닌 관리자 기능에서 별도로 부여한다.
    role VARCHAR(25) NOT NULL DEFAULT 'CITIZEN' COMMENT '계정 권한',

    CONSTRAINT pk_user PRIMARY KEY (user_id),
    CONSTRAINT uk_user_name UNIQUE (user_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='회원';

-- ==================================================================
-- 2. 사건 및 사건 종결
-- ==================================================================
-- case_id는 자동 증가 번호가 아니라 Java에서 생성해 넣는 VARCHAR(25) 사건번호다.
CREATE TABLE IF NOT EXISTS tbl_case (
    case_id VARCHAR(25) NOT NULL COMMENT '사건 번호',
    reporter_id INT NOT NULL COMMENT '사건 신고자의 회원 번호',
    assigned_officer_id INT NULL COMMENT '사건 담당 경찰의 회원 번호',
    title VARCHAR(100) NOT NULL COMMENT '사건명',
    case_type VARCHAR(30) NOT NULL COMMENT '사건 유형',
    description TEXT NOT NULL COMMENT '사건 설명',
    occurred_at DATETIME NOT NULL COMMENT '사건 발생 일시',
    location VARCHAR(255) NOT NULL COMMENT '사건 발생 장소',
    -- 사건 접수 시 상태를 생략하면 대기중(WAITING)으로 자동 저장된다.
    status VARCHAR(20) NOT NULL DEFAULT 'WAITING' COMMENT '사건 상태',
    cancel_reason VARCHAR(500) NOT NULL COMMENT '사건 취소 사유',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '사건 등록 일시',

    CONSTRAINT pk_case PRIMARY KEY (case_id),
    CONSTRAINT fk_case_reporter FOREIGN KEY (reporter_id) REFERENCES tbl_user (user_id),
    CONSTRAINT fk_case_assigned_officer FOREIGN KEY (assigned_officer_id) REFERENCES tbl_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='사건';

-- 사건 하나는 한 번만 종결한다.
-- case_id가 PK이면서 tbl_case를 참조하는 FK 역할도 한다.
CREATE TABLE IF NOT EXISTS tbl_case_close (
    case_id VARCHAR(25) NOT NULL COMMENT '사건 번호',
    closed_by INT NOT NULL COMMENT '사건 종결 처리 관리자의 회원 번호',
    close_reason TEXT NOT NULL COMMENT '사건 종결 사유',
    closed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '사건 종결 일시',

    CONSTRAINT pk_case_close PRIMARY KEY (case_id),
    CONSTRAINT fk_case_close_case FOREIGN KEY (case_id) REFERENCES tbl_case (case_id),
    CONSTRAINT fk_case_close_user FOREIGN KEY (closed_by) REFERENCES tbl_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='사건 종결';

-- ==================================================================
-- 3. 수사 및 AI 분석
-- ==================================================================
CREATE TABLE IF NOT EXISTS tbl_evidence (
    evidence_id VARCHAR(25) NOT NULL COMMENT '증거 번호',
    case_id VARCHAR(25) NOT NULL COMMENT '사건 번호',
    evidence_name VARCHAR(50) NOT NULL COMMENT '증거물명',
    evidence_type VARCHAR(30) NOT NULL COMMENT '증거물 유형',
    description TEXT NOT NULL COMMENT '증거물 설명',
    acquired_at DATETIME NOT NULL COMMENT '증거 확보 일시',
    acquired_location VARCHAR(255) NOT NULL COMMENT '증거 확보 장소',
    file_path VARCHAR(500) NULL COMMENT '첨부 파일 경로',

    CONSTRAINT pk_evidence PRIMARY KEY (evidence_id),
    CONSTRAINT fk_evidence_case FOREIGN KEY (case_id) REFERENCES tbl_case (case_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='증거물';

CREATE TABLE IF NOT EXISTS tbl_witness (
    witness_id VARCHAR(25) NOT NULL COMMENT '목격자 고유 번호',
    case_id VARCHAR(25) NOT NULL COMMENT '사건 번호',
    name VARCHAR(50) NOT NULL COMMENT '목격자 이름',
    phone VARCHAR(20) NOT NULL COMMENT '목격자 연락처',
    relationship VARCHAR(50) NOT NULL COMMENT '사건 관련 관계',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '등록 일시',

    CONSTRAINT pk_witness PRIMARY KEY (witness_id),
    CONSTRAINT fk_witness_case FOREIGN KEY (case_id) REFERENCES tbl_case (case_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='목격자';

-- 한 목격자는 최초 및 추가 진술을 여러 건 남길 수 있다.
CREATE TABLE IF NOT EXISTS tbl_statement (
    statement_id INT AUTO_INCREMENT NOT NULL COMMENT '진술 번호',
    witness_id VARCHAR(25) NOT NULL COMMENT '목격자 고유 번호',
    content TEXT NOT NULL COMMENT '진술 내용',
    statement_type VARCHAR(30) NOT NULL COMMENT '진술 유형',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '진술 등록 일시',

    CONSTRAINT pk_statement PRIMARY KEY (statement_id),
    CONSTRAINT fk_statement_witness FOREIGN KEY (witness_id) REFERENCES tbl_witness (witness_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='목격자 진술';

CREATE TABLE IF NOT EXISTS tbl_hypothesis (
    hypothesis_id INT AUTO_INCREMENT NOT NULL COMMENT '수사 가설 번호',
    case_id VARCHAR(25) NOT NULL COMMENT '사건 번호',
    adopted_by INT NOT NULL COMMENT '채택한 경찰의 회원 번호',
    content TEXT NOT NULL COMMENT '수사 가설 내용',
    reason TEXT NOT NULL COMMENT 'AI 추론 근거',
    additional_recommendation TEXT NULL COMMENT '추가 수사 권고 사항',
    confidence INT NULL COMMENT 'AI 신뢰도(0~100)',
    source VARCHAR(10) NOT NULL DEFAULT 'AI' COMMENT '가설 출처',
    basis_json TEXT NULL COMMENT '근거가 된 증거물·진술 목록',
    adopted_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '채택 일시',

    CONSTRAINT pk_hypothesis PRIMARY KEY (hypothesis_id),
    CONSTRAINT fk_hypothesis_case FOREIGN KEY (case_id) REFERENCES tbl_case (case_id),
    CONSTRAINT fk_hypothesis_user FOREIGN KEY (adopted_by) REFERENCES tbl_user (user_id),
    CONSTRAINT chk_hypothesis_confidence CHECK (confidence IS NULL OR confidence BETWEEN 0 AND 100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='수사 가설';

CREATE TABLE IF NOT EXISTS tbl_investigation_log (
    log_id INT AUTO_INCREMENT NOT NULL COMMENT '수사 기록 번호',
    case_id VARCHAR(25) NOT NULL COMMENT '사건 번호',
    officer_id INT NOT NULL COMMENT '담당 경찰 회원 번호',
    content TEXT NOT NULL COMMENT '수사 내용',
    investigated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '수사 일시',

    CONSTRAINT pk_investigation_log PRIMARY KEY (log_id),
    CONSTRAINT fk_investigation_log_case FOREIGN KEY (case_id) REFERENCES tbl_case (case_id),
    CONSTRAINT fk_investigation_log_officer FOREIGN KEY (officer_id) REFERENCES tbl_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='수사 기록';

-- ==================================================================
-- 4. 분실물 · 습득물 및 반환 요청
-- ==================================================================
-- item_id도 Java에서 직접 생성하는 VARCHAR(25) 물품번호다.
CREATE TABLE IF NOT EXISTS tbl_item (
    item_id VARCHAR(25) NOT NULL COMMENT '물품 번호',
    registrant_id INT NOT NULL COMMENT '등록자 회원 번호',
    item_type VARCHAR(25) NOT NULL COMMENT '물품 등록 유형',
    item_name VARCHAR(50) NOT NULL COMMENT '물품명',
    category VARCHAR(25) NOT NULL COMMENT '물품 카테고리',
    description TEXT NOT NULL COMMENT '물품 설명',
    location VARCHAR(255) NOT NULL COMMENT '발생 장소',
    occurred_at DATETIME NOT NULL COMMENT '발생 일시',
    status VARCHAR(25) NOT NULL COMMENT '물품 상태',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '물품 등록 일시',
    returned_at DATETIME NULL COMMENT '반환 완료 일시',
    image_path VARCHAR(500) NULL COMMENT '이미지 파일 경로',

    CONSTRAINT pk_item PRIMARY KEY (item_id),
    CONSTRAINT fk_item_registrant FOREIGN KEY (registrant_id) REFERENCES tbl_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='분실물 및 습득물';

-- lost_item_id와 found_item_id는 모두 tbl_item을 참조한다.
-- 반환 요청은 분실물과 대응되는 습득물 한 쌍을 대상으로 만든다.
CREATE TABLE IF NOT EXISTS tbl_return_request (
    request_id INT AUTO_INCREMENT NOT NULL COMMENT '반환 신청 번호',
    requester_id INT NOT NULL COMMENT '신청자 회원 번호',
    lost_item_id VARCHAR(25) NOT NULL COMMENT '분실물 번호',
    found_item_id VARCHAR(25) NOT NULL COMMENT '습득물 번호',
    processed_by INT NULL COMMENT '반환 처리 경찰 회원 번호',
    status VARCHAR(25) NOT NULL COMMENT '반환 신청 상태',
    request_reason TEXT NOT NULL COMMENT '반환 신청 사유',
    reject_reason VARCHAR(500) NOT NULL DEFAULT '' COMMENT '반환 반려 사유',
    processed_at DATETIME NULL COMMENT '반환 처리 일시',
    created_at DATETIME NOT NULL COMMENT '반환 신청 일시',

    CONSTRAINT pk_return_request PRIMARY KEY (request_id),
    CONSTRAINT fk_return_request_requester FOREIGN KEY (requester_id) REFERENCES tbl_user (user_id),
    CONSTRAINT fk_return_request_lost_item FOREIGN KEY (lost_item_id) REFERENCES tbl_item (item_id),
    CONSTRAINT fk_return_request_found_item FOREIGN KEY (found_item_id) REFERENCES tbl_item (item_id),
    CONSTRAINT fk_return_request_processed_by FOREIGN KEY (processed_by) REFERENCES tbl_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='반환 요청';

-- ==================================================================
-- 생성 확인: 아래 명령은 실행해도 데이터를 변경하지 않는다.
-- ==================================================================
SHOW TABLES;

DESC tbl_user;
DESC tbl_case;
DESC tbl_case_close;
DESC tbl_evidence;
DESC tbl_witness;
DESC tbl_statement;
DESC tbl_hypothesis;
DESC tbl_investigation_log;
DESC tbl_item;
DESC tbl_return_request;
