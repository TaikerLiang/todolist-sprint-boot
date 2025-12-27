# Feature Specification: General Approval Workflow

**Feature Branch**: `002-approval-workflow`
**Created**: 2025-12-27
**Status**: Draft
**Input**: User description: "I want to design the general approval process and apply this new workflow to the creation/update/delete of todo and invoice item. more specifically, if the user want to do any action except the read on the todo or invoice item, they need to get the approval first. there are three role types of user in the future, "admin", "manager", "user". todo item: for the to item we will use the level info to design the approval rules level = high, then need to get the approvals from both admin and manager. level = medium, then need to get the approval from manager only level = low, no need to get approval invoice item: invoice didn't have the leveling info for the creation and update, then need to get the approvals from manager. for the delete, then need to get the approvals from both admin or manager, one of them is ok. based on the description above, the may use the different senario to design different approval flow, so make the whole desing is general and easy to apply other new item in the future like day-off request. user can send the approval request and wait for the result, but they also can withdraw the request before the approval request going to the final state (apporoved or rejected), need to think carefully here. also need to provide the diff check review mechanism for the review to quick review the difference especially for the update cases. all the actions (creation/update/delete), after they got all the necessary approvals the system need to send the eamil to remind the user, but now we can use the print info to mock the sending email functionality for now."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Submit Approval Request (Priority: P1)

A user wants to create, update, or delete a Todo or Invoice item. Instead of the change taking effect immediately, the system captures their intended change and creates an approval request that goes to the appropriate approvers based on predefined rules.

**Why this priority**: This is the foundation of the approval workflow. Without the ability to submit requests, no other functionality can work. It delivers immediate value by enabling users to initiate changes while maintaining governance.

**Independent Test**: Can be fully tested by a user attempting to create/update/delete a Todo or Invoice item, verifying the approval request is created with the correct status, metadata, and routed to the appropriate approvers based on the approval rules.

**Acceptance Scenarios**:

1. **Given** a user with role "user" wants to create a new Todo with level "high", **When** they submit the creation request, **Then** an approval request is created requiring approvals from both admin and manager
2. **Given** a user wants to update a Todo with level "medium", **When** they submit the update request, **Then** an approval request is created requiring approval from manager only
3. **Given** a user wants to create a Todo with level "low", **When** they submit the creation request, **Then** the Todo is created immediately without requiring approval
4. **Given** a user wants to create a new Invoice, **When** they submit the creation request, **Then** an approval request is created requiring approval from manager
5. **Given** a user wants to update an existing Invoice, **When** they submit the update request, **Then** an approval request is created requiring approval from manager
6. **Given** a user wants to delete an Invoice, **When** they submit the deletion request, **Then** an approval request is created requiring approval from either admin or manager
7. **Given** a user wants to read/view a Todo or Invoice, **When** they access it, **Then** they can view it immediately without approval
8. **Given** a Todo has a pending update or delete approval request, **When** another user tries to submit an update or delete request for the same Todo, **Then** the system prevents the request and shows an error message indicating the item is locked for review
9. **Given** multiple users want to create new Todos, **When** they submit creation requests simultaneously, **Then** the system accepts all creation requests and routes them to appropriate approvers independently

---

### User Story 2 - Review and Approve/Reject Requests (Priority: P2)

An approver (admin or manager) receives approval requests and can review the requested changes. They can see what change is being requested, who requested it, and when. They can then approve or reject the request with optional comments.

**Why this priority**: Once requests are submitted, approvers must be able to act on them. This completes the basic approval workflow loop and enables the system to function. Without this, requests would accumulate with no way to process them.

**Independent Test**: Can be fully tested by pre-creating approval requests in various states (pending, partially approved) and verifying that approvers can view them, see all relevant details, and successfully approve or reject them. The system correctly updates request status and applies the change when all required approvals are obtained.

**Acceptance Scenarios**:

