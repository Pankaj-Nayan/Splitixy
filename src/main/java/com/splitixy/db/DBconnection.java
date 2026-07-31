package com.splitixy.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DBconnection {

    private static final String URL = "jdbc:mysql://localhost:3306/splitixy";
    private static final String USERNAME = "root";
    private static final String PASSWORD = "Pankaj_3206";

    public static Connection getConnection() {

        try {
            Connection connection = DriverManager.getConnection(URL, USERNAME, PASSWORD);
            System.out.println("✅ Database Connected Successfully!");
            return connection;
        } catch (SQLException e) {
            System.out.println("❌ Connection Failed!");
            e.printStackTrace();
            return null;
        }

    }
}