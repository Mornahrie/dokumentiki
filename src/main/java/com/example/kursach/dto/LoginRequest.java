package com.example.kursach.dto;

public class LoginRequest {
    private String tabNumber;
    private String password;

    public String getTabNumber() { return tabNumber; }
    public void setTabNumber(String tabNumber) { this.tabNumber = tabNumber; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}