-- 단식 원장(wallets/transactions/ledger)을 복식부기 원장으로 교체한다. 기존 데이터는 시연용이라 이관하지 않는다.
DROP TABLE IF EXISTS ledger;
DROP TABLE IF EXISTS transactions;
DROP TABLE IF EXISTS wallets;

-- payment 컨텍스트
CREATE TABLE payments(
  execution_id text PRIMARY KEY,
  user_id text NOT NULL,
  requested_method text NOT NULL,
  method text NOT NULL CHECK(method IN ('CARD_A','CARD_B','PAYMONEY')),
  amount_krw bigint NOT NULL CHECK(amount_krw>0),
  discount_krw bigint NOT NULL DEFAULT 0 CHECK(discount_krw>=0),
  digest text NOT NULL,
  status text NOT NULL CHECK(status IN ('PENDING','APPROVED','FAILED','UNKNOWN','CANCEL_PENDING','CANCELLED')),
  approval_no text,
  failure_code text,
  business_date date NOT NULL,
  cancel_business_date date,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now());
CREATE INDEX payments_unresolved ON payments(status,updated_at) WHERE status IN ('PENDING','UNKNOWN','CANCEL_PENDING');
CREATE INDEX payments_business_date ON payments(business_date);
-- 구간별 상태 기록(감사·장애 분석)
CREATE TABLE payment_history(id bigserial PRIMARY KEY,execution_id text NOT NULL REFERENCES payments(execution_id),from_status text,to_status text NOT NULL,created_at timestamptz NOT NULL DEFAULT now());
-- 사용자 카드 토큰(카드번호 원문 없음)
CREATE TABLE card_links(user_id text NOT NULL,method text NOT NULL,card_token text NOT NULL,PRIMARY KEY(user_id,method));
INSERT INTO card_links VALUES
 ('00000000-0000-0000-0000-000000000001','CARD_A','tok-demo-card-a'),
 ('00000000-0000-0000-0000-000000000001','CARD_B','tok-demo-card-b'),
 ('00000000-0000-0000-0000-000000000002','CARD_A','tok-other-card-a'),
 ('00000000-0000-0000-0000-000000000002','CARD_B','tok-other-card-b');

-- paymoney 컨텍스트
CREATE TABLE paymoney_links(user_id text PRIMARY KEY,bank_account_no text NOT NULL);
INSERT INTO paymoney_links VALUES
 ('00000000-0000-0000-0000-000000000001','088-0001-000001'),
 ('00000000-0000-0000-0000-000000000002','088-0001-000002');
CREATE TABLE topups(
  id text PRIMARY KEY,user_id text NOT NULL,bank_account_no text NOT NULL,
  amount_krw bigint NOT NULL CHECK(amount_krw>0),
  status text NOT NULL CHECK(status IN ('REQUESTED','COMPLETED','FAILED','UNKNOWN')),
  failure_code text,business_date date NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),updated_at timestamptz NOT NULL DEFAULT now());
CREATE INDEX topups_business_date ON topups(business_date);

-- ledger 컨텍스트: 복식부기. balance_krw는 정상 잔액 방향의 캐시(LED-04 검사 대상).
CREATE TABLE accounts(
  code text PRIMARY KEY,
  type text NOT NULL CHECK(type IN ('ASSET','EXPENSE','LIABILITY','EQUITY')),
  allow_negative boolean NOT NULL,
  balance_krw bigint NOT NULL DEFAULT 0,
  CHECK(allow_negative OR balance_krw>=0));
CREATE TABLE journal_entries(
  id bigserial PRIMARY KEY,reference_id text NOT NULL,
  kind text NOT NULL CHECK(kind IN ('OPENING','PAYMENT','CANCEL','TOPUP','CARD_PAYOUT')),
  amount_krw bigint NOT NULL,created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE(reference_id,kind));
CREATE TABLE journal_lines(
  id bigserial PRIMARY KEY,entry_id bigint NOT NULL REFERENCES journal_entries(id),
  account_code text NOT NULL REFERENCES accounts(code),
  debit_krw bigint NOT NULL CHECK(debit_krw>=0),credit_krw bigint NOT NULL CHECK(credit_krw>=0),
  CHECK((debit_krw=0) <> (credit_krw=0)));
