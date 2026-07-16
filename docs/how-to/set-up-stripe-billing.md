# Set Up Stripe Billing

Use this guide to connect the billing boundary to Stripe in a test environment.

Billing treats Stripe as a provider adapter. Local entitlement state should remain provider-neutral so product services do not depend on Stripe vocabulary.

## Steps

1. Create or open a Stripe account in test mode.
2. Create a Stripe product for each sellable product.
3. Create a recurring Stripe price for each product.
4. Create Stripe Entitlements features for application access.
5. Map each Stripe product to the entitlement features it grants.
6. Optional: add feature metadata for limits the application needs to project locally.
7. Enable the Stripe Customer Portal for self-service subscription management.
8. Configure the billing service environment:

   ```sh
   STRIPE_SECRET_KEY=sk_test_...
   STRIPE_WEBHOOK_SECRET=whsec_...
   STRIPE_CLINIC_PRICE_ID=price_...
   STRIPE_POLITY_PRICE_ID=price_...
   ```

9. Forward Stripe webhooks to the billing service:

   ```sh
   stripe listen --forward-to localhost:8085/api/v1/billing/webhooks/stripe
   ```

10. Create a checkout session as an authenticated user.
11. Complete payment with a Stripe test card.
12. Verify that the local entitlement projection becomes active after Stripe sends the entitlement summary webhook.

## Verification

Confirm that the billing service receives the Stripe webhook and updates the local entitlement projection for the authenticated user.

## Notes

- Store webhook events by provider and provider event id before returning success so duplicate deliveries are harmless.
- Use Stripe Entitlements as the access source of truth.
- Use portal sessions for customer self-service instead of rebuilding subscription management locally.
