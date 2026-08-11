package com.splitixy.service;

import com.splitixy.entity.ExpenseGroup;
import com.splitixy.repository.ExpenseGroupRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ExpenseGroupService {

    @Autowired
    private ExpenseGroupRepository expenseGroupRepository;

    public void createGroup(ExpenseGroup expenseGroup) {
        expenseGroupRepository.save(expenseGroup);
    }
    public List<ExpenseGroup> getMyGroups(Integer userId) {
        return expenseGroupRepository.findByCreatedBy(userId);
    }

}