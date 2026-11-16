CREATE TABLE app_users (
    username VARCHAR(80) PRIMARY KEY,
    password_hash VARCHAR(60) NOT NULL
);
