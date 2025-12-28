package com.example.todolist.dto;

import com.example.todolist.model.ApprovalRequest;
import com.example.todolist.model.ApprovalStatus;
import com.example.todolist.model.RequestOperation;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalRequestResponseDTO {
    private Long id;
    private String targetItemType;
    private Long targetItemId;
    private RequestOperation operation;
    private Map<String, Object> requestedData;
    private ApprovalStatus status;
    private String requesterUsername;
    private Long requesterId;
    private Instant createdAt;
    private Instant updatedAt;

    public static ApprovalRequestResponseDTO fromEntity(ApprovalRequest request, ObjectMapper objectMapper) {
        ApprovalRequestResponseDTO dto = new ApprovalRequestResponseDTO();
        dto.setId(request.getId());
        dto.setTargetItemType(request.getTargetItemType());
        dto.setTargetItemId(request.getTargetItemId());
        dto.setOperation(request.getOperation());

        // Deserialize JSONB data
        try {
            dto.setRequestedData(objectMapper.readValue(request.getRequestedData(), Map.class));
        } catch (JsonProcessingException e) {
            dto.setRequestedData(Map.of("error", "Failed to parse data"));
        }

        dto.setStatus(request.getStatus());
        dto.setRequesterUsername(request.getRequester().getUsername());
        dto.setRequesterId(request.getRequester().getId());
        dto.setCreatedAt(request.getCreatedAt());
        dto.setUpdatedAt(request.getUpdatedAt());

        return dto;
    }
}
