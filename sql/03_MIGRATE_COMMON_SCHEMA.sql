-- 이전 버전의 01_DB_SCRIPT.sql로 생성한 DB의 스키마를 갱신한다.
-- 기존 데이터를 유지하면서 수사 가설과 반환 신청에 필요한 컬럼을 추가한다.
-- 이미 컬럼이 추가된 DB에서는 재실행하지 않는다.
-- 최신 01_DB_SCRIPT.sql로 새로 만든 DB에는 실행하지 않는다.

USE polink;

ALTER TABLE tbl_hypothesis
    ADD COLUMN confidence INT NULL COMMENT 'AI 신뢰도',
    ADD COLUMN source VARCHAR(10) NOT NULL DEFAULT 'AI' COMMENT '가설 출처',
    ADD COLUMN basis_json TEXT NULL COMMENT '근거가 된 증거물·진술 목록',
    ADD COLUMN adopted_at DATETIME NULL COMMENT '채택 일시',
    ADD COLUMN adopted_by INT NULL COMMENT '채택한 경찰의 회원 번호';

ALTER TABLE tbl_hypothesis
    ADD CONSTRAINT fk_hypothesis_user
        FOREIGN KEY (adopted_by) REFERENCES tbl_user (user_id);

SELECT
    hypothesis_id,
    case_id,
    adopted_by,
    adopted_at
FROM tbl_hypothesis
WHERE adopted_by IS NULL
   OR adopted_at IS NULL
ORDER BY hypothesis_id;

SELECT COUNT(*) AS missing_adoption_data
FROM tbl_hypothesis
WHERE adopted_by IS NULL
   OR adopted_at IS NULL;

ALTER TABLE tbl_return_request
    ADD COLUMN completed_at DATETIME NULL COMMENT '실제 반환 완료 일시';

ALTER TABLE tbl_return_request
    ADD COLUMN completed_by INT NULL COMMENT '실제 반환 완료 처리 경찰 회원 번호',
    ADD CONSTRAINT fk_return_request_completed_by
        FOREIGN KEY (completed_by) REFERENCES tbl_user (user_id);