CREATE TABLE auth.user (
  id serial PRIMARY KEY,
  username VARCHAR (100) NOT NULL,
  password_hash VARCHAR (100),
  role VARCHAR (20) NOT NULL,
  blocked BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE UNIQUE INDEX user_username_idx ON auth.user ((lower(username)));

--INSERT DEFAULT USER user:user
INSERT INTO auth.user (username, password_hash, role)
VALUES ('admin', '$2a$10$Aach5gd4gTGUFXemUEtA/OT2i7bveGi9af1n5xqDqSjWmeZ7I27oe', 'ADMIN');
--INSERT DEFAULT USER admin:admin
INSERT INTO auth.user (username, password_hash, role)
VALUES ('user', '$2a$10$cnuotKyF9YlzChdEEuLLfeCstYkH7C65zbVX1VHmABPKp4S8lmG1C', 'USER');
