-- ------------------------------------------------------------------
-- Polink 팀 프로젝트용 데이터베이스 선택
-- ------------------------------------------------------------------
-- 프로젝트 전용 MySQL 계정과 데이터베이스를 모두 polink 이름으로 만든다.
-- 이 프로젝트는 polink 데이터베이스를 사용한다.
-- Polink 테이블(tbl_user, tbl_case 등)은 기존 레스토랑 테이블과 다른 이름으로 관리한다.

-- ==================================================================
-- 실행 순서 (IntelliJ Database 창 기준)
-- ==================================================================
-- 1. Database(원통 모양 아이콘) → + → Data Source → MySQL을 누른다.
-- 2. root 계정으로 연결한다.
-- 3. root 연결을 선택한 상태에서 이 파일 전체를 실행한다.
--      이 파일은 polink 데이터베이스와 polink 계정 권한을 준비한다.
-- 4. Database → + → Data Source → MySQL을 다시 눌러 프로젝트용 연결을 만든다.
-- 5. 프로젝트용(polink@polink) 연결에서 01_DB_SCRIPT.sql을 전체 실행한다.
--    더미 데이터가 필요하면 이어서 02_DUMMY_DATA.sql을 전체 실행한다.

-- 6. Database 창에서 polink를 우클릭 → Refresh 한다.
--      tbl_user, tbl_case, tbl_item 등이 보이면 성공이다.
-- ==================================================================

-- 1) root 계정으로 접속한 상태에서 실행한다.
USE mysql;

-- 2) 프로젝트용 데이터베이스가 없을 때만 생성
CREATE DATABASE IF NOT EXISTS project
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

-- 데이터베이스 목록을 확인해 polink가 존재하는지 확인
SHOW DATABASES;

-- 3) 프로젝트용 MySQL 계정을 생성한다.
-- 이미 계정이 있어도 CREATE USER IF NOT EXISTS는 오류 없이 넘어간다.
CREATE USER IF NOT EXISTS 'project'@'localhost'
    IDENTIFIED BY 'project';

-- 4) polink 데이터베이스에 polink 유저의 모든 권한을 부여
GRANT ALL PRIVILEGES ON project.* TO 'project'@'localhost';

-- polink 유저에게 부여된 권한 확인
SHOW GRANTS FOR 'project'@'localhost';

-- 5) 이후 작업은 project 계정 접속기에서 진행한다.
-- 01_DB_SCRIPT.sql을 열고 아래처럼 데이터베이스를 선택한 뒤 전체 실행한다.
USE polink;
