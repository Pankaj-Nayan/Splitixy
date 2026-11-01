package com.splitixy.service;
import java.util.List;
import com.splitixy.entity.GroupMember;
import com.splitixy.entity.GroupMemberId;
import com.splitixy.repository.GroupMemberRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class GroupMemberService {

    @Autowired
    private GroupMemberRepository groupMemberRepository;

    public boolean addMember(GroupMember groupMember) {
        if (groupMemberRepository.existsById(groupMember.getId())) {
            return false;
        }
        groupMemberRepository.save(groupMember);
        return true;
    }

    public List<GroupMember> getMembers(Integer groupId) {
        return groupMemberRepository.findByIdGroupId(groupId);
    }

    public boolean isMember(Integer groupId, Integer userId) {
        return groupMemberRepository.existsById(new GroupMemberId(groupId, userId));
    }

    public List<GroupMember> getMemberships(Integer userId) {
        return groupMemberRepository.findByIdUserId(userId);
    }
}