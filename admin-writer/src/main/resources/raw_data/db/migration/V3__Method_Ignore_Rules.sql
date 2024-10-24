CREATE TABLE IF NOT EXISTS raw_data.method_ignore_rules
(
    id SERIAL PRIMARY KEY,
    group_id VARCHAR,
    app_id VARCHAR,
    name_pattern VARCHAR NULL,
    classname_pattern VARCHAR NULL
);
