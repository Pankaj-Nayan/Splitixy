package com.splitixy.service;

import com.splitixy.entity.Expense;
import com.splitixy.entity.ExpenseSplit;
import com.splitixy.entity.GroupMember;
import com.splitixy.entity.User;
import com.splitixy.repository.ExpenseSplitRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class BalanceService {

    @Autowired
    private ExpenseService expenseService;

    @Autowired
    private ExpenseSplitRepository expenseSplitRepository;

    @Autowired
    private GroupMemberService groupMemberService;

    @Autowired
    private UserService userService;


    public Map<Integer, Double> calculateBalances(Integer groupId) {

        Map<Integer, Double> balances = new HashMap<>();

        // Get group members
        List<GroupMember> members =
                groupMemberService.getMembers(groupId);

        // Initialize everyone with 0
        for (GroupMember member : members) {

            Integer userId =
                    member.getId().getUserId();

            balances.put(userId, 0.0);
        }


        // Get all expenses
        List<Expense> expenses =
                expenseService.getExpensesByGroup(groupId);


        for (Expense expense : expenses) {

            Integer payerId =
                    expense.getPaidBy();

            double expenseAmount =
                    expense.getAmount();


            // Payer paid the whole expense
            balances.put(
                    payerId,
                    balances.getOrDefault(payerId, 0.0)
                            + expenseAmount
            );


            // Subtract what every person owes
            List<ExpenseSplit> splits =
                    expenseSplitRepository
                            .findByExpenseId(
                                    expense.getExpenseId()
                            );


            for (ExpenseSplit split : splits) {

                Integer userId =
                        split.getUserId();

                double owed =
                        split.getAmountOwed();

                balances.put(
                        userId,
                        balances.getOrDefault(userId, 0.0)
                                - owed
                );
            }
        }

        return balances;
    }
}