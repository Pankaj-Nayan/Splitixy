package com.splitixy.dao;

import com.splitixy.db.DBconnection;
import com.splitixy.model.Expense;

import java.sql.*;

public class ExpenseDAO {
    public int addExpense(Expense expense) {

        String query = """
            INSERT INTO expenses(group_id, paid_by, amount, description)
            VALUES (?, ?, ?, ?)
            """;

        try (Connection connection = DBconnection.getConnection();
             PreparedStatement preparedStatement =
                     connection.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {

            preparedStatement.setInt(1, expense.getGroupId());
            preparedStatement.setInt(2, expense.getPaidBy());
            preparedStatement.setDouble(3, expense.getAmount());
            preparedStatement.setString(4, expense.getDescription());

            int rows = preparedStatement.executeUpdate();

            if (rows > 0) {

                ResultSet resultSet = preparedStatement.getGeneratedKeys();

                if (resultSet.next()) {
                    return resultSet.getInt(1);
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return -1;
    }

}