package com.splitixy.service;

import com.splitixy.entity.ExpenseGroup;
import com.splitixy.entity.GroupMember;
import com.splitixy.entity.GroupMemberId;
import com.splitixy.repository.ExpenseGroupRepository;
import com.splitixy.repository.GroupMemberRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ExpenseGroupService {

    @Autowired
    private ExpenseGroupRepository expenseGroupRepository;

    @Autowired
    private GroupMemberRepository groupMemberRepository;

    @Transactional
    public ExpenseGroup createGroup(ExpenseGroup expenseGroup, Integer creatorId) {
        expenseGroup.setCreatedBy(creatorId);
        ExpenseGroup savedGroup = expenseGroupRepository.save(expenseGroup);
        GroupMember membership = new GroupMember();
        membership.setId(new GroupMemberId(savedGroup.getGroupId(), creatorId));
        groupMemberRepository.save(membership);
        return savedGroup;
    }

    public List<ExpenseGroup> getMyGroups(Integer userId) {
        Map<Integer, ExpenseGroup> groups = new LinkedHashMap<>();
        for (ExpenseGroup group : expenseGroupRepository.findByCreatedBy(userId)) {
            groups.put(group.getGroupId(), group);
        }

        Set<Integer> memberGroupIds = groupMemberRepository.findByIdUserId(userId)
                .stream()
                .map(membership -> membership.getId().getGroupId())
                .collect(java.util.stream.Collectors.toSet());
        for (ExpenseGroup group : expenseGroupRepository.findAllById(memberGroupIds)) {
            groups.putIfAbsent(group.getGroupId(), group);
        }
        return new ArrayList<>(groups.values());
    }

    public ExpenseGroup getGroup(Integer groupId) {
        return expenseGroupRepository.findById(groupId).orElse(null);
    }

}