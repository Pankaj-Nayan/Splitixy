package com.splitixy.entity;

import jakarta.persistence.*;
import java.sql.Timestamp;

@Entity
@Table(name = "group_members")
public class GroupMember {

    @EmbeddedId
    private GroupMemberId id;

    @Column(name = "joined_at")
    private Timestamp joinedAt;

    public GroupMember() {
    }

    public GroupMemberId getId() {
        return id;
    }

    public void setId(GroupMemberId id) {
        this.id = id;
    }

    public Timestamp getJoinedAt() {
        return joinedAt;
    }

    public void setJoinedAt(Timestamp joinedAt) {
        this.joinedAt = joinedAt;
    }
}