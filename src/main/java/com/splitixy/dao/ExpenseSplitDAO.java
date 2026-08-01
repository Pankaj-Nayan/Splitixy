package com.splitixy.dao;

import com.splitixy.db.DBconnection;
import com.splitixy.model.ExpenseSplit;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class ExpenseSplitDAO {

    public boolean addSplit(ExpenseSplit split) {

        String query = """
                INSERT INTO expense_splits(expense_id, user_id, amount_owed)
                VALUES (?, ?, ?)
                """;

        try (Connection connection = DBconnection.getConnection();
             PreparedStatement preparedStatement =
                     connection.prepareStatement(query)) {

            preparedStatement.setInt(1, split.getExpenseId());
            preparedStatement.setInt(2, split.getUserId());
            preparedStatement.setDouble(3, split.getAmountOwed());

            return preparedStatement.executeUpdate() > 0;

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return false;
    }
}