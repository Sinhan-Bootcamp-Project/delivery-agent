-- 가상 카드사 (inst_card). 카드번호 원문은 저장하지 않는다: 토큰 + 마스킹 번호만.
CREATE TABLE cards(token text PRIMARY KEY,masked_no text NOT NULL,owner_ref text NOT NULL,limit_krw bigint NOT NULL CHECK(limit_krw>=0),used_krw bigint NOT NULL DEFAULT 0 CHECK(used_krw>=0),status text NOT NULL CHECK(status IN ('ACTIVE','SUSPENDED')),CHECK(used_krw<=limit_krw));
CREATE TABLE authorizations(transaction_id text PRIMARY KEY,card_token text NOT NULL REFERENCES cards(token),amount_krw bigint NOT NULL CHECK(amount_krw>0),approval_no text NOT NULL,status text NOT NULL CHECK(status IN ('APPROVED','CANCELLED')),business_date date NOT NULL,cancel_business_date date,created_at timestamptz NOT NULL DEFAULT now());
CREATE INDEX authorizations_business_date ON authorizations(business_date);
CREATE TABLE payouts(business_date date PRIMARY KEY,amount_krw bigint NOT NULL,status text NOT NULL,created_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE settlement_legs(leg_id text PRIMARY KEY,amount_krw bigint NOT NULL,created_at timestamptz NOT NULL DEFAULT now());
-- 시연 카드: 사용자마다 CARD_A, CARD_B 각 1장, 한도 50만원
INSERT INTO cards VALUES
 ('tok-demo-card-a','9410-****-****-0001','00000000-0000-0000-0000-000000000001',500000,0,'ACTIVE'),
 ('tok-demo-card-b','5310-****-****-0001','00000000-0000-0000-0000-000000000001',500000,0,'ACTIVE'),
 ('tok-other-card-a','9410-****-****-0002','00000000-0000-0000-0000-000000000002',500000,0,'ACTIVE'),
 ('tok-other-card-b','5310-****-****-0002','00000000-0000-0000-0000-000000000002',500000,0,'ACTIVE');
