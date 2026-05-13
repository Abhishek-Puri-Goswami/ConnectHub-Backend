package com.connecthub.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "app")
public class ConnectHubGatewayProperties {

    private List<String> openEndpoints = new ArrayList<>();

    public List<String> getOpenEndpoints() {
        return openEndpoints;
    }

    public void setOpenEndpoints(List<String> openEndpoints) {
        this.openEndpoints = openEndpoints;
    }
}
