package com.example.smartcanteen.application.port;

import com.example.smartcanteen.domain.Canteen;
import com.example.smartcanteen.domain.School;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface OrganizationStore {

    List<School> listSchools(Set<String> allowedSchoolIds, String keyword, boolean includeInactive);

    Optional<School> findSchool(String schoolId);

    School createSchool(School school);

    School updateSchool(School school);

    void updateSchoolStatus(String schoolId, boolean active);

    List<Canteen> listCanteens(
            Set<String> allowedSchoolIds,
            Set<String> allowedCanteenIds,
            String schoolId,
            String keyword,
            boolean includeInactive);

    Optional<Canteen> findCanteen(String canteenId);

    Canteen createCanteen(Canteen canteen);

    Canteen updateCanteen(Canteen canteen);

    void updateCanteenStatus(String canteenId, boolean active);
}
