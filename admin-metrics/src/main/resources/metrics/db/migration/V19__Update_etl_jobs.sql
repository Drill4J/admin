UPDATE metrics.etl_jobs
SET etl_name = 'incremental'
WHERE etl_name = 'metrics' AND upper(period) = '2100-01-01';

UPDATE metrics.etl_jobs
SET etl_name = 'historical'
WHERE etl_name = 'metrics' AND upper(period) = '2100-01-01';