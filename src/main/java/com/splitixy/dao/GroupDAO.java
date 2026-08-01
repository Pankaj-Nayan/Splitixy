package com.splitixy.dao;

import com.splitixy.db.DBconnection;
import com.splitixy.model.ExpenseGroup;
import java.util.ArrayList;
import java.util.List;
import java.sql.*;
import com.splitixy.model.User;
import java.util.ArrayList;
import java.util.List;

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
    public List<ExpenseGroup> getGroupsByUser(int userId) {

        List<ExpenseGroup> groups = new ArrayList<>();

        String query = """
            SELECT eg.group_id, eg.group_name, eg.created_by
            FROM expense_groups eg
            INNER JOIN group_members gm
            ON eg.group_id = gm.group_id
            WHERE gm.user_id = ?
            """;

        try (Connection connection = DBconnection.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(query)) {

            preparedStatement.setInt(1, userId);

            ResultSet resultSet = preparedStatement.executeQuery();

            while (resultSet.next()) {

                ExpenseGroup group = new ExpenseGroup();

                group.setGroupId(resultSet.getInt("group_id"));
                group.setGroupName(resultSet.getString("group_name"));
                group.setCreatedBy(resultSet.getInt("created_by"));

                groups.add(group);
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return groups;
    }
    public boolean addMember(int groupId, int userId) {

        String query = "INSERT INTO group_members(group_id, user_id) VALUES(?, ?)";

        try (Connection connection = DBconnection.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(query)) {

            preparedStatement.setInt(1, groupId);
            preparedStatement.setInt(2, userId);

            return preparedStatement.executeUpdate() > 0;

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return false;
    }
    public List<User> getGroupMembers(int groupId) {

        List<User> members = new ArrayList<>();

        String query = """
            SELECT u.user_id, u.name, u.email
            FROM users u
            INNER JOIN group_members gm
            ON u.user_id = gm.user_id
            WHERE gm.group_id = ?
            """;

        try (Connection connection = DBconnection.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(query)) {

            preparedStatement.setInt(1, groupId);

            ResultSet resultSet = preparedStatement.executeQuery();

            while (resultSet.next()) {

                User user = new User();

                user.setUserId(resultSet.getInt("user_id"));
                user.setName(resultSet.getString("name"));
                user.setEmail(resultSet.getString("email"));

                members.add(user);
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return members;
    }
}