1. **Given** a manager has a pending approval request for a Todo (level "medium"), **When** they approve it, **Then** the request status changes to "approved", the Todo change is applied, and the Todo is unlocked for new requests
2. **Given** an admin has a pending approval request for a Todo (level "high") that already has manager approval, **When** they approve it, **Then** the request status changes to "approved", the Todo change is applied, and the Todo is unlocked for new requests
3. **Given** a manager has a pending approval request for a Todo (level "high") without admin approval, **When** they approve it, **Then** the request status changes to "partially approved", the Todo change is not yet applied, and the Todo remains locked
4. **Given** an approver has a pending approval request, **When** they reject it with a reason, **Then** the request status changes to "rejected", the change is not applied, the item is unlocked for new requests, and the requester can see the rejection reason
5. **Given** an Invoice deletion request requires either admin or manager approval, **When** a manager approves it, **Then** the request status changes to "approved" and the Invoice is deleted
6. **Given** an approver has a pending approval request, **When** they try to approve a request outside their permission scope, **Then** the system prevents the approval and shows an error message

---

### User Story 3 - Withdraw Pending Approval Request (Priority: P3)

A user who submitted an approval request realizes they made a mistake or changed their mind. They can withdraw their pending request before it reaches a final state (approved or rejected), removing it from approvers' queues.

**Why this priority**: This gives users control and reduces wasted effort for approvers. However, the core workflow can function without it, making it lower priority than submission and approval capabilities.

**Independent Test**: Can be fully tested by pre-creating pending approval requests and verifying that the requester can successfully withdraw them, the request status changes to "withdrawn", and approvers no longer see them. Also verify that approved or rejected requests cannot be withdrawn.

**Acceptance Scenarios**:

1. **Given** a user has a pending approval request (status "pending"), **When** they withdraw it, **Then** the request status changes to "withdrawn", it is removed from approvers' queues, and the item is unlocked for new requests
2. **Given** a user has a partially approved request (status "partially approved"), **When** they withdraw it, **Then** the request status changes to "withdrawn", it is removed from approvers' queues, and the item is unlocked for new requests
3. **Given** a user has an approved request (status "approved"), **When** they try to withdraw it, **Then** the system prevents the withdrawal and shows an error message
4. **Given** a user has a rejected request (status "rejected"), **When** they try to withdraw it, **Then** the system prevents the withdrawal and shows an error message
5. **Given** a user tries to withdraw another user's approval request, **When** they attempt the withdrawal, **Then** the system prevents it and shows an error message

---

### User Story 4 - View Diff for Update Requests (Priority: P4)

When an approver reviews an update request for a Todo or Invoice, they can see a clear comparison showing what fields are changing, displaying both the current values and the proposed new values side by side.

**Why this priority**: This significantly improves reviewer efficiency and decision quality, especially for complex updates. However, approvers can still review and approve requests without it by viewing the new values directly, making it an enhancement rather than a core requirement.

**Independent Test**: Can be fully tested by creating update requests with various field changes and verifying that approvers see a clear diff view showing old vs new values for each changed field. For create and delete requests, appropriate views are shown (new values for create, current values for delete).

**Acceptance Scenarios**:

1. **Given** an approver is reviewing an update request for a Todo, **When** they open the request details, **Then** they see a diff view showing old and new values for each changed field
2. **Given** an approver is reviewing an update request for an Invoice, **When** they open the request details, **Then** they see a diff view showing old and new values for each changed field
3. **Given** an approver is reviewing a creation request, **When** they open the request details, **Then** they see only the new values to be created (no old values)
4. **Given** an approver is reviewing a deletion request, **When** they open the request details, **Then** they see the current values that will be deleted
5. **Given** an update request changes multiple fields, **When** the approver views the diff, **Then** only the changed fields are highlighted, with unchanged fields either hidden or clearly marked as unchanged

---

### User Story 5 - Receive Notification on Approval Completion (Priority: P5)

After an approval request receives all required approvals and the change is applied, the original requester receives an email notification informing them that their request was approved and the change has been completed.

**Why this priority**: Notifications improve user experience by providing closure and confirmation, but the workflow functions completely without them. Users can check request status manually. This is a quality-of-life enhancement.

