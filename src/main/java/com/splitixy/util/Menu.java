package com.splitixy.util;

import com.splitixy.model.User;
import com.splitixy.model.ExpenseGroup;
import com.splitixy.service.GroupService;
import java.util.Scanner;

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

                case 1:

                    System.out.print("Enter Group Name : ");
                    String groupName = scanner.nextLine();

                    ExpenseGroup group = new ExpenseGroup(
                            groupName,
                            loggedInUser.getUserId()
                    );

                    GroupService groupService = new GroupService();

                    if(groupService.createGroup(group)){
                        System.out.println("✅ Group Created Successfully!");
                    }else{
                        System.out.println("❌ Failed to Create Group!");
                    }

                    break;

                case 2:
                    System.out.println("View Groups Feature Coming Soon...");
                    break;

                case 3:
                    return;

                default:
                    System.out.println("Invalid Choice!");
            }
        }
    }
}