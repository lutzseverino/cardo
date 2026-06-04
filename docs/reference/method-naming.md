# Method Naming

Methods should read clearly at the call site while avoiding words already supplied by the surrounding owner.

Good names use the smallest precise phrase that communicates the operation. They rely on class, interface, module, package, route, or test context instead of restating that context inside every method.

## Structure

A method name is made of:

- an action: the operation being performed
- an optional qualifier: the extra information needed to distinguish this operation from another operation owned by the same type

The owner supplies the main subject. The method supplies what happens to that subject.

## Rules

- Prefer simple action names when the owner already names the resource or concept.
- Read names from the call site, not in isolation.
- Repeat the subject only when the call site would otherwise be ambiguous.
- Add a qualifier only when the owner exposes multiple operations with the same action.
- Use stable domain vocabulary instead of implementation detail.
- Prefer verbs for commands and mutations.
- Prefer nouns or noun phrases for values, projections, and data holders.
- Keep names symmetric across similar owners when the operations are equivalent.
- Do not add suffixes such as `resource`, `entity`, `model`, or `object` unless they disambiguate a real boundary.
- When a qualifier disambiguates a boundary, keep it next to the action and avoid repeating the owner subject. For example, prefer `getProjection()` over `getTreatmentProjection()` inside `TreatmentService`.
- Do not encode transport, persistence, framework, or generated-code details into product method names.
- Let generated or external contract surfaces be more explicit when they require globally unique names.
- Test method names may rely on the test class context in the same way production methods rely on their owner.

Short names are not a goal by themselves. A longer name is better when it removes real ambiguity, names a distinct domain action, or makes a risky side effect visible.
