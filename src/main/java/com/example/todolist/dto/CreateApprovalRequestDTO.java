package com.example.todolist.dto;

import com.example.todolist.model.RequestOperation;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreateApprovalRequestDTO {
    private String targetItemType;  // "TODO", "INVOICE"
    private Long targetItemId;  // Nullable for CREATE operations
    private RequestOperation operation;  // CREATE, UPDATE, DELETE
    private Map<String, Object> requestedData;  // The data for the operation
}
