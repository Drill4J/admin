--
-- Copyright 2020 - 2022 EPAM Systems
--
-- Licensed under the Apache License, Version 2.0 (the "License");
-- you may not use this file except in compliance with the License.
-- You may obtain a copy of the License at
--
-- http://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing, software
-- distributed under the License is distributed on an "AS IS" BASIS,
-- WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
-- See the License for the specific language governing permissions and
-- limitations under the License.
--

CREATE TABLE auth.user (
  id serial PRIMARY KEY,
  username VARCHAR (30) UNIQUE NOT NULL,
  password_hash VARCHAR (100) NOT NULL,
  role VARCHAR (20) NOT NULL,
  blocked BOOLEAN NOT NULL DEFAULT FALSE,
  deleted BOOLEAN NOT NULL DEFAULT FALSE
);

--INSERT DEFAULT USER user:user
INSERT INTO auth.user (username, password_hash, role)
VALUES ('admin', '$2a$10$Aach5gd4gTGUFXemUEtA/OT2i7bveGi9af1n5xqDqSjWmeZ7I27oe', 'ADMIN');
--INSERT DEFAULT USER admin:admin
INSERT INTO auth.user (username, password_hash, role)
VALUES ('user', '$2a$10$cnuotKyF9YlzChdEEuLLfeCstYkH7C65zbVX1VHmABPKp4S8lmG1C', 'USER');
