package com.splitixy.util;

import java.util.Scanner;

public class Menu {

    public static void userDashboard() {

        Scanner scanner = new Scanner(System.in);

        while (true) {

            System.out.println("\n========== Splitixy ==========");
            System.out.println("1. Create Group");
            System.out.println("2. Join Group");
            System.out.println("3. My Groups");
            System.out.println("4. Logout");
            System.out.print("Enter Choice: ");

            int choice = scanner.nextInt();

            switch (choice) {
                case 1:
                    System.out.println("Create Group");
                    break;

                case 2:
                    System.out.println("Join Group");
                    break;

                case 3:
                    System.out.println("My Groups");
                    break;

                case 4:
                    return;

                default:
                    System.out.println("Invalid Choice!");
            }
        }
    }
}