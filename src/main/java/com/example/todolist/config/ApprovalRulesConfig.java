package com.example.todolist.config;

import com.example.todolist.model.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

import static com.example.todolist.model.RoleRequirement.mandatory;
import static com.example.todolist.model.RoleRequirement.optional;

@Configuration
public class ApprovalRulesConfig {

    @Bean
    public List<ApprovalRule> approvalRules() {
        return List.of(
            // ============ TODO RULES ============

            // Todo HIGH level CREATE - Admin AND Manager both mandatory
            new ApprovalRule(
                "TODO",
                RequestOperation.CREATE,
                "level=HIGH",
                Map.of(
                    Role.ADMIN, mandatory(),
                    Role.MANAGER, mandatory()
                ),
                100  // High priority (most specific)
            ),

            // Todo MEDIUM level CREATE - Manager mandatory
            new ApprovalRule(
                "TODO",
                RequestOperation.CREATE,
                "level=MEDIUM",
                Map.of(Role.MANAGER, mandatory()),
                50  // Medium priority
            ),

            // Todo LOW level CREATE - No rule (immediate execution)

            // Todo HIGH level UPDATE - Admin AND Manager both mandatory
            new ApprovalRule(
                "TODO",
                RequestOperation.UPDATE,
                "level=HIGH",
                Map.of(
                    Role.ADMIN, mandatory(),
                    Role.MANAGER, mandatory()
                ),
                100
            ),

            // Todo MEDIUM level UPDATE - Manager mandatory
            new ApprovalRule(
                "TODO",
                RequestOperation.UPDATE,
                "level=MEDIUM",
                Map.of(Role.MANAGER, mandatory()),
                50
            ),

            // Todo HIGH level DELETE - Admin AND Manager both mandatory
            new ApprovalRule(
                "TODO",
                RequestOperation.DELETE,
                "level=HIGH",
                Map.of(
                    Role.ADMIN, mandatory(),
                    Role.MANAGER, mandatory()
                ),
                100
            ),

            // Todo MEDIUM level DELETE - Manager mandatory
            new ApprovalRule(
                "TODO",
                RequestOperation.DELETE,
                "level=MEDIUM",
                Map.of(Role.MANAGER, mandatory()),
                50
            ),

            // ============ INVOICE RULES ============

            // Invoice CREATE - Manager mandatory
            new ApprovalRule(
                "INVOICE",
                RequestOperation.CREATE,
                null,  // No condition (applies to all invoices)
                Map.of(Role.MANAGER, mandatory()),
                0
            ),

            // Invoice UPDATE - Manager mandatory
            new ApprovalRule(
                "INVOICE",
                RequestOperation.UPDATE,
                null,
                Map.of(Role.MANAGER, mandatory()),
                0
            ),

            // Invoice DELETE - Admin OR Manager (both optional, need at least one)
            new ApprovalRule(
                "INVOICE",
                RequestOperation.DELETE,
                null,
                Map.of(
                    Role.ADMIN, optional(),
                    Role.MANAGER, optional()
                    // Logic: Need at least 1 approval from these optional roles
                ),
                0
            )

            // ============ FUTURE RULES ============
            // Easy to add new item types here!
            // Example for DAY_OFF_REQUEST:
            //
            // new ApprovalRule(
            //     "DAY_OFF_REQUEST",
            //     RequestOperation.CREATE,
            //     null,
            //     Map.of(
            //         Role.MANAGER, mandatory(),
            //         Role.HR, optional()
            //     ),
            //     0
            // )
        );
    }
}
