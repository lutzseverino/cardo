CREATE INDEX idx_billing_customer_provisioning_terminal
  ON billing_customer_provisioning_operations (status, updated_at)
  WHERE status = 'FAILED';
