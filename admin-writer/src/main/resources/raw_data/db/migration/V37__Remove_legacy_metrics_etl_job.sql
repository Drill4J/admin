DELETE FROM quartz.cron_triggers
WHERE trigger_name = 'etlTrigger'
  AND trigger_group = 'drill';

DELETE FROM quartz.triggers
WHERE trigger_name = 'etlTrigger'
  AND trigger_group = 'drill';

DELETE FROM quartz.job_details
WHERE job_name = 'metricsEtl'
  AND job_group = 'drill';
