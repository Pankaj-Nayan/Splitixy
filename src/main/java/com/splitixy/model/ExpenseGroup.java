
package com.splitixy.model;

public class ExpenseGroup {

    private int groupId;
    private String groupName;
    private int createdBy;

    public ExpenseGroup() {
    }

    public ExpenseGroup(String groupName, int createdBy) {
        this.groupName = groupName;
        this.createdBy = createdBy;
    }

    public int getGroupId() {
        return groupId;
    }

    public void setGroupId(int groupId) {
        this.groupId = groupId;
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public int getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(int createdBy) {
        this.createdBy = createdBy;
    }
}