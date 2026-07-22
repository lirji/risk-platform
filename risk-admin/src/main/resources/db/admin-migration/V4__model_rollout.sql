ALTER TABLE model_version
    ADD COLUMN rollout_percentage INT NOT NULL DEFAULT 0;
