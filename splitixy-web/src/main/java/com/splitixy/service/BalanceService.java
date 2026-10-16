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

        List<GroupMember> members =
                groupMemberService.getMembers(groupId);

        for (GroupMember member : members) {

            Integer userId =
                    member.getId().getUserId();

            balances.put(userId, 0.0);
        }


        List<Expense> expenses =
                expenseService.getExpensesByGroup(groupId);


        for (Expense expense : expenses) {

            Integer payerId =
                    expense.getPaidBy();

            balances.put(
                    payerId,
                    balances.getOrDefault(payerId, 0.0)
                            + expense.getAmount()
            );


            List<ExpenseSplit> splits =
                    expenseSplitRepository
                            .findByExpenseId(
                                    expense.getExpenseId()
                            );


            for (ExpenseSplit split : splits) {

                Integer userId =
                        split.getUserId();

                balances.put(
                        userId,
                        balances.getOrDefault(userId, 0.0)
                                - split.getAmountOwed()
                );
            }
        }

        return balances;
    }


    public List<Debt> calculateDebts(Integer groupId) {

        Map<Integer, Double> balances =
                calculateBalances(groupId);


        List<Integer> creditors = new ArrayList<>();
        List<Integer> debtors = new ArrayList<>();


        for (Map.Entry<Integer, Double> entry
                : balances.entrySet()) {

            if (entry.getValue() > 0.01) {

                creditors.add(entry.getKey());

            } else if (entry.getValue() < -0.01) {

                debtors.add(entry.getKey());
            }
        }


        List<Debt> debts = new ArrayList<>();

        int i = 0;
        int j = 0;


        while (i < debtors.size()
                && j < creditors.size()) {

            Integer debtorId = debtors.get(i);
            Integer creditorId = creditors.get(j);


            double debt =
                    -balances.get(debtorId);

            double credit =
                    balances.get(creditorId);


            double amount =
                    Math.min(debt, credit);


            User debtor =
                    userService.getUserById(debtorId);

            User creditor =
                    userService.getUserById(creditorId);


            if (debtor != null && creditor != null) {

                debts.add(
                        new Debt(
                                debtor.getFullName(),
                                creditor.getFullName(),
                                amount
                        )
                );
            }


            balances.put(
                    debtorId,
                    balances.get(debtorId) + amount
            );

            balances.put(
                    creditorId,
                    balances.get(creditorId) - amount
            );


            if (Math.abs(balances.get(debtorId)) < 0.01) {
                i++;
            }

            if (Math.abs(balances.get(creditorId)) < 0.01) {
                j++;
            }
        }


        return debts;
    }


    public static class Debt {

        private String debtor;
        private String creditor;
        private double amount;


        public Debt(
                String debtor,
                String creditor,
                double amount) {

            this.debtor = debtor;
            this.creditor = creditor;
            this.amount = amount;
        }


        public String getDebtor() {
            return debtor;
        }


        public String getCreditor() {
            return creditor;
        }


        public double getAmount() {
            return amount;
        }
    }
}