CREATE INDEX journal_lines_account ON journal_lines(account_code);
-- 원장 불변(LED-02): 수정·삭제 금지. 정정은 역분개로만.
CREATE FUNCTION forbid_ledger_change() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'ledger rows are immutable'; END $$;
CREATE TRIGGER journal_entries_immutable BEFORE UPDATE OR DELETE ON journal_entries FOR EACH ROW EXECUTE FUNCTION forbid_ledger_change();
CREATE TRIGGER journal_lines_immutable BEFORE UPDATE OR DELETE ON journal_lines FOR EACH ROW EXECUTE FUNCTION forbid_ledger_change();
-- 분개 균형(LED-01): 커밋 시점에 분개별 차변 합 = 대변 합 검사
CREATE FUNCTION check_entry_balanced() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF (SELECT coalesce(sum(debit_krw-credit_krw),0) FROM journal_lines WHERE entry_id=NEW.entry_id) <> 0 THEN
    RAISE EXCEPTION 'journal entry % is not balanced', NEW.entry_id;
  END IF;
  RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER journal_lines_balanced AFTER INSERT ON journal_lines DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_entry_balanced();

-- 시연 초기 잔액: 사용자별 페이머니 50만원 = 은행 충전금 계좌 100만원
INSERT INTO accounts(code,type,allow_negative,balance_krw) VALUES
 ('TRUST_DEPOSIT','ASSET',true,1000000),
 ('CARD_RECEIVABLE','ASSET',true,0),
 ('SETTLEMENT_DEPOSIT','ASSET',true,0),
 ('MERCHANT_PAYABLE','LIABILITY',true,0),
 ('BENEFIT_EXPENSE','EXPENSE',true,0),
 ('OPENING_EQUITY','EQUITY',true,0),
 ('PAYMONEY:00000000-0000-0000-0000-000000000001','LIABILITY',false,500000),
 ('PAYMONEY:00000000-0000-0000-0000-000000000002','LIABILITY',false,500000);
INSERT INTO journal_entries(id,reference_id,kind,amount_krw) VALUES (1,'OPENING','OPENING',1000000);
SELECT setval('journal_entries_id_seq',1);
INSERT INTO journal_lines(entry_id,account_code,debit_krw,credit_krw) VALUES
 (1,'TRUST_DEPOSIT',1000000,0),
 (1,'PAYMONEY:00000000-0000-0000-0000-000000000001',0,500000),
 (1,'PAYMONEY:00000000-0000-0000-0000-000000000002',0,500000);

-- outbox 컨텍스트
CREATE TABLE outbox_events(id bigserial PRIMARY KEY,aggregate_id text NOT NULL,event_type text NOT NULL,payload jsonb NOT NULL,attempts int NOT NULL DEFAULT 0,created_at timestamptz NOT NULL DEFAULT now(),sent_at timestamptz,UNIQUE(aggregate_id,event_type));
CREATE INDEX outbox_unsent ON outbox_events(id) WHERE sent_at IS NULL;

-- settlement 컨텍스트
CREATE TABLE settlement_runs(business_date date PRIMARY KEY,status text NOT NULL CHECK(status IN ('RUNNING','FAILED','COMPLETED')),failed_step text,error text,created_at timestamptz NOT NULL DEFAULT now(),updated_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE settlement_steps(business_date date NOT NULL REFERENCES settlement_runs(business_date),step text NOT NULL,detail text,created_at timestamptz NOT NULL DEFAULT now(),PRIMARY KEY(business_date,step));
CREATE TABLE reconciliation_items(id bigserial PRIMARY KEY,business_date date NOT NULL,step text NOT NULL,transaction_id text NOT NULL,record_type text NOT NULL,kind text NOT NULL,hanki_amount_krw bigint,hanki_status text,institution_amount_krw bigint,institution_status text,resolution text,created_at timestamptz NOT NULL DEFAULT now(),UNIQUE(business_date,step,transaction_id,record_type));
