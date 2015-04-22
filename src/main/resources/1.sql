DROP TABLE users;
-- DROP TYPE gender;

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- CREATE TYPE gender AS ENUM ('male', 'female');

CREATE TABLE users (
    id UUID PRIMARY KEY,
    name VARCHAR,
    email VARCHAR UNIQUE,
    password VARCHAR,
    gender VARCHAR,
    pin CHAR(4) CHECK (pin ~* '^[0-9]+$')
);
