CREATE TABLE auth.api_key (
  id serial PRIMARY KEY,
  user_id INTEGER REFERENCES auth.user (id) NOT NULL,
  description VARCHAR (200) NOT NULL,
  api_key_hash VARCHAR (100) NOT NULL,
  expires_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
  created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL
);

CREATE INDEX api_key_user_id_idx ON auth.api_key (user_id);
