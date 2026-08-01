package com.splitixy.dao;

import com.splitixy.db.DBconnection;
import com.splitixy.model.ExpenseGroup;

import java.sql.*;

public class GroupDAO {

    public boolean createGroup(ExpenseGroup group) {

        String insertGroup =
                "INSERT INTO expense_groups(group_name, created_by) VALUES (?, ?)";

        String insertMember =
                "INSERT INTO group_members(group_id, user_id) VALUES (?, ?)";

        try (Connection connection = DBconnection.getConnection()) {

            connection.setAutoCommit(false);

            PreparedStatement groupStatement =
                    connection.prepareStatement(insertGroup, Statement.RETURN_GENERATED_KEYS);

            groupStatement.setString(1, group.getGroupName());
            groupStatement.setInt(2, group.getCreatedBy());

            int rows = groupStatement.executeUpdate();

            if (rows == 0) {
                connection.rollback();
                return false;
            }

            ResultSet generatedKeys = groupStatement.getGeneratedKeys();

            if (generatedKeys.next()) {

                int groupId = generatedKeys.getInt(1);

                PreparedStatement memberStatement =
                        connection.prepareStatement(insertMember);

                memberStatement.setInt(1, groupId);
                memberStatement.setInt(2, group.getCreatedBy());

                memberStatement.executeUpdate();

                connection.commit();

                return true;
            }

            connection.rollback();

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return false;
    }
}