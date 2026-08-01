
package com.splitixy.service;

import com.splitixy.dao.UserDAO;
import com.splitixy.model.User;

public class UserService {

    private UserDAO userDAO = new UserDAO();

    public boolean registerUser(User user) {
        return userDAO.insertUser(user);
    }

    public User loginUser(String email, String password) {
        return userDAO.loginUser(email, password);
    }
    public User getUserByEmail(String email) {
        return userDAO.getUserByEmail(email);
    }

}