**Independent Test**: Can be fully tested by completing approval requests (getting all required approvals) and verifying that the system sends (prints) a notification to the requester with details of the approved request and the applied change.

**Acceptance Scenarios**:

1. **Given** an approval request has received all required approvals, **When** the system applies the change, **Then** an email notification is sent (printed) to the requester confirming the approval and change
2. **Given** an approval request is rejected, **When** the rejection is recorded, **Then** an email notification is sent (printed) to the requester with the rejection reason
3. **Given** the email sending functionality is not yet implemented, **When** a notification should be sent, **Then** the system prints the notification details to the console/log for verification

---

### Edge Cases

- What happens when an approver who has already approved a request tries to approve it again?
- How does the system handle when an admin or manager role is assigned to a user who has pending approval requests as a regular user?
- How does the system handle concurrent approvals (two approvers approving at the exact same time)?
- What happens when there are no users with the required approver role (e.g., no managers in the system)?
- How does the system handle partial approvals when additional approvers are needed but not available?
- What happens when a user tries to create multiple update/delete approval requests for the same existing item simultaneously (race condition)?
- What happens when an update/delete approval request is withdrawn - does the item immediately become available for new approval requests?
- How does the system handle when a user views an item that has a pending update approval request showing different values?
- What happens when an admin needs to make an urgent change to an item that has a pending update or delete approval request?
- How does the system differentiate between "similar" creation requests to determine if they should be treated as duplicates or independent requests?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST support three user role types: "admin", "manager", and "user"
- **FR-002**: System MUST block all create, update, and delete operations on Todo and Invoice items that require approval until approval is granted
- **FR-003**: System MUST allow read operations on Todo and Invoice items without requiring approval
- **FR-004**: System MUST create an approval request when a user attempts a create, update, or delete operation that requires approval
- **FR-005**: System MUST determine required approvers based on configurable approval rules specific to each item type and operation
- **FR-006**: System MUST apply the following approval rules for Todo items based on their level field:
  - Level "high": Requires approval from both admin AND manager (all required)
  - Level "medium": Requires approval from manager only
  - Level "low": No approval required (immediate execution)
- **FR-007**: System MUST apply the following approval rules for Invoice items:
  - Create operation: Requires approval from manager
  - Update operation: Requires approval from manager
  - Delete operation: Requires approval from either admin OR manager (one sufficient)
- **FR-008**: System MUST track approval request status with states: pending, partially approved, approved, rejected, withdrawn
- **FR-009**: System MUST allow users to withdraw their approval requests only when status is "pending" or "partially approved"
- **FR-010**: System MUST prevent withdrawal of approval requests with status "approved" or "rejected"
- **FR-011**: System MUST allow authorized approvers to approve or reject approval requests
- **FR-012**: System MUST verify that approvers have the correct role before allowing them to approve requests
- **FR-013**: System MUST support approval rules requiring multiple specific approvers (e.g., admin AND manager)
- **FR-014**: System MUST support approval rules requiring any one from a set of approvers (e.g., admin OR manager)
- **FR-015**: System MUST apply the requested change (create/update/delete) only after all required approvals are obtained
- **FR-016**: System MUST provide a diff view for update requests showing old values vs new values for changed fields
- **FR-017**: System MUST send an email notification to the requester when their approval request is approved (mock with print statement)
- **FR-018**: System MUST send an email notification to the requester when their approval request is rejected (mock with print statement)
- **FR-019**: System MUST store approval request metadata including: requester, request type (create/update/delete), target item type, target item ID (for update/delete), requested data (for create/update), timestamp, current status
- **FR-020**: System MUST store individual approval records including: approver, approval request ID, approval decision (approve/reject), optional comment, timestamp
- **FR-021**: System MUST be designed in a general way that allows easy addition of approval rules for new item types in the future (e.g., day-off requests)
- **FR-022**: System MUST prevent users from modifying or deleting approval requests created by other users
- **FR-023**: System MAY allow users with approver roles (admin or manager) to approve their own requests
- **FR-024**: System MUST allow multiple pending or partially approved creation requests for Todo and Invoice items (creation requests do not lock anything)
- **FR-025**: System MUST prevent creation of new approval requests (update or delete) for an existing item that already has a pending or partially approved approval request for update or delete
- **FR-026**: System MUST lock existing items from being modified or deleted while they have pending or partially approved approval requests for update or delete operations
- **FR-027**: System MUST unlock items when their approval request reaches a final state (approved, rejected, or withdrawn)

