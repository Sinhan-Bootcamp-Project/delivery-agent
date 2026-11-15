-- 가상 한국은행 (inst_bok). 기관별 당좌계정과 영업일별 차액결제 배치.
CREATE TABLE current_accounts(institution text PRIMARY KEY,balance_krw bigint NOT NULL CHECK(balance_krw>=0));
CREATE TABLE batches(business_date date PRIMARY KEY,applied_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE batch_entries(business_date date NOT NULL REFERENCES batches(business_date),institution text NOT NULL REFERENCES current_accounts(institution),net_krw bigint NOT NULL,PRIMARY KEY(business_date,institution));
INSERT INTO current_accounts VALUES ('CRD',10000000000),('088',10000000000);
