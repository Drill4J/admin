ALTER TABLE metrics.builds ADD COLUMN first_instance_created_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE metrics.builds ADD COLUMN last_instance_heartbeat_at TIMESTAMP WITH TIME ZONE;