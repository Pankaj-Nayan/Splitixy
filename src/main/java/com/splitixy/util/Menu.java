package com.splitixy.util;

import com.splitixy.model.User;
import com.splitixy.model.ExpenseGroup;
import com.splitixy.service.GroupService;
import java.util.Scanner;
import com.splitixy.model.ExpenseGroup;
import java.util.List;
import com.splitixy.service.UserService;
import com.splitixy.model.User;

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
                case 3:
                    System.out.println("Add Expense Coming Soon...");
                    break;

                case 4:
                    System.out.println("View Balances Coming Soon...");
                    break;

                case 5:
                    return;

                default:
                    System.out.println("Invalid Choice!");
            }
        }
    }
}