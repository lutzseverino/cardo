# Invite

Invite owns cross-product invitation token lifecycle operations.

## Product Integration

Products currently integrate with Invite through its service API. Invite uses
Identity for provisional users and Authorization for grant staging, but product
access remains expressed through product-owned resource types and access
profiles.

There is no stable Java client or HTTP client module yet. Add them when a real
product needs to call Invite from backend code. Add a product integration module
only if multiple products repeat the same invite lifecycle wiring.

Products still own why an invitation is created, which tenant/resource it
targets, which access profile is used, and what product lifecycle should happen
around acceptance.
