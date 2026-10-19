-- 1) root 계정으로 접속한 상태에서 실행
USE mysql;

-- 2) 프로젝트용 데이터베이스가 없을 때만 생성
CREATE DATABASE IF NOT EXISTS polink
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

-- 데이터베이스 목록 확인
SHOW DATABASES;

-- 3) 프로젝트용 MySQL 계정을 생성
-- 이미 계정이 있어도 CREATE USER IF NOT EXISTS는 오류 없이 넘어간다.
CREATE USER IF NOT EXISTS 'polink'@'localhost'
    IDENTIFIED BY 'polink';

-- 4) 데이터베이스에 새 DB 유저의 모든 권한을 부여
GRANT ALL PRIVILEGES ON polink.* TO 'polink'@'localhost';

-- 새 DB 유저에게 부여된 권한 확인
SHOW GRANTS FOR 'polink'@'localhost';

-- 5) 이후 작업은 새계정 계정 접속기에서 진행한다.
-- 01_DB_SCRIPT.sql을 열고 아래처럼 데이터베이스를 선택한 뒤 전체 실행한다.
USE polink;
