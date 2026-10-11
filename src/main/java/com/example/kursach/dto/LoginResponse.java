package com.example.kursach.dto;

public class LoginResponse {
    private String token;
    private Long userId;
    private String tabNumber;
    private String fullName;
    private String roleCode;
    private String roleName;
    private boolean requirePasswordChange;
    private String passwordChangeReason;

    public LoginResponse(String token, Long userId, String tabNumber, String fullName,
                         String roleCode, String roleName, boolean requirePasswordChange,
                         String passwordChangeReason) {
        this.token = token;
        this.userId = userId;
        this.tabNumber = tabNumber;
        this.fullName = fullName;
        this.roleCode = roleCode;
        this.roleName = roleName;
        this.requirePasswordChange = requirePasswordChange;
        this.passwordChangeReason = passwordChangeReason;
    }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getTabNumber() { return tabNumber; }
    public void setTabNumber(String tabNumber) { this.tabNumber = tabNumber; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getRoleCode() { return roleCode; }
    public void setRoleCode(String roleCode) { this.roleCode = roleCode; }
    public String getRoleName() { return roleName; }
    public void setRoleName(String roleName) { this.roleName = roleName; }
    public boolean isRequirePasswordChange() { return requirePasswordChange; }
    public void setRequirePasswordChange(boolean requirePasswordChange) { this.requirePasswordChange = requirePasswordChange; }
    public String getPasswordChangeReason() { return passwordChangeReason; }
    public void setPasswordChangeReason(String passwordChangeReason) { this.passwordChangeReason = passwordChangeReason; }
}