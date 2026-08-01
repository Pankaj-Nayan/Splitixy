package com.splitixy.service;

import com.splitixy.dao.GroupDAO;
import com.splitixy.model.ExpenseGroup;

public class GroupService {

    private GroupDAO groupDAO = new GroupDAO();

    public boolean createGroup(ExpenseGroup group) {
        return groupDAO.createGroup(group);
    }
}