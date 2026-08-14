package com.splitixy.service;

import com.splitixy.entity.Expense;
import com.splitixy.entity.ExpenseSplit;
import com.splitixy.entity.GroupMember;
import com.splitixy.repository.ExpenseRepository;
import com.splitixy.repository.ExpenseSplitRepository;
import com.splitixy.repository.GroupMemberRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ExpenseService {

    @Autowired
    private ExpenseRepository expenseRepository;

    @Autowired
    private ExpenseSplitRepository expenseSplitRepository;

    @Autowired
    private GroupMemberRepository groupMemberRepository;


    public Expense addExpense(Expense expense) {

        // Save expense first
        Expense savedExpense = expenseRepository.save(expense);

        // Get all members of the group
        List<GroupMember> members =
                groupMemberRepository.findByIdGroupId(
                        expense.getGroupId()
                );

        // Calculate equal share
        double splitAmount =
                expense.getAmount() / members.size();

        // Create split for every member
        for (GroupMember member : members) {

            ExpenseSplit split = new ExpenseSplit();

            split.setExpenseId(savedExpense.getExpenseId());

            split.setUserId(member.getId().getUserId());

            split.setAmountOwed(splitAmount);

            expenseSplitRepository.save(split);
        }

        return savedExpense;
    }


    public List<Expense> getExpensesByGroup(Integer groupId) {

        return expenseRepository.findByGroupId(groupId);
    }


    public Expense getExpenseById(Integer expenseId) {

        return expenseRepository
                .findById(expenseId)
                .orElse(null);
    }
}