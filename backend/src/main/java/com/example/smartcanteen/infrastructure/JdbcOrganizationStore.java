package com.example.smartcanteen.infrastructure;

import com.example.smartcanteen.application.port.OrganizationStore;
import com.example.smartcanteen.domain.Canteen;
import com.example.smartcanteen.domain.School;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcOrganizationStore implements OrganizationStore {

    private final JdbcTemplate jdbc;

    public JdbcOrganizationStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<School> listSchools(
            Set<String> allowedSchoolIds, String keyword, boolean includeInactive) {
        if (allowedSchoolIds != null && allowedSchoolIds.isEmpty()) {
            return List.of();
        }
        StringBuilder sql = new StringBuilder(
                "SELECT id, name, region_code, status FROM schools WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        appendSchoolFilter(sql, args, "id", allowedSchoolIds);
        if (!includeInactive) {
            sql.append(" AND status = 'ACTIVE'");
        }
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND LOWER(name) LIKE LOWER(?)");
            args.add("%" + keyword.trim() + "%");
        }
        sql.append(" ORDER BY id");
        return jdbc.query(sql.toString(), this::mapSchool, args.toArray());
    }

    @Override
    public java.util.Optional<School> findSchool(String schoolId) {
        return jdbc.query(
                        "SELECT id, name, region_code, status FROM schools WHERE id = ?",
                        this::mapSchool,
                        schoolId)
                .stream()
                .findFirst();
    }

    @Override
    public School createSchool(School school) {
        try {
            jdbc.update(
                    "INSERT INTO schools (id, name, region_code, status) VALUES (?, ?, ?, ?)",
                    school.id(), school.name(), school.regionCode(), status(school.active()));
        } catch (DuplicateKeyException exception) {
            throw new IllegalArgumentException("school already exists: " + school.id(), exception);
        }
        return school;
    }

    @Override
    public School updateSchool(School school) {
        int updated = jdbc.update(
                "UPDATE schools SET name = ?, region_code = ?, status = ?, "
                        + "updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                school.name(), school.regionCode(), status(school.active()), school.id());
        if (updated == 0) {
            throw new IllegalArgumentException("school not found: " + school.id());
        }
        return school;
    }

    @Override
    public void updateSchoolStatus(String schoolId, boolean active) {
        if (jdbc.update(
                "UPDATE schools SET status = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                status(active), schoolId) == 0) {
            throw new IllegalArgumentException("school not found: " + schoolId);
        }
    }

    @Override
    public List<Canteen> listCanteens(
            Set<String> allowedSchoolIds,
            Set<String> allowedCanteenIds,
            String schoolId,
            String keyword,
            boolean includeInactive) {
        if ((allowedSchoolIds != null && allowedSchoolIds.isEmpty())
                || (allowedCanteenIds != null && allowedCanteenIds.isEmpty())) {
            return List.of();
        }
        StringBuilder sql = new StringBuilder(
                "SELECT c.id, c.school_id, c.name, c.address, s.region_code, c.status "
                        + "FROM canteens c JOIN schools s ON s.id = c.school_id WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        appendSchoolFilter(sql, args, "c.school_id", allowedSchoolIds);
        appendCanteenFilter(sql, args, "c.id", allowedCanteenIds);
        if (schoolId != null && !schoolId.isBlank()) {
            sql.append(" AND c.school_id = ?");
            args.add(schoolId.trim());
        }
        if (!includeInactive) {
            sql.append(" AND c.status = 'ACTIVE' AND s.status = 'ACTIVE'");
        }
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND LOWER(c.name) LIKE LOWER(?)");
            args.add("%" + keyword.trim() + "%");
        }
        sql.append(" ORDER BY c.school_id, c.id");
        return jdbc.query(sql.toString(), this::mapCanteen, args.toArray());
    }

    @Override
    public java.util.Optional<Canteen> findCanteen(String canteenId) {
        return jdbc.query(
                        "SELECT c.id, c.school_id, c.name, c.address, s.region_code, c.status "
                                + "FROM canteens c JOIN schools s ON s.id = c.school_id WHERE c.id = ?",
                        this::mapCanteen,
                        canteenId)
                .stream()
                .findFirst();
    }

    @Override
    public Canteen createCanteen(Canteen canteen) {
        try {
            jdbc.update(
                    "INSERT INTO canteens (id, school_id, name, address, status) "
                            + "VALUES (?, ?, ?, ?, ?)",
                    canteen.id(), canteen.schoolId(), canteen.name(), canteen.address(),
                    status(canteen.active()));
        } catch (DuplicateKeyException exception) {
            throw new IllegalArgumentException("canteen already exists: " + canteen.id(), exception);
        }
        return canteen;
    }

    @Override
    public Canteen updateCanteen(Canteen canteen) {
        int updated = jdbc.update(
                "UPDATE canteens SET name = ?, address = ?, status = ?, "
                        + "updated_at = CURRENT_TIMESTAMP WHERE id = ? AND school_id = ?",
                canteen.name(), canteen.address(), status(canteen.active()),
                canteen.id(), canteen.schoolId());
        if (updated == 0) {
            throw new IllegalArgumentException("canteen not found: " + canteen.id());
        }
        return findCanteen(canteen.id()).orElseThrow();
    }

    @Override
    public void updateCanteenStatus(String canteenId, boolean active) {
        if (jdbc.update(
                "UPDATE canteens SET status = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                status(active), canteenId) == 0) {
            throw new IllegalArgumentException("canteen not found: " + canteenId);
        }
    }

    private static void appendSchoolFilter(
            StringBuilder sql, List<Object> args, String column, Set<String> allowedSchoolIds) {
        if (allowedSchoolIds == null) {
            return;
        }
        sql.append(" AND ").append(column).append(" IN (")
                .append("?,".repeat(Math.max(0, allowedSchoolIds.size())))
                .deleteCharAt(sql.length() - 1)
                .append(")");
        args.addAll(allowedSchoolIds);
    }

    private School mapSchool(ResultSet result, int row) throws SQLException {
        return new School(
                result.getString("id"),
                result.getString("name"),
                result.getString("region_code"),
                "ACTIVE".equals(result.getString("status")));
    }

    private Canteen mapCanteen(ResultSet result, int row) throws SQLException {
        return new Canteen(
                result.getString("id"),
                result.getString("school_id"),
                result.getString("name"),
                result.getString("address"),
                result.getString("region_code"),
                "ACTIVE".equals(result.getString("status")));
    }

    private static String status(boolean active) {
        return active ? "ACTIVE" : "DISABLED";
    }

    private static void appendCanteenFilter(
            StringBuilder sql, List<Object> args, String column, Set<String> allowedCanteenIds) {
        if (allowedCanteenIds == null) {
            return;
        }
        sql.append(" AND ").append(column).append(" IN (")
                .append("?,".repeat(allowedCanteenIds.size()));
        sql.deleteCharAt(sql.length() - 1).append(")");
        args.addAll(allowedCanteenIds);
    }
}
