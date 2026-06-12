package com.gulfnet.shared_library.model.request;

import com.gulfnet.shared_library.enums.AppType;
import lombok.Data;

@Data
public class ForgotPasswordRequest {
    private String email;
    private String userCode;

    /**
     * Application type from which the user is initiating the forgot-password flow
     * (e.g. HQADMIN, MANAGER, CASHIER, WAITER, KDS).
     */
    private AppType appType;

    public AppType getAppType() {
        return appType;
    }

    public void setAppType(AppType appType) {
        this.appType = appType;
    }
}