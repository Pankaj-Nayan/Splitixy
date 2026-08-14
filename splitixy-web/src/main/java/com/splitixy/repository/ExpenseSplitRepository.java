package com.splitixy.repository;

import com.splitixy.entity.ExpenseSplit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExpenseSplitRepository extends JpaRepository<ExpenseSplit, Integer> {

    List<ExpenseSplit> findByExpenseId(Integer expenseId);

    List<ExpenseSplit> findByUserId(Integer userId);
}