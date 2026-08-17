package com.example.smartcanteen.application.port;

import com.example.smartcanteen.domain.AlertReport;
import java.util.List;

/** Replaceable boundary for city/county bright-kitchen systems and cameras. */
public interface BrightKitchenGateway {

    Session login(Credentials credentials);

    List<Organization> organizations(Session session);

    List<Camera> cameras(Session session, String schoolId);

    StreamEndpoint liveStream(Session session, String cameraId);

    List<AlertReport> warnings(Session session, String schoolId);

    record Credentials(String username, String password) {
    }

    record Session(String accessToken, java.time.Instant expiresAt) {
    }

    record Organization(String schoolId, String schoolName, String areaCode) {
    }

    record Camera(String cameraId, String deviceName, String schoolId) {
    }

    record StreamEndpoint(String url, String format) {
    }
}
