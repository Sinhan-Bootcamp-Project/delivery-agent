-- 가상 은행 (inst_bank). 잔액은 postings 합과 같아야 한다(CHECK 대신 대사에서 검사).
CREATE TABLE accounts(account_no text PRIMARY KEY,owner_ref text NOT NULL,kind text NOT NULL CHECK(kind IN ('CUSTOMER','TRUST','SETTLEMENT','CLEARING')),balance_krw bigint NOT NULL CHECK(balance_krw>=0));
CREATE TABLE postings(id bigserial PRIMARY KEY,leg_id text NOT NULL UNIQUE,account_no text NOT NULL REFERENCES accounts(account_no),delta_krw bigint NOT NULL CHECK(delta_krw<>0),memo text,business_date date NOT NULL,created_at timestamptz NOT NULL DEFAULT now());
CREATE INDEX postings_business_date ON postings(business_date);
-- 시연 계좌. TRUST(충전금 별도관리) 초기잔액 = 한끼 원장의 초기 페이머니 합계(각 사용자 50만원).
INSERT INTO accounts VALUES
 ('088-0001-000001','00000000-0000-0000-0000-000000000001','CUSTOMER',1000000),
 ('088-0001-000002','00000000-0000-0000-0000-000000000002','CUSTOMER',1000000),
 ('088-9000-000001','HANKI','TRUST',1000000),
 ('088-9000-000002','HANKI','SETTLEMENT',0);
INSERT INTO postings(leg_id,account_no,delta_krw,memo,business_date) VALUES
 ('OPENING-1','088-0001-000001',1000000,'opening',DATE '2026-11-01'),
 ('OPENING-2','088-0001-000002',1000000,'opening',DATE '2026-11-01'),
 ('OPENING-3','088-9000-000001',1000000,'opening',DATE '2026-11-01');
