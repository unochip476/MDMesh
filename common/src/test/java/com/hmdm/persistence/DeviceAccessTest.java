package com.hmdm.persistence;

import com.google.inject.Injector;
import com.hmdm.persistence.domain.Device;
import com.hmdm.persistence.domain.User;
import com.hmdm.persistence.domain.UserRole;
import com.hmdm.persistence.mapper.DeviceMapper;
import com.hmdm.security.SecurityContext;
import org.junit.After;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class DeviceAccessTest {

    private final AtomicInteger mapperCalls = new AtomicInteger();

    @After
    public void cleanup() {
        SecurityContext.release();
    }

    private DeviceDAO dao(boolean permittedByMapper) {
        DeviceMapper mapper = (DeviceMapper) Proxy.newProxyInstance(
            DeviceMapper.class.getClassLoader(),
            new Class<?>[]{DeviceMapper.class},
            (proxy, method, args) -> {
                if ("isDeviceAccessibleToUser".equals(method.getName())) {
                    mapperCalls.incrementAndGet();
                    assertEquals(100, args[0]);
                    assertEquals(10, args[1]);
                    assertEquals(1, args[2]);
                    return permittedByMapper;
                }
                throw new UnsupportedOperationException(method.getName());
            }
        );

        Injector injector = (Injector) Proxy.newProxyInstance(
            Injector.class.getClassLoader(),
            new Class<?>[]{Injector.class},
            (proxy, method, args) -> {
                if ("getAllBindings".equals(method.getName())) {
                    return Collections.emptyMap();
                }
                throw new UnsupportedOperationException(method.getName());
            }
        );

        return new DeviceDAO(mapper, null, injector, null, 0);
    }

    private User user(int customerId, boolean superAdmin) {
        User user = new User();
        user.setId(10);
        user.setCustomerId(customerId);

        UserRole role = new UserRole();
        role.setSuperAdmin(superAdmin);
        user.setUserRole(role);

        return user;
    }

    private Device device(int customerId) {
        Device device = new Device();
        device.setId(100);
        device.setCustomerId(customerId);
        return device;
    }

    @Test
    public void superAdminCanAccessOwnTenant() {
        SecurityContext.init(user(1, true));

        assertTrue(dao(false).isAccessibleToUser(device(1)));
        assertEquals(0, mapperCalls.get());
    }

    @Test
    public void superAdminCannotAccessAnotherTenant() {
        SecurityContext.init(user(1, true));

        assertFalse(dao(true).isAccessibleToUser(device(2)));
        assertEquals(0, mapperCalls.get());
    }

    @Test
    public void regularUserCanAccessPermittedDevice() {
        SecurityContext.init(user(1, false));

        assertTrue(dao(true).isAccessibleToUser(device(1)));
        assertEquals(1, mapperCalls.get());
    }

    @Test
    public void regularUserCannotAccessRestrictedDevice() {
        SecurityContext.init(user(1, false));

        assertFalse(dao(false).isAccessibleToUser(device(1)));
        assertEquals(1, mapperCalls.get());
    }

    @Test
    public void regularUserCannotAccessAnotherTenant() {
        SecurityContext.init(user(1, false));

        assertFalse(dao(true).isAccessibleToUser(device(2)));
        assertEquals(0, mapperCalls.get());
    }

    @Test
    public void customerOnlyContextCannotAccessDevice() {
        SecurityContext.init(Integer.valueOf(1));

        assertFalse(dao(true).isAccessibleToUser(device(1)));
        assertEquals(0, mapperCalls.get());
    }

    @Test
    public void missingDeviceIsDenied() {
        SecurityContext.init(user(1, true));

        assertFalse(dao(true).isAccessibleToUser(null));
        assertEquals(0, mapperCalls.get());
    }

    @Test
    public void deviceWithoutIdIsDenied() {
        SecurityContext.init(user(1, true));

        assertFalse(dao(true).isAccessibleToUser(new Device()));
        assertEquals(0, mapperCalls.get());
    }
}
