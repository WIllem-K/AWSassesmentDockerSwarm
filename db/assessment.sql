CREATE TABLE eurtousd (
  call_id  SERIAL,
  conversion DECIMAL NOT NULL,
  inserted_time TIMESTAMP DEFAULT NOW(),
  PRIMARY KEY (call_id)
);
CREATE TABLE usdtoeur (
  call_id SERIAL,
  conversion DECIMAL NOT NULL,
  inserted_time TIMESTAMP DEFAULT NOW(),
  PRIMARY KEY (call_id)
);