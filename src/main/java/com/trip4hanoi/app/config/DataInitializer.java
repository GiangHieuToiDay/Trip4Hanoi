package com.trip4hanoi.app.config;

import com.trip4hanoi.app.common.AuthProvider;
import com.trip4hanoi.app.common.UserStatus;
import com.trip4hanoi.app.entity.*;
import com.trip4hanoi.app.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j(topic = "DATA INITIALIZER")
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.init.admin-password:123456}")
    private String adminPassword;

    @Value("${app.init.staff-password:123456}")
    private String staffPassword;

    @Override
    @Transactional
    public void run(String... args) {
        log.info("Starting Core Data Initialization...");

        // CHỈ GIỮ LẠI: Hệ thống Quyền, Vai Trò và Tài khoản mặc định để hệ thống chạy được
        createPermissions();
        createRoles();
        createAccounts();

        log.info("Core Data Initialization Completed Successfully!");
    }

    private void createPermissions() {
        // Administrative & System
        createPermissionIfNotExist("MANAGE_USER", "Quản trị người dùng (Xem/Sửa/Khóa)");
        createPermissionIfNotExist("MANAGE_ROLE", "Quản trị vai trò và quyền hạn");

        // Category & Content
        createPermissionIfNotExist("MANAGE_CATEGORY", "Quản trị danh mục");
        createPermissionIfNotExist("MANAGE_PLACE", "Quản trị địa điểm");
        createPermissionIfNotExist("MANAGE_EVENT", "Quản trị sự kiện du lịch");
        createPermissionIfNotExist("MODERATE_CONTENT", "Kiểm duyệt bài đăng, bình luận, đánh giá");

        // Basic Access & Chat
        createPermissionIfNotExist("VIEW_ALL", "Xem thông tin hệ thống (Công khai)");
        createPermissionIfNotExist("APPROVE_CHAT", "Quyền dành cho Staff để nhận phòng.");
        createPermissionIfNotExist("MANAGE_CHAT", "Quyền dành cho Admin/Staff để quản lý hoặc viết ghi chú");
    }

    private void createPermissionIfNotExist(String name, String description) {
        if (!permissionRepository.existsByName(name)) {
            permissionRepository.save(Permission.builder()
                    .name(name)
                    .description(description)
                    .build());
        }
    }

    private void createRoles() {
        List<Permission> allPermissions = permissionRepository.findAll();

        // ADMIN: Toàn quyền
        Set<Permission> adminPermissions = new HashSet<>(allPermissions);
        createRoleIfNotExist("ADMIN", "Quản trị viên hệ thống", adminPermissions);

        // STAFF: Quản lý nội dung và kiểm duyệt cộng đồng
        Set<String> staffPermissionNames = Set.of(
                "MANAGE_PLACE", "MANAGE_EVENT", "MODERATE_CONTENT", "VIEW_ALL", "MANAGE_CATEGORY",
                "APPROVE_CHAT", "MANAGE_CHAT"
        );
        Set<Permission> staffPermissions = allPermissions.stream()
                .filter(p -> staffPermissionNames.contains(p.getName()))
                .collect(Collectors.toSet());
        createRoleIfNotExist("STAFF", "Nhên viên vận hành nội dung", staffPermissions);

        // USER: Quyền xem cơ bản
        Set<String> userPermissionNames = Set.of("VIEW_ALL");
        Set<Permission> userPermissions = allPermissions.stream()
                .filter(p -> userPermissionNames.contains(p.getName()))
                .collect(Collectors.toSet());
        createRoleIfNotExist("USER", "Người dùng ứng dụng", userPermissions);
    }

    private void createRoleIfNotExist(String name, String description, Set<Permission> permissions) {
        roleRepository.findByName(name).ifPresentOrElse(
                role -> {
                    role.setPermissions(permissions);
                    roleRepository.save(role);
                },
                () -> {
                    Role newRole = Role.builder()
                            .name(name)
                            .description(description)
                            .permissions(permissions)
                            .build();
                    roleRepository.save(newRole);
                }
        );
    }

    private void createAccounts() {
        Role adminRole = roleRepository.findByName("ADMIN").orElseThrow();
        Role staffRole = roleRepository.findByName("STAFF").orElseThrow();
        Role userRole = roleRepository.findByName("USER").orElseThrow();

        createDefaultAccount("HanoiAdmin", "admin@trip4hanoi.com", adminPassword, Set.of(adminRole));
        createDefaultAccount("HanoiStaff1", "staff_1@trip4hanoi.com", staffPassword, Set.of(staffRole));
        createDefaultAccount("HanoiUser", "user@gmail.com", "123456", Set.of(userRole));
    }

    private void createDefaultAccount(String username, String email, String password, Set<Role> roles) {
        if (!userRepository.existsByEmail(email)) {
            User user = User.builder()
                    .username(username)
                    .email(email)
                    .password(passwordEncoder.encode(password))
                    .roles(roles)
                    .provider(AuthProvider.LOCAL)
                    .status(UserStatus.ACTIVE)
                    .build();
            userRepository.save(user);
            log.info("Created default account: {}", email);
        }
    }
}