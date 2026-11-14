package com.hmdm.persistence;

import com.hmdm.persistence.mapper.DeviceMapper;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

import static org.junit.Assert.*;

public class DeviceMapperIsolationTest {

    private static SqlSessionFactory factory;

    @BeforeClass
    public static void setup() throws Exception {
        String url = System.getProperty("acl.jdbc.url");
        if (url == null || !url.startsWith("jdbc:postgresql://127.0.0.1:")) {
            throw new IllegalStateException(
                "An isolated localhost PostgreSQL URL is required");
        }

        DataSource ds = new UnpooledDataSource(
            "org.postgresql.Driver", url, "postgres", "test_only_password");

        try (Connection c = ds.getConnection();
             Statement s = c.createStatement()) {

            s.execute("CREATE TABLE users (" +
                "id integer PRIMARY KEY, customerId integer, " +
                "allDevicesAvailable boolean)");

            s.execute("CREATE TABLE devices (" +
                "id integer PRIMARY KEY, customerId integer, number text)");

            s.execute("CREATE TABLE deviceGroups (" +
                "deviceId integer, groupId integer)");

            s.execute("CREATE TABLE userDeviceGroupsAccess (" +
                "userId integer, groupId integer)");

            s.execute("INSERT INTO users VALUES " +
                "(10,1,true),(11,1,false),(12,1,false),(20,2,true)");

            s.execute("INSERT INTO devices VALUES " +
                "(100,1,'device-a'),(101,1,'device-b'),(200,2,'device-c')");

            s.execute("INSERT INTO deviceGroups VALUES " +
                "(100,50),(101,51),(200,60)");

            s.execute("INSERT INTO userDeviceGroupsAccess VALUES " +
                "(11,50),(12,51)");
        }

        Environment env = new Environment(
            "isolated-test", new JdbcTransactionFactory(), ds);

        Configuration config = new Configuration(env);
        config.getTypeAliasRegistry()
            .registerAliases("com.hmdm.persistence.domain");
        config.addMapper(DeviceMapper.class);

        factory = new SqlSessionFactoryBuilder().build(config);
    }

    private boolean access(int device, int user, int customer) {
        try (SqlSession session = factory.openSession()) {
            return session.getMapper(DeviceMapper.class)
                .isDeviceAccessibleToUser(device, user, customer);
        }
    }

    @Test
    public void unrestrictedUserCanAccessOwnDevice() {
        assertTrue(access(100, 10, 1));
    }

    @Test
    public void groupMemberCanAccessAssignedDevice() {
        assertTrue(access(100, 11, 1));
    }

    @Test
    public void groupMemberCannotAccessOtherGroup() {
        assertFalse(access(101, 11, 1));
    }

    @Test
    public void tenantIsolationWorksForCorrectUserContext() {
        assertFalse(access(200, 10, 1));
        assertFalse(access(100, 20, 2));
    }

    @Test
    public void unknownDeviceIsDenied() {
        assertFalse(access(999, 10, 1));
    }

    @Test
    public void deviceListMustEnforceUserCustomerIsolation() {
        try (SqlSession session = factory.openSession()) {
            DeviceMapper mapper = session.getMapper(DeviceMapper.class);

            assertTrue(mapper.getDeviceNumbersAccessibleToUser(10, 1)
                .contains("device-a"));

            assertFalse(mapper.getDeviceNumbersAccessibleToUser(11, 1)
                .contains("device-b"));

            assertTrue(mapper.getDeviceNumbersAccessibleToUser(11, 1)
                .contains("device-a"));

            assertTrue(mapper.getDeviceNumbersAccessibleToUser(10, 2)
                .isEmpty());
        }
    }

    @Test
    public void mismatchedUserAndCustomerMustBeDenied() {
        assertFalse(
            "Mapper must independently enforce user/customer isolation",
            access(200, 10, 2));
    }
}
