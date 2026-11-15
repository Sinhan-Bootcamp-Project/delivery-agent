-- FEP (fep 스키마). 전문 원문은 마스킹 후 저장한다.
CREATE TABLE messages(id bigserial PRIMARY KEY,transaction_id text NOT NULL,direction text NOT NULL CHECK(direction IN ('REQUEST','RESPONSE')),message_type text NOT NULL,institution text NOT NULL,response_code text,body jsonb NOT NULL,fault text,elapsed_ms bigint,created_at timestamptz NOT NULL DEFAULT now());
CREATE INDEX messages_transaction ON messages(transaction_id);
CREATE TABLE faults(institution text NOT NULL,message_type text NOT NULL,mode text NOT NULL CHECK(mode IN ('DELAY','NO_RESPONSE','RESPONSE_LOST','REJECT')),delay_ms bigint NOT NULL DEFAULT 0,reject_code text NOT NULL,remaining int NOT NULL CHECK(remaining>=0),PRIMARY KEY(institution,message_type));
