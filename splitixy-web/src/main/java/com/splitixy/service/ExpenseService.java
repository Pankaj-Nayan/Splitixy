package com.splitixy.service;

import com.splitixy.entity.Expense;
import com.splitixy.entity.ExpenseSplit;
import com.splitixy.entity.GroupMember;
import com.splitixy.repository.ExpenseRepository;
import com.splitixy.repository.ExpenseSplitRepository;
import com.splitixy.repository.GroupMemberRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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


    // =========================
    // SAVE EXPENSE ONLY
    // =========================

    public Expense addExpense(Expense expense) {

        return expenseRepository.save(expense);
    }


    // =========================
    // EQUAL SPLIT
    // =========================

    @Transactional
    public void addEqualExpense(Expense expense) {

        // Get group members first
        List<GroupMember> members =
                groupMemberRepository.findByIdGroupId(
                        expense.getGroupId()
                );

        // Group must have members
        if (members.isEmpty()) {

            throw new IllegalArgumentException(
                    "Group has no members"
            );
        }

        // Calculate equal share
        double splitAmount =
                expense.getAmount() / members.size();


        // Save expense
        Expense savedExpense =
                expenseRepository.save(expense);


        // Create splits
        for (GroupMember member : members) {

            ExpenseSplit split =
                    new ExpenseSplit();

            split.setExpenseId(
                    savedExpense.getExpenseId()
            );

            split.setUserId(
                    member.getId().getUserId()
            );

            split.setAmountOwed(
                    splitAmount
            );

            expenseSplitRepository.save(
                    split
            );
        }
    }


    // =========================
    // CUSTOM SPLIT
    // =========================

    @Transactional
    public void addCustomExpense(
            Expense expense,
            Map<Integer, Double> splitAmounts) {


        // -------------------------
        // 1. Check split exists
        // -------------------------

        if (splitAmounts == null ||
                splitAmounts.isEmpty()) {

            throw new IllegalArgumentException(
                    "No split amounts provided"
            );
        }


        // -------------------------
        // 2. Validate amounts
        // -------------------------

        double total = 0;


        for (Double amount : splitAmounts.values()) {

            if (amount == null || amount < 0) {

                throw new IllegalArgumentException(
                        "Invalid split amount"
                );
            }

            total += amount;
        }


        // -------------------------
        // 3. Validate total
        // -------------------------

        if (Math.abs(
                total - expense.getAmount()
        ) > 0.01) {

            throw new IllegalArgumentException(
                    "Split amounts must equal expense amount"
            );
        }


        // -------------------------
        // 4. Get group members
        // -------------------------

        List<GroupMember> members =
                groupMemberRepository.findByIdGroupId(
                        expense.getGroupId()
                );


        if (members.isEmpty()) {

            throw new IllegalArgumentException(
                    "Group has no members"
            );
        }


        // -------------------------
        // 5. Make sure every selected
        //    user belongs to group
        // -------------------------

        for (Integer userId :
                splitAmounts.keySet()) {

            boolean isMember = false;


            for (GroupMember member :
                    members) {

                if (member.getId()
                        .getUserId()
                        .equals(userId)) {

                    isMember = true;
                    break;
                }
            }


            if (!isMember) {

                throw new IllegalArgumentException(
                        "User is not a member of this group"
                );
            }
        }


        // -------------------------
        // 6. ONLY AFTER ALL
        //    VALIDATION PASSES
        //    SAVE EXPENSE
        // -------------------------

        Expense savedExpense =
                expenseRepository.save(expense);


        // -------------------------
        // 7. Save custom splits
        // -------------------------

        for (Map.Entry<Integer, Double> entry :
                splitAmounts.entrySet()) {

            ExpenseSplit split =
                    new ExpenseSplit();

            split.setExpenseId(
                    savedExpense.getExpenseId()
            );

            split.setUserId(
                    entry.getKey()
            );

            split.setAmountOwed(
                    entry.getValue()
            );

            expenseSplitRepository.save(
                    split
            );
        }
    }


    // =========================
    // GET GROUP EXPENSES
    // =========================

    public List<Expense> getExpensesByGroup(
            Integer groupId) {

        return expenseRepository.findByGroupId(
                groupId
        );
    }


    // =========================
    // GET SINGLE EXPENSE
    // =========================

    public Expense getExpenseById(
            Integer expenseId) {

        return expenseRepository
                .findById(expenseId)
                .orElse(null);
    }
}