package com.example.todolist.service;

import com.example.todolist.model.ApprovalRule;
import com.example.todolist.model.RequestOperation;
import com.example.todolist.model.Role;
import com.example.todolist.model.RoleRequirement;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service for evaluating and applying approval rules.
 * Rules are defined in code via ApprovalRulesConfig.
 */
@Service
public class ApprovalRuleService {

    private final List<ApprovalRule> approvalRules;

    @Autowired
    public ApprovalRuleService(List<ApprovalRule> approvalRules) {
        this.approvalRules = approvalRules;
    }

    /**
     * Find the matching approval rule for the given item type, operation, and data.
     * Returns the highest-priority matching rule, or empty if no rule matches.
     *
     * @param itemType The target item type (e.g., "TODO", "INVOICE")
     * @param operation The operation being performed (CREATE, UPDATE, DELETE)
     * @param itemData The item data to evaluate conditions against
     * @return Optional containing the matching rule, or empty if no approval required
     */
    public Optional<ApprovalRule> findMatchingRule(
            String itemType,
            RequestOperation operation,
            Map<String, Object> itemData
    ) {
        return approvalRules.stream()
                .filter(rule -> rule.matches(itemType, operation, itemData))
                .max(Comparator.comparing(ApprovalRule::getPriority));
    }

    /**
     * Check if the given approvals satisfy the rule requirements.
     *
     * For rules with mandatory roles: ALL mandatory roles must approve
     * For rules with only optional roles: At least ONE optional role must approve
     *
     * @param rule The approval rule to check against
     * @param approverRoles The set of roles that have approved
     * @return true if the rule is satisfied, false otherwise
     */
    public boolean isRuleSatisfied(ApprovalRule rule, List<Role> approverRoles) {
        Map<Role, RoleRequirement> roleRequirements = rule.getRoleRequirements();

        // Check if all mandatory roles have approved
        List<Role> mandatoryRoles = roleRequirements.entrySet().stream()
                .filter(entry -> entry.getValue().isMandatory())
                .map(Map.Entry::getKey)
                .toList();

        boolean allMandatoryApproved = mandatoryRoles.isEmpty() ||
                approverRoles.containsAll(mandatoryRoles);

        if (!allMandatoryApproved) {
            return false;
        }

        // If there are mandatory roles, we're done (all mandatory approved)
        if (!mandatoryRoles.isEmpty()) {
            return true;
        }

        // If there are only optional roles, need at least one approval
        List<Role> optionalRoles = roleRequirements.entrySet().stream()
                .filter(entry -> !entry.getValue().isMandatory())
                .map(Map.Entry::getKey)
                .toList();

        return optionalRoles.stream().anyMatch(approverRoles::contains);
    }

    /**
     * Get the set of mandatory roles for a rule
     */
    public List<Role> getMandatoryRoles(ApprovalRule rule) {
        return rule.getRoleRequirements().entrySet().stream()
                .filter(entry -> entry.getValue().isMandatory())
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    /**
     * Get the set of optional roles for a rule
     */
    public List<Role> getOptionalRoles(ApprovalRule rule) {
        return rule.getRoleRequirements().entrySet().stream()
                .filter(entry -> !entry.getValue().isMandatory())
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    /**
     * Get a human-readable description of the approval requirements
     */
    public String getRequirementDescription(ApprovalRule rule) {
        List<Role> mandatory = getMandatoryRoles(rule);
        List<Role> optional = getOptionalRoles(rule);

        StringBuilder sb = new StringBuilder();

        if (!mandatory.isEmpty()) {
            sb.append("Requires approval from: ");
            sb.append(mandatory.stream().map(Enum::name).collect(Collectors.joining(", ")));
        }

        if (!optional.isEmpty()) {
            if (!mandatory.isEmpty()) {
                sb.append(". ");
            }
            sb.append("Optional approvers: ");
            sb.append(optional.stream().map(Enum::name).collect(Collectors.joining(", ")));
            sb.append(" (at least one required if no mandatory approvers)");
        }

        return sb.toString();
    }
}
