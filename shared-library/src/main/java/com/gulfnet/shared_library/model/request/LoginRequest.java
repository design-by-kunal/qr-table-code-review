package com.gulfnet.shared_library.model.request;

import com.gulfnet.shared_library.enums.AppType;
import lombok.Data;

import java.util.UUID;

@Data
public class LoginRequest {

    private String email;
    private String password;
    /**
     * Optional login strategy from frontend (e.g. "local").
     * Currently used for compatibility with clients; server-side auth is password-based.
     */
    private String strategy;
    private String userCode;
    private UUID kdsId;

    /**
     * Application type from which the user is trying to login
     * (e.g. HQADMIN, MANAGER, CASHIER, WAITER, KDS).
     */
    private AppType appType;

    public AppType getAppType() {
        return appType;
    }

    public void setAppType(AppType appType) {
        this.appType = appType;
    }

    /**
     * If true, login will invalidate any existing active session for this user
     * and continue by creating a new session.
     */
    private Boolean forcedLogin;

    // Explicit accessor methods are provided because some build/lint setups
    // used in this repo don't always recognize Lombok-generated methods.
    public Boolean getForcedLogin() {
        return forcedLogin;
    }

    public void setForcedLogin(Boolean forcedLogin) {
        this.forcedLogin = forcedLogin;
    }

    public boolean isForcedLogin() {
        return Boolean.TRUE.equals(forcedLogin);
    }
    
    // Optional encrypted payload field for RSA encryption
    private String payload;
}
