-- 가상 금융결제원 (inst_kftc). 구간별 상태를 컬럼으로 남겨 어디서 멈췄는지 보이게 한다.
CREATE TABLE transfers(transaction_id text PRIMARY KEY,from_institution text NOT NULL,from_account text NOT NULL,to_institution text NOT NULL,to_account text NOT NULL,amount_krw bigint NOT NULL CHECK(amount_krw>0),purpose text NOT NULL,
 status text NOT NULL CHECK(status IN ('REQUESTED','DEBITED','COMPLETED','FAILED','COMPENSATED')),
 debit_status text,credit_status text,compensation_status text,failure_code text,business_date date NOT NULL,
 created_at timestamptz NOT NULL DEFAULT now(),updated_at timestamptz NOT NULL DEFAULT now());
CREATE INDEX transfers_business_date ON transfers(business_date);
CREATE TABLE closings(business_date date PRIMARY KEY,status text NOT NULL CHECK(status IN ('NET_CALCULATED','SETTLED')),created_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE net_entries(business_date date NOT NULL REFERENCES closings(business_date),institution text NOT NULL,net_krw bigint NOT NULL,PRIMARY KEY(business_date,institution));
