package com.example.todolist.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SubmitApprovalDTO {
    private Boolean decision;  // true = approve, false = reject
    private String comment;  // Optional comment explaining the decision
}
