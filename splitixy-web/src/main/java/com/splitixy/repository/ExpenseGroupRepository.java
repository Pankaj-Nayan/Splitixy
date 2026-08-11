package com.splitixy.repository;

import com.splitixy.entity.ExpenseGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExpenseGroupRepository extends JpaRepository<ExpenseGroup, Integer> {

    List<ExpenseGroup> findByCreatedBy(Integer createdBy);

}