package com.odonta.billing.repository;

import com.odonta.billing.model.StripeEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StripeEventRepository extends JpaRepository<StripeEvent, String> {}
