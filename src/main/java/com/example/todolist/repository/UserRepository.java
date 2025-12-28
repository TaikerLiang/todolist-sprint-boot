package com.example.todolist.repository;

import com.example.todolist.model.Role;
import com.example.todolist.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);

    List<User> findByRoleIn(List<Role> roles);
}
