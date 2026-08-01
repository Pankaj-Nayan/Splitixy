package com.splitixy.split;

import com.splitixy.model.User;
import java.util.List;
import java.util.Map;

public interface SplitStrategy {

    Map<Integer, Double> splitExpense(
            double amount,
            List<User> members
    );
}
