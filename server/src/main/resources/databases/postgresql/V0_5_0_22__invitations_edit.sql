CREATE INDEX invitations__consumed_by_user_id ON invitations (consumed_by_user_id) WHERE consumed_by_user_id IS NOT NULL;
