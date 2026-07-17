# Application Boundaries

This reference is authoritative for application ownership, dependency direction, data shapes, and
boundary naming in domain-oriented Spring Boot applications.

Projects may add a local reference for their package layout, domain vocabulary, and deliberate
exceptions. A local reference may make these rules narrower, but it should not silently redefine
their terms or weaken a boundary.

## Core Model

An application is organized around semantic owners: stable domain concepts or application
capabilities with a clear reason to change. Framework types, route nesting, database tables, and
file size do not establish ownership by themselves.

Values normally cross the application like this:

```text
Request -> Input -> Service or Workflow -> Result -> Response
                         ^                    ^
                         |                    |
                   domain behavior       Projection
```

Not every operation needs every type. Bodyless operations may use ordinary parameters, mutations
may return no result, and operations may produce results from an explicit outcome or updated domain
state rather than a projection.

The application boundary is the stable center:

- transport types do not become application contracts;
- persistence types do not escape through application contracts;
- application types do not depend on how transport code is generated or data is queried;
- side effects and transactions remain owned by a visible application entrypoint.

## Choosing An Owner

Use the smallest boundary that truthfully owns the behavior.

| Boundary | Use it when | Do not use it for |
| --- | --- | --- |
| `...Service` | One stable domain or application owner can explain the operations. | A use case whose only apparent owner is the route, caller, or largest entity involved. |
| `{Verb}{Object}Workflow` | One effectful use case coordinates independent owners. | A named transition that still belongs completely to one service owner. |
| `...Resolver` | Reusable, repository-backed lookup reconstructs a domain set or context without effects. | A public use case, transaction owner, generic entity reader, or mutation. |
| `...Evaluator` | Pure rules calculate an answer from supplied values. | Repository access, orchestration, or effects. |
| `...Planner` | Pure or declarative logic describes work that another component applies. | Applying the planned side effects itself. |
| `...Adapter`, `...Client`, `...Sender`, or `...Handler` | Code crosses an infrastructure or integration boundary. | Hiding an application use case behind an infrastructure name. |
| `...Mapper` | Declarative code translates between two owned data shapes. | Repository access, authorization, orchestration, or business rules. |
| `...Controller` | An inbound transport adapter implements one semantic transport owner. | Persistence access or application behavior. |

Use this decision order:

1. If one stable owner explains the behavior, put it in that owner's service.
2. If a transaction or effectful use case coordinates independent owners, create a workflow.
3. If several owners are only being located to establish reusable context, use a resolver.
4. If the operation only calculates, plans, maps, or adapts, use the narrower role named above.
5. Do not introduce a boundary only to shorten a class or prepare for hypothetical reuse.

An entity is a strong ownership signal, not an automatic reason to create a service. Aggregate
roots and independently meaningful concepts commonly earn services; subordinate records, join
entities, and value objects commonly do not. Conversely, a stable capability such as
authentication may earn a service without owning a persisted entity.

Calling several dependencies does not automatically create a workflow. A service may coordinate
repositories and infrastructure ports when those collaborators implement the lifecycle of the
same owner. The split is caused by independent reasons to change, not dependency count.

## Services

An application service owns the public operations and reusable rules of one stable semantic owner.

- Use the `Service` suffix for this role. In Spring applications, use the `@Service` stereotype for
  application-service beans.
- Keep owner reads, owner mutations, and reusable derived owner rules together while they retain the
  same reason to change.
- Accept application-owned inputs or ordinary context values and return application-owned results
  or no value.
- Keep entities, projections, generated transport models, and provider SDK types out of public
  methods.
- A service may depend on repositories, application mappers, authorization policies, and
  infrastructure ports belonging to its owner.
- A service must not depend on a workflow. The stable owner must remain usable without a particular
  cross-owner use case.
- Do not use `Service` as a generic suffix for any injectable class. Name infrastructure and
  supporting mechanics for their actual roles.

These rules apply to public embedded Java services as well as Spring-managed HTTP application
services. The absence of `@Service` does not make a persistence projection or entity a suitable
public library contract.

## Workflows

A workflow is a public application entrypoint for one effectful use case that coordinates
independent owners.

- Name it `{Verb}{Object}Workflow`, using the domain verb and object visible at the call site.
- In Spring applications, use `@Component` unless the project defines a more specific stereotype.
- Let the workflow own the transaction when the coordinated state change is atomic within one
  transactional resource.
- Let controllers call workflows directly; do not preserve a thin service facade around a
  workflow merely to keep a controller-to-service shape.
- Workflows may coordinate services, repositories, policies, integration ports, and narrow local
  collaborators.
- Workflows must not call other workflows. Extract the shared lower-level owner operation or
  collaborator instead.
- Keep reusable owner rules in the relevant service rather than duplicating them in workflows.
- Keep workflow-specific helpers local until another owner demonstrates real reuse.

The phrase *workflow action* also describes an HTTP or domain operation such as activate, approve,
cancel, complete, or certify. It does not imply that every such endpoint requires a `...Workflow`
class. A transition that belongs entirely to one owner remains a service method.

