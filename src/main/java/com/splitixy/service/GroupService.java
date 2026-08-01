package com.splitixy.service;
import com.splitixy.model.User;
import java.util.ArrayList;
import java.util.List;
import com.splitixy.dao.GroupDAO;
import com.splitixy.model.ExpenseGroup;
import java.util.List;
public class GroupService {

    private GroupDAO groupDAO = new GroupDAO();

    public boolean createGroup(ExpenseGroup group) {
        return groupDAO.createGroup(group);
    }
    public List<ExpenseGroup> getGroupsByUser(int userId) {
        return groupDAO.getGroupsByUser(userId);
    }
    public boolean addMember(int groupId, int userId) {
        return groupDAO.addMember(groupId, userId);
    }
    public List<User> getGroupMembers(int groupId) {
        return groupDAO.getGroupMembers(groupId);
    }
}