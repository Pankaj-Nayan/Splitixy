package com.splitixy.util;

import com.splitixy.model.User;
import com.splitixy.model.ExpenseGroup;
import com.splitixy.service.GroupService;
import java.util.Scanner;
import com.splitixy.model.ExpenseGroup;
import com.splitixy.model.ExpenseSplit;
import com.splitixy.service.ExpenseSplitService;

import com.splitixy.split.SplitStrategy;
import com.splitixy.split.EqualSplitStrategy;
import com.splitixy.model.Balance;
import com.splitixy.service.BalanceService;
import java.util.Map;
import java.util.List;
import com.splitixy.service.UserService;
import com.splitixy.model.User;
import com.splitixy.model.Expense;
import com.splitixy.service.ExpenseService;

public class Menu {

    public static void dashboard(User loggedInUser) {

        Scanner scanner = new Scanner(System.in);

        while (true) {

            System.out.println("\n========== Welcome " + loggedInUser.getName() + " ==========");
            System.out.println("1. Create Group");
            System.out.println("2. View My Groups");
            System.out.println("3. Logout");
            System.out.print("Enter Choice : ");

            int choice = scanner.nextInt();
            scanner.nextLine();

            switch (choice) {

                case 1: {

                    System.out.print("Enter Group Name : ");
                    String groupName = scanner.nextLine();

                    ExpenseGroup group = new ExpenseGroup(
                            groupName,
                            loggedInUser.getUserId()
                    );

                    GroupService groupService = new GroupService();

                    if (groupService.createGroup(group)) {
                        System.out.println("✅ Group Created Successfully!");
                    } else {
                        System.out.println("❌ Failed to Create Group!");
                    }

                    break;
                }
                case 2: {

                    GroupService groupService = new GroupService();

                    List<ExpenseGroup> groups =
                            groupService.getGroupsByUser(loggedInUser.getUserId());

                    if (groups.isEmpty()) {
                        System.out.println("You are not part of any group.");
                        break;
                    }

                    System.out.println("\n========== My Groups ==========");

                    int index = 1;

                    for (ExpenseGroup group : groups) {
                        System.out.println(index + ". " + group.getGroupName());
                        index++;
                    }

                    System.out.print("\nSelect Group : ");
                    int groupChoice = scanner.nextInt();
                    scanner.nextLine();

                    if (groupChoice < 1 || groupChoice > groups.size()) {
                        System.out.println("❌ Invalid Group Selection!");
                        break;
                    }

                    ExpenseGroup selectedGroup = groups.get(groupChoice - 1);

                    groupMenu(selectedGroup, loggedInUser);

                    break;
                }

                case 3:
                    return;

                default:
                    System.out.println("Invalid Choice!");
            }
        }
    }
    public static void groupMenu(ExpenseGroup group, User loggedInUser) {

        Scanner scanner = new Scanner(System.in);

        while (true) {

            System.out.println("\n========== " + group.getGroupName() + " ==========");
            System.out.println("1. Add Member");
            System.out.println("2. View Members");
            System.out.println("3. Add Expense");
            System.out.println("4. View Balances");
            System.out.println("5. Back");

            System.out.print("Enter Choice : ");

            int choice = scanner.nextInt();
            scanner.nextLine();

            switch (choice) {

                case 1: {

                    System.out.print("Enter Member Email : ");
                    String email = scanner.nextLine();

                    UserService userService = new UserService();

                    User user = userService.getUserByEmail(email);

                    if (user == null) {
                        System.out.println("❌ User Not Found!");
                        break;
                    }

                    GroupService groupService = new GroupService();

                    if (groupService.addMember(group.getGroupId(), user.getUserId())) {
                        System.out.println("✅ Member Added Successfully!");
                    } else {
                        System.out.println("❌ Failed to Add Member!");
                    }

                    break;
                }

                case 2: {

                    GroupService groupService = new GroupService();

                    List<User> members = groupService.getGroupMembers(group.getGroupId());

                    if (members.isEmpty()) {
                        System.out.println("No members found.");
                        break;
                    }

                    System.out.println("\n========== Members ==========");

                    int index = 1;

                    for (User member : members) {
                        System.out.println(index + ". " + member.getName() +
                                " (" + member.getEmail() + ")");
                        index++;
                    }

                    break;
                }
                case 3: {

                    System.out.print("Enter Description : ");
                    String description = scanner.nextLine();

                    System.out.print("Enter Amount : ");
                    double amount = scanner.nextDouble();
                    scanner.nextLine();

                    GroupService groupService = new GroupService();

                    List<User> members = groupService.getGroupMembers(group.getGroupId());

                    System.out.println("\nWho Paid?");

                    int index = 1;

                    for (User member : members) {
                        System.out.println(index + ". " + member.getName());
                        index++;
                    }

                    System.out.print("Choose : ");
                    int payerChoice = scanner.nextInt();
                    scanner.nextLine();

                    if (payerChoice < 1 || payerChoice > members.size()) {
                        System.out.println("❌ Invalid Choice!");
                        break;
                    }

                    User payer = members.get(payerChoice - 1);

                    Expense expense = new Expense(
                            group.getGroupId(),
                            payer.getUserId(),
                            amount,
                            description
                    );

                    ExpenseService expenseService = new ExpenseService();

                    int expenseId = expenseService.addExpense(expense);

                    if (expenseId == -1) {
                        System.out.println("❌ Failed to Add Expense!");
                        break;
                    }

                    System.out.println("\nChoose Split Type");
                    System.out.println("1. Equal Split");
                    System.out.println("2. Custom Split");

                    int splitChoice = scanner.nextInt();
                    scanner.nextLine();

                    SplitStrategy strategy;

                    if (splitChoice == 1) {
                        strategy = new EqualSplitStrategy();
                    } else {
                        System.out.println("Custom Split Coming Soon...");
                        break;
                    }

                    Map<Integer, Double> splitMap =
                            strategy.splitExpense(amount, members);

                    ExpenseSplitService splitService =
                            new ExpenseSplitService();

                    for (Map.Entry<Integer, Double> entry : splitMap.entrySet()) {

                        ExpenseSplit split = new ExpenseSplit(
                                expenseId,
                                entry.getKey(),
                                entry.getValue()
                        );

                        splitService.addSplit(split);
                    }

                    System.out.println("✅ Expense Added & Split Successfully!");

                    break;
                }

                case 4: {

                    BalanceService balanceService = new BalanceService();

                    List<Balance> balances =
                            balanceService.getBalances(group.getGroupId());

                    System.out.println("\n========== Balances ==========");

                    for (Balance balance : balances) {

                        if (balance.getBalance() > 0) {
                            System.out.println(balance.getUserName()
                                    + " should receive ₹"
                                    + balance.getBalance());
                        }
                        else if (balance.getBalance() < 0) {
                            System.out.println(balance.getUserName()
                                    + " owes ₹"
                                    + Math.abs(balance.getBalance()));
                        }
                        else {
                            System.out.println(balance.getUserName()
                                    + " is settled.");
                        }
                    }

                    break;
                }
                case 5:
                    return;

                default:
                    System.out.println("Invalid Choice!");
            }
        }
    }
}