DROP TABLE users;

CREATE TABLE users (
  id VARCHAR2(20 BYTE),
  password BLOB,
  salt BLOB,
  CONSTRAINT users_pk PRIMARY KEY (id)
);

COMMIT;
