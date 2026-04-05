-- ============================================
-- Student Marks Management System
-- Database Setup Script
-- Run this in MySQL 8.0 first
-- ============================================

CREATE DATABASE IF NOT EXISTS marks_system;
USE marks_system;

-- -----------------------------------------------
-- Table 1: users (for login - admin & students)
-- PRN is exactly 12 digits for students
-- -----------------------------------------------
CREATE TABLE IF NOT EXISTS users (
    user_id     INT PRIMARY KEY AUTO_INCREMENT,
    prn         VARCHAR(20) UNIQUE NOT NULL,
    name        VARCHAR(100) NOT NULL,
    branch      VARCHAR(100),
    password    VARCHAR(100) NOT NULL,
    role        ENUM('admin', 'student') NOT NULL,

    -- Students must have exactly 12 numeric digits as PRN
    CONSTRAINT chk_prn_format CHECK (
        role = 'admin' OR (CHAR_LENGTH(prn) = 12 AND prn REGEXP '^[0-9]+$')
    )
);

-- -----------------------------------------------
-- Table 2: marks (stores CIA marks per course)
--
-- RULES:
--   CIA1, CIA3, CIA4  : max 10 marks each
--   CIA2              : max 50 marks
--   CIA2_CONVERTED    : AUTO = ROUND(cia2 * 20 / 50)  [out of 20]
--   TOTAL             : AUTO = cia1 + cia2_converted + cia3 + cia4
--
-- Generated columns (MySQL 8.0 feature) handle
-- CIA2_CONVERTED and TOTAL automatically.
-- Teacher only needs to enter cia1, cia2, cia3, cia4.
-- -----------------------------------------------
CREATE TABLE IF NOT EXISTS marks (
    id              INT PRIMARY KEY AUTO_INCREMENT,
    prn             VARCHAR(20) NOT NULL,
    course_code     VARCHAR(20) NOT NULL,
    course_name     VARCHAR(100) NOT NULL,

    cia1            INT NOT NULL DEFAULT 0,
    cia2            INT NOT NULL DEFAULT 0,
    cia3            INT NOT NULL DEFAULT 0,
    cia4            INT NOT NULL DEFAULT 0,

    -- GENERATED COLUMNS: calculated automatically by MySQL, never entered manually
    cia2_converted  INT GENERATED ALWAYS AS (ROUND(cia2 * 20 / 50)) STORED,
    total           INT GENERATED ALWAYS AS (cia1 + ROUND(cia2 * 20 / 50) + cia3 + cia4) STORED,

    -- CHECK CONSTRAINTS: enforce mark caps at database level
    CONSTRAINT chk_cia1 CHECK (cia1 >= 0 AND cia1 <= 10),
    CONSTRAINT chk_cia2 CHECK (cia2 >= 0 AND cia2 <= 50),
    CONSTRAINT chk_cia3 CHECK (cia3 >= 0 AND cia3 <= 10),
    CONSTRAINT chk_cia4 CHECK (cia4 >= 0 AND cia4 <= 10),

    -- A student cannot have duplicate entry for same course
    UNIQUE KEY unique_prn_course (prn, course_code),

    FOREIGN KEY (prn) REFERENCES users(prn) ON DELETE CASCADE
);

-- -----------------------------------------------
-- Admin account
-- -----------------------------------------------
INSERT INTO users (prn, name, branch, password, role)
VALUES ('ADMIN001', 'Prof. Sharma', 'Administration', 'admin123', 'admin');

-- -----------------------------------------------
-- Sample Student accounts (PRN = exactly 12 digits)
-- -----------------------------------------------
INSERT INTO users (prn, name, branch, password, role) VALUES
('240105121011', 'Dhanraj Gupta',  'B.Tech CSE specialization in CTIS', 'pass1011', 'student'),
('240105121012', 'Priya Mehta',    'B.Tech CSE specialization in CTIS', 'pass1012', 'student'),
('240105121013', 'Rahul Verma',    'B.Tech CSE specialization in CTIS', 'pass1013', 'student');

-- -----------------------------------------------
-- Sample Marks: Dhanraj Gupta (240105121011)
-- Only cia1, cia2, cia3, cia4 are entered.
-- cia2_converted and total are AUTO-CALCULATED by MySQL.
-- -----------------------------------------------
INSERT INTO marks (prn, course_code, course_name, cia1, cia2, cia3, cia4) VALUES
('240105121011', 'NYCT401', 'Java Programming',                                   9, 44, 8, 8),
('240105121011', 'NYCT402', 'Database Management System',                         8, 47, 7, 8),
('240105121011', 'NYCT403', 'Computer Organization and Architecture',             9, 43, 8, 9),
('240105121011', 'NYCT411', 'Database Management System Laboratory',              7, 42, 9, 7),
('240105121011', 'MDM',     'Microcontrollers & Interfacing',                     9, 41, 9, 8),
('240105121011', 'NCAO10',  'Open Elective II - Digital Electronics',             9, 45, 8, 9),
('240105121011', 'NYCT412', 'Java Programming Laboratory',                        7, 46, 9, 7),
('240105121011', 'NHSA12',  'Strategic Communication for Professionals (AEC-IV)',8, 47, 9, 8);

-- Sample Marks: Priya Mehta (240105121012)
INSERT INTO marks (prn, course_code, course_name, cia1, cia2, cia3, cia4) VALUES
('240105121012', 'NYCT401', 'Java Programming',                                   8, 46, 9, 9),
('240105121012', 'NYCT402', 'Database Management System',                         9, 48, 8, 9),
('240105121012', 'NYCT403', 'Computer Organization and Architecture',             8, 44, 9, 8),
('240105121012', 'NYCT411', 'Database Management System Laboratory',              9, 43, 8, 9),
('240105121012', 'MDM',     'Microcontrollers & Interfacing',                     8, 42, 9, 8),
('240105121012', 'NCAO10',  'Open Elective II - Digital Electronics',             9, 47, 9, 9),
('240105121012', 'NYCT412', 'Java Programming Laboratory',                        8, 45, 8, 8),
('240105121012', 'NHSA12',  'Strategic Communication for Professionals (AEC-IV)',9, 48, 8, 9);

-- -----------------------------------------------
-- Verify setup + preview auto-calculated values
-- -----------------------------------------------
SELECT 'Database setup complete!' AS Status;
SELECT CONCAT('Users created: ', COUNT(*)) AS Info FROM users;
SELECT CONCAT('Marks entries: ', COUNT(*)) AS Info FROM marks;

SELECT
    course_code,
    course_name,
    cia1,
    cia2,
    cia2_converted,
    cia3,
    cia4,
    total
FROM marks
WHERE prn = '240105121011'
ORDER BY course_code;
