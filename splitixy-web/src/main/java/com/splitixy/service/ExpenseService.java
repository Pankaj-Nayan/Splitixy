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
import java.util.Map;

@Service
public class ExpenseService {

    @Autowired
    private ExpenseRepository expenseRepository;

    @Autowired
    private ExpenseSplitRepository expenseSplitRepository;

    @Autowired
    private GroupMemberRepository groupMemberRepository;


    // Save expense
    public Expense addExpense(Expense expense) {

        return expenseRepository.save(expense);
    }


    // Equal split
    public void createEqualSplits(Expense expense) {

        List<GroupMember> members =
                groupMemberRepository.findByIdGroupId(
                        expense.getGroupId()
                );

        if (members.isEmpty()) {
            return;
        }

        double splitAmount =
                expense.getAmount() / members.size();

        for (GroupMember member : members) {

            ExpenseSplit split = new ExpenseSplit();

            split.setExpenseId(expense.getExpenseId());

            split.setUserId(
                    member.getId().getUserId()
            );

            split.setAmountOwed(splitAmount);

            expenseSplitRepository.save(split);
        }
    }


    // Custom split
    public void createCustomSplits(
            Expense expense,
            Map<Integer, Double> splitAmounts) {

        for (Map.Entry<Integer, Double> entry
                : splitAmounts.entrySet()) {

            if (entry.getValue() == null ||
                    entry.getValue() < 0) {
                continue;
            }

            ExpenseSplit split = new ExpenseSplit();

            split.setExpenseId(expense.getExpenseId());

            split.setUserId(entry.getKey());

            split.setAmountOwed(entry.getValue());

            expenseSplitRepository.save(split);
        }
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