
package com.splitixy;

import com.splitixy.model.User;
import com.splitixy.service.UserService;

import java.util.Scanner;

public class Main {

    public static void main(String[] args) {

        Scanner scanner = new Scanner(System.in);
        UserService userService = new UserService();

        while (true) {

            System.out.println("\n========== Splitixy ==========");
            System.out.println("1. Register");
            System.out.println("2. Login");
            System.out.println("3. Exit");
            System.out.print("Enter Choice : ");

            int choice = scanner.nextInt();
            scanner.nextLine();

            switch (choice) {

                case 1:

                    System.out.print("Enter Name : ");
                    String name = scanner.nextLine();

                    System.out.print("Enter Email : ");
                    String email = scanner.nextLine();

                    System.out.print("Enter Password : ");
                    String password = scanner.nextLine();

                    User user = new User(name, email, password);

                    if (userService.registerUser(user))
                        System.out.println("Registration Successful!");
                    else
                        System.out.println("Registration Failed!");

                    break;

                case 2:

                    System.out.print("Enter Email : ");
                    String login_email = scanner.nextLine();

                    System.out.print("Enter Password : ");
                    String login_password = scanner.nextLine();

                    if (userService.loginUser(login_email, login_password)) {
                        System.out.println("✅ Login Successful!");
                    } else {
                        System.out.println("❌ Invalid Email or Password!");
                    }
                    break;

                case 3:

                    System.out.println("Thank you for using Splitixy!");
                    scanner.close();
                    System.exit(0);

                default:

                    System.out.println("Invalid Choice!");

            }
        }
    }
}