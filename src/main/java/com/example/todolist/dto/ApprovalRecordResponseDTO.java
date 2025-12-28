package com.example.todolist.dto;

import com.example.todolist.model.ApprovalRecord;
import com.example.todolist.model.Role;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalRecordResponseDTO {
    private Long id;
    private Long approvalRequestId;
    private Long approverId;
    private String approverUsername;
    private Role approverRole;
    private Boolean decision;  // true = approved, false = rejected
    private String comment;
    private Instant createdAt;

    public static ApprovalRecordResponseDTO fromEntity(ApprovalRecord record) {
        ApprovalRecordResponseDTO dto = new ApprovalRecordResponseDTO();
        dto.setId(record.getId());
        dto.setApprovalRequestId(record.getApprovalRequest().getId());
        dto.setApproverId(record.getApprover().getId());
        dto.setApproverUsername(record.getApprover().getUsername());
        dto.setApproverRole(record.getApprover().getRole());
        dto.setDecision(record.getDecision());
        dto.setComment(record.getComment());
        dto.setCreatedAt(record.getCreatedAt());
        return dto;
    }
}
