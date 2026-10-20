-- 기존 01_DB_SCRIPT.sql로 테이블을 생성한 경우 해당 sql을 실행한다.
-- 기존 tbl_hypothesis의 데이터를 유지하며 새 컬럼을 추가한다.

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
