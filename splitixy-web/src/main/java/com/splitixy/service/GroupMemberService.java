package com.splitixy.service;
import java.util.List;
import com.splitixy.entity.GroupMember;
import com.splitixy.repository.GroupMemberRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class GroupMemberService {

    @Autowired
    private GroupMemberRepository groupMemberRepository;

    public void addMember(GroupMember groupMember) {
        groupMemberRepository.save(groupMember);
    }
    public List<GroupMember> getMembers(Integer groupId) {
        return groupMemberRepository.findByIdGroupId(groupId);
    }
}