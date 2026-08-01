package com.splitixy.split;

import com.splitixy.model.User;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EqualSplitStrategy implements SplitStrategy {

    @Override
    public Map<Integer, Double> splitExpense(double amount, List<User> members) {

        Map<Integer, Double> splitMap = new HashMap<>();

        double share = amount / members.size();

        for (User member : members) {
            splitMap.put(member.getUserId(), share);
        }

        return splitMap;
    }
}