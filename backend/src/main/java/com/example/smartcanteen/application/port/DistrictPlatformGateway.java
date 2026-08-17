package com.example.smartcanteen.application.port;

import java.util.List;

/**
 * Replaceable boundary for the district/county platform. No network adapter is
 * bundled until credentials and the target platform contract are supplied.
 */
public interface DistrictPlatformGateway {

    AccessToken authenticate(Credentials credentials);

    void push(AccessToken token, DataBatch batch);

    record Credentials(String clientId, String clientSecret) {
    }

    record AccessToken(String value, java.time.Instant expiresAt) {
    }

    record DataBatch(String schoolId, List<String> resourceTypes) {
        public DataBatch {
            resourceTypes = List.copyOf(resourceTypes);
        }
    }
}
