DELETE FROM quartz.cron_triggers WHERE trigger_group in ('retentionPolicies', 'refreshViews');
DELETE FROM quartz.triggers WHERE trigger_group in ('retentionPolicies', 'refreshViews');
DELETE FROM quartz.job_details WHERE job_group in ('retentionPolicies', 'metricsJobs');
