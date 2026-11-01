package com.splitixy.repository;

import com.splitixy.entity.Expense;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExpenseRepository extends JpaRepository<Expense, Integer> {

    List<Expense> findByGroupIdOrderByExpenseDateDescExpenseIdDesc(Integer groupId);
    List<Expense> findByGroupIdInOrderByExpenseDateDescExpenseIdDesc(
            List<Integer> groupIds);

}