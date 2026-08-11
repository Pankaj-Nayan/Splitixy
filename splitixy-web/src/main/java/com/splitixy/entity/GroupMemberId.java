package com.splitixy.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;

@Embeddable
public class GroupMemberId implements Serializable {

    @Column(name = "group_id")
    private Integer groupId;

    @Column(name = "user_id")
    private Integer userId;

    public GroupMemberId() {
    }

    public GroupMemberId(Integer groupId, Integer userId) {
        this.groupId = groupId;
        this.userId = userId;
    }

    public Integer getGroupId() {
        return groupId;
    }

    public void setGroupId(Integer groupId) {
        this.groupId = groupId;
    }

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }
}