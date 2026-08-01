package com.splitixy.dao;

import com.splitixy.db.DBConnection;
import com.splitixy.model.Balance;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class BalanceDAO {

    public List<Balance> getBalances(int groupId) {

        List<Balance> balances = new ArrayList<>();

        String query = """
                SELECT
                    u.name,
                    COALESCE(paid.total_paid,0) -
                    COALESCE(owed.total_owed,0) AS balance
                FROM users u

                INNER JOIN group_members gm
                ON u.user_id = gm.user_id

                LEFT JOIN (
                    SELECT paid_by,
                           SUM(amount) AS total_paid
                    FROM expenses
                    WHERE group_id = ?
                    GROUP BY paid_by
                ) paid
                ON u.user_id = paid.paid_by

                LEFT JOIN (
                    SELECT es.user_id,
                           SUM(es.amount_owed) AS total_owed
                    FROM expense_splits es
                    INNER JOIN expenses e
                    ON es.expense_id = e.expense_id
                    WHERE e.group_id = ?
                    GROUP BY es.user_id
                ) owed
                ON u.user_id = owed.user_id

                WHERE gm.group_id = ?;
                """;

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(query)) {

            ps.setInt(1, groupId);
            ps.setInt(2, groupId);
            ps.setInt(3, groupId);

            ResultSet rs = ps.executeQuery();

            while (rs.next()) {

                balances.add(
                        new Balance(
                                rs.getString("name"),
                                rs.getDouble("balance")
                        )
                );
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return balances;
    }
}