## Resolvers And Supporting Roles

A resolver reconstructs reusable application context. It may query repositories, but it does not
own effects or a transaction.

- Give a resolver one narrow question, such as resolving the active membership or current policy
  context.
- Return application or domain context needed by another application owner.
- Do not expose a resolver directly as the public use case when a service or workflow owns the
  operation.
- Do not move an entire service implementation into a resolver while leaving a thin service facade.
- Do not create generic `Reader` or `Writer` wrappers around ordinary repository operations.

`Reader` remains appropriate when reading is itself a precise boundary role, such as reading the
current security principal, a token, a stream, or an external document. The problem is generic
entity access without a distinct responsibility, not the word `Reader`.

Package-private workflow collaborators should use precise responsibility names such as
`Resolver`, `Planner`, `Applicator`, `Evaluator`, or `Adapter`; they do not use the `Workflow`
suffix unless they are public application entrypoints.

## Dependency Direction

Keep application dependency direction visible:

```text
Controller -> Service
Controller -> Workflow -> Service
                       -> Repository
                       -> Policy or integration port

Service -X-> Workflow
Workflow -X-> Workflow
Controller -X-> Repository or Resolver
```

- Controllers own transport interaction, authentication extraction, transport mapping, and HTTP
  response construction.
- Services and workflows own application behavior.
- Repositories own persistence access.
- Domain objects own invariants and state transitions that can be expressed without application
  orchestration.
- Integration adapters own provider-specific protocols and SDK interaction.

Put transactional boundaries on the public application entrypoint that owns the state change.
Resolvers and pure collaborators do not own transactions. When an external side effect cannot
participate in the local transaction, make its ordering, retry, compensation, or after-commit
behavior explicit in the owning service or workflow.

## Application Contract Types

Names describe ownership, not merely data direction.

| Suffix | Owner | Role |
| --- | --- | --- |
| `...Request` | Transport contract | Carries inbound transport data. |
| `...Input` | Application | Carries structured input to an application operation. |
| `...Criteria` | Application or persistence port | Groups meaningful read filters, sorting, or pagination. |
| `...Projection` | Persistence | Carries repository query output without exposing an entity. |
| `...Result` | Application | Carries output promised by an application operation. |
| `...Response` | Transport contract | Carries outbound transport data. |

Use ordinary parameters for simple identifiers, scalar values, and context. Do not introduce an
input record solely to wrap one identifier.

Reserve `...Command` for a value dispatched through an explicit command execution model, such as a
command bus and handler. A mutation input is not a command merely because it changes state.

Public service and workflow methods:

- accept application inputs, domain value objects, security principals, or ordinary context
  values;
- return application results, domain values deliberately established as application contracts, or
  no value;
- do not accept or return generated requests, responses, provider SDK types, repository
  projections, or persistence entities.

Domain value objects, domain events, validation errors, pagination metadata, security principals,
audit records, integration DTOs, entities, and infrastructure configuration keep vocabulary
faithful to their own roles. Do not rename every type to `Input` or `Result` merely to make it fit
the operation flow.

## Operation And Method Shapes

- Resource operations create, retrieve, update, or remove a resource representation.
- Queries retrieve lists, searches, reports, summaries, or other read models without mutation.
- Workflow actions perform named domain transitions such as activate, cancel, restore, approve,
  reject, or complete.

Use operation-specific names when CRUD vocabulary would hide domain meaning. Prefer
`ActivateAccountInput` and `ActivationResult` to a generic update payload for activation.

At Java call sites, let the owner supply the subject: `invitationService.get(token)` is clearer than
`invitationService.getInvitation(token)`. Repeat the subject only when required to distinguish
operations. Follow the complete rules in [Method Naming](method-naming.md).

Generated or external operation identifiers may be more explicit because they must be unique
outside the owner context.

## Persistence And Materialization

Repository methods make their materialization behavior visible.

- Use projections or scalar values for ordinary reads.
- Name entity-owned projections after the entity, such as `AccountProjection`.
- Keep a generic entity projection limited to fields owned by that entity. Do not borrow labels,
  display names, or versions from neighboring owners merely because a join is convenient.
- Use repository joins for filtering, existence predicates, locks, and persistence constraints when
  the selected data remains owned by the repository entity.
- Treat cross-owned projections as exceptions for concrete read models that cannot be assembled
  cleanly by owners. Name them after the actual view, not `Slice`, `Data`, or a misleading entity
  summary.
- Let the application owner assemble results that need data from multiple owners by asking each
  owner for its data.

Materialize an entity only for a mutation-side state transition that genuinely benefits from a
managed aggregate. Prefer projections, scalar predicates, and explicit repository updates for
application flow that does not need entity behavior.

Custom repository methods returning entities include `Entity` in the name, such as
`findEntityByEmail` or `findEntitiesByTenantId`. This makes exceptional mutation-side materialization
visible at the call site. Inherited repository methods such as `findById` cannot be renamed; keep
their entity-returning use on mutation paths.

Do not add repository adapters, readers, writers, or query services merely to conceal a mapper call
or rename a standard repository operation. Add a boundary only when it owns meaningful behavior.

## Mapper Boundaries

Mappers are named for the semantic owner and boundary they cross:

- `{Owner}TransportMapper` maps `Request -> Input` and `Result -> Response`.
- `{Owner}ApplicationMapper` maps persistence or domain output into an application result;
  `Projection -> Result` is the common query case.

The inbound transport adapter invokes the transport mapper. The service or workflow that owns the
application operation invokes the application mapper.

- A transport mapper must not accept or return projections, entities, repositories, or other
  persistence-owned types.
- An application mapper must not accept or return requests, responses, controllers, or other
  transport-owned types.
- Do not combine transport and persistence mappings in one mapper.
- Do not group unrelated owners in one mapper because one caller happens to need them.
- An aggregate response mapper may delegate nested fields to their canonical owner mappers. It must
  not absorb those owners' request mappings or reimplement their nested response mappings.
- Do not create one mapper per request or response unless that type is itself the stable owner.
- Keep mapper interfaces declarative. Do not use default methods for orchestration, repository
  access, authorization, response assembly, or unrelated object construction.
- Put pure conversions that MapStruct cannot express in a dedicated helper referenced by the
  mapper.
- If result construction requires owner lookups or decisions, assemble it in the owning service or
  workflow rather than hiding those dependencies in a mapper.

Use strict MapStruct reporting for unmapped source and target properties. Mapper configuration may
remain local to independently compiled modules. Share it only through a dependency intentionally
owned by every participating module.

## Transport Owners

A transport owner represents one semantic resource or cohesive use-case family.

- Name a controller after its canonical singular owner.
- Keep a controller's application dependencies within that owner's services, workflows, transport
  mapper, and authentication boundary.
- Controllers do not call repositories or resolvers directly.
- Route nesting does not transfer ownership. A nested route belongs to the object or capability it
  changes or returns, not automatically to the parent path.
- Do not group unrelated owners under broad product-area umbrellas merely because they share a route
  prefix.
- When using generated APIs, align each tag, generated interface, and implementing controller with
  one transport owner or cohesive use-case family.
- Preserve operation identifiers, paths, methods, parameters, payloads, responses, and errors when
  changing transport ownership without changing behavior.

## Partial Updates

Choose the transport operation from its semantics before applying partial-update mechanics:

- Use PATCH when omitted fields mean "leave unchanged."
- Use PUT for complete replacement, including an idempotent value such as one caller's vote.
- Use POST for named workflow actions such as activate, approve, certify, or complete.

Do not convert PUT or workflow POST operations to PATCH solely to make modules uniform. The
cross-module invariant is boundary ownership; partial-update presence is an additional mechanic
used only where the operation requires it.

Transport-specific presence wrappers stay at the transport boundary. A local `...PatchAdapter`
converts absent, explicit-null, and concrete values into an application-owned representation.
Generator-specific wrappers do not appear in application inputs.

Use `FieldUpdate<T>` or an equivalent application-owned value for nullable fields when all three
states matter:

- absent leaves the field unchanged;
- present with `null` clears the field;
- present with a value assigns the value.

Do not use partial-update wrappers in create inputs, complete replacements, non-nullable updates,
results, or domain state.

## Collections And Enumerated Values

Return `List<ItemResponse>` when the transport response is only an unpaginated collection.
Introduce a named response wrapper only when it carries contract meaning such as pagination,
cursors, totals, links, summaries, or multiple collections.

Generated transport enums remain transport-owned. Map them to application or domain enums at the
transport boundary even when the current values are identical. Do not configure transport
generation to substitute application enums; independent types allow either contract to evolve.

## Package Placement

These roles do not prescribe one universal package tree. A project may organize mechanical roles
at the top level, colocate them in vertical features, or combine both approaches.

Whichever layout it chooses:

- keep each role recognizable from its name, dependencies, and public seam;
- keep one canonical home for each role in the local area;
- do not create parallel conventions for equivalent owners;
- document the project-specific package map and deliberate deviations in a local reference.

## Exceptions

Before adding a new layer, package role, generic reader or writer, cross-owned projection, or
non-owner service, first try to express the behavior with an existing boundary.

An exception is warranted only when the standard shape would misrepresent ownership, widen a
contract, hide important performance behavior, or obscure a necessary integration boundary.
Document the reason and keep the exception as narrow as possible. Do not derive a new convention
from one exceptional case until another independent owner proves the same need.

When reviewing a boundary, ask:

1. What stable concept or use case owns this code?
2. Does its name describe that owner and its architectural role?
3. Are its public inputs and outputs owned by the application?
4. Are transaction and side-effect boundaries visible?
5. Does persistence materialization remain private and intentional?
6. Do mappers and controllers cross only their assigned boundaries?
7. Would a new contributor know where the next equivalent operation belongs?
