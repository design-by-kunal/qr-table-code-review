package com.gulfnet.restaurantmanagement.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "gmo")
public class GmoProperties {

    /**
     * Base URL for GMO QR payment gateway, e.g. https://stg-pos-gw.gcp.gmopg.jp
     */
    private String baseUrl;

    /**
     * Device user login ID, e.g. T1150419. Must come from environment, not hard-coded.
     */
    private String loginId;

    /**
     * Device user password. Must come from environment, not hard-coded.
     */
    private String userPassword;

    /**
     * OS / application name, e.g. restaurant-app.
     */
    private String osName;

    /**
     * OS / application version, e.g. 1.0.0.
     */
    private String osVersion;
}

