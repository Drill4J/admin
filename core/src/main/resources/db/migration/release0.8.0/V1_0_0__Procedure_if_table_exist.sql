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

CREATE OR REPLACE PROCEDURE execute_if_table_exist(tableName text, sql text)
    LANGUAGE plpgsql
AS
$func$
BEGIN
    IF EXISTS
        (SELECT
         FROM information_schema.tables
         WHERE table_schema = 'admin'
           AND table_name = tableName
        )
    THEN
        EXECUTE sql || ' ;' ;
    ELSE
        RAISE NOTICE 'Skip migration because table ''%'' does not exist.', tableName;
    END IF;
end
$func$;