### Key Entities

- **User**: Represents a system user with one of three roles (admin, manager, user). Users can submit approval requests and, if they have admin or manager roles, can also approve requests.

- **Approval Request**: Represents a pending change to a Todo or Invoice item. Contains the operation type (create/update/delete), target item type, target item identifier, requested data, current status, requester information, and timestamps.

- **Approval Record**: Represents a single approval or rejection action by an approver on an approval request. Links to the approval request, records the approver, decision (approve/reject), optional comment, and timestamp.

- **Approval Rule**: Defines the approval requirements for a specific item type and operation. Specifies which approvers are required and whether all are needed (AND logic) or any one is sufficient (OR logic). Designed to be configurable and extensible for new item types.

- **Todo Item**: Existing entity with a level field (high, medium, low) that determines approval requirements for operations on it. Can be locked when it has a pending or partially approved approval request for update or delete operations, preventing new update/delete approval requests until the current request is resolved.

- **Invoice Item**: Existing entity without leveling. Approval requirements are based solely on the operation type. Can be locked when it has a pending or partially approved approval request for update or delete operations, preventing new update/delete approval requests until the current request is resolved.

- **Change Diff**: For update operations, represents the difference between current and requested values, showing which fields are changing and their old vs new values.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Users can submit approval requests for Todo and Invoice create/update/delete operations in under 30 seconds
- **SC-002**: Approvers can review and approve/reject approval requests in under 60 seconds
- **SC-003**: Users can withdraw pending approval requests in under 15 seconds
- **SC-004**: System correctly routes 100% of approval requests to the appropriate approvers based on defined rules
- **SC-005**: System prevents 100% of unauthorized changes (changes that require approval are blocked until approved)
- **SC-006**: Diff view for update requests displays all changed fields with old and new values accurately
- **SC-007**: System sends (prints) email notifications within 5 seconds of approval request completion (approval or rejection)
- **SC-008**: 90% of approvers report that the diff view helps them make faster and more confident approval decisions
- **SC-009**: System supports adding new item types with custom approval rules without requiring changes to core approval workflow logic
- **SC-010**: Zero instances of approved changes failing to be applied or unapproved changes being applied incorrectly
- **SC-011**: System prevents 100% of attempts to create duplicate update or delete approval requests for items with pending or partially approved update or delete requests
- **SC-012**: System allows unlimited concurrent creation approval requests without any locking or conflicts
- **SC-013**: Items are unlocked and available for new update/delete approval requests within 1 second of their approval request reaching a final state (approved, rejected, or withdrawn)

## Assumptions

- Email functionality is not yet implemented; console/log printing will be used to mock email sending
- Users are already authenticated and their roles are established through an existing authentication system
- The existing Todo and Invoice entities can be extended or have related approval data stored separately
- Concurrent approval scenarios are rare and can be handled with standard database transaction mechanisms
- Users will have appropriate permissions to read Todo and Invoice items to understand what they're requesting changes to
- The system will maintain a complete audit trail of all approval activities for compliance and debugging
- Users with approver roles (admin or manager) are allowed to approve their own requests, providing flexibility for managers and administrators
- Only one update or delete approval request can be active (pending or partially approved) for an existing item at any given time to prevent confusion and conflicts
- Multiple creation approval requests can exist simultaneously since there is no existing item to reference or lock
- Existing items remain readable while they have pending update/delete approval requests; only write operations (update/delete) are blocked
- Item locking is automatically managed by the approval workflow and does not require manual intervention
- Creation requests are considered independent even if they contain similar data, as determining "sameness" for non-existent items is not feasible
