package com.splitixy.repository;

import com.splitixy.entity.GroupMember;
import com.splitixy.entity.GroupMemberId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GroupMemberRepository
        extends JpaRepository<GroupMember, GroupMemberId> {
    List<GroupMember> findByIdGroupId(Integer groupId);
    List<GroupMember> findByIdUserId(Integer userId);

}