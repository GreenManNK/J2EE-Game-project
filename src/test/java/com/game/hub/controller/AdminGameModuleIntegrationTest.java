package com.game.hub.controller;

import com.game.hub.config.RoleGuardInterceptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "app.game-modules.external-registry-path=target/test-external-game-modules-admin.json"
})
class AdminGameModuleIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void resetRegistryFile() throws Exception {
        Files.deleteIfExists(Path.of("target/test-external-game-modules-admin.json"));
    }

    @Test
    void anonymousUserCannotUseAdminGameModuleApi() throws Exception {
        mockMvc.perform(get("/admin/game-modules/api"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(get("/admin/game-modules/api/export"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void managerCannotOpenAdminModuleUiOrImportModules() throws Exception {
        MockHttpSession managerSession = session("manager-1", "Manager");

        mockMvc.perform(get("/admin").session(managerSession))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/admin/game-modules/api").session(managerSession))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/admin/game-modules/api/export").session(managerSession))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/admin/game-modules/api/import")
                .session(managerSession)
                .with(SecurityMockMvcRequestPostProcessors.csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "replaceAll": false,
                      "modules": [
                        {
                          "code": "python-blast",
                          "displayName": "Python Blast"
                        }
                      ]
                    }
                    """))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/admin/game-modules/api/preview")
                .session(managerSession)
                .with(SecurityMockMvcRequestPostProcessors.csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "modules": [
                        {
                          "code": "python-blast",
                          "displayName": "Python Blast"
                        }
                      ]
                    }
                    """))
            .andExpect(status().isForbidden());

        mockMvc.perform(delete("/admin/game-modules/api/python-blast")
                .session(managerSession)
                .with(SecurityMockMvcRequestPostProcessors.csrf()))
            .andExpect(status().isForbidden());
    }

    @Test
    void adminCanOpenUiImportAndDeleteModules() throws Exception {
        MockHttpSession adminSession = session("admin-1", "Admin");

        mockMvc.perform(get("/admin").session(adminSession))
            .andExpect(status().isOk())
            .andExpect(view().name("admin/index"));

        mockMvc.perform(post("/admin/game-modules/api/preview")
                .session(adminSession)
                .with(SecurityMockMvcRequestPostProcessors.csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "modules": [
                        {
                          "code": "python-blast",
                          "displayName": "Python Blast",
                          "runtime": "python",
                          "primaryActionUrl": "https://games.example.com/python-blast"
                        }
                      ]
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.previewedModules").value(1))
            .andExpect(jsonPath("$.modules[0].code").value("python-blast"));

        mockMvc.perform(get("/admin/game-modules/api").session(adminSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.configuredModules").value(0));

        mockMvc.perform(post("/admin/game-modules/api/import")
                .session(adminSession)
                .with(SecurityMockMvcRequestPostProcessors.csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "replaceAll": false,
                      "modules": [
                        {
                          "code": "python-blast",
                          "displayName": "Python Blast",
                          "runtime": "python",
                          "sourceType": "external-module",
                          "detailMode": "redirect",
                          "primaryActionUrl": "https://games.example.com/python-blast",
                          "apiBaseUrl": "https://api.example.com/python-blast"
                        }
                      ]
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.importedModules").value(1));

        mockMvc.perform(get("/admin/game-modules/api").session(adminSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.configuredModules").value(1))
            .andExpect(jsonPath("$.modules[0].code").value("python-blast"));

        mockMvc.perform(get("/admin/game-modules/api/export").session(adminSession))
            .andExpect(status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"external-game-modules-backup.json\""))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.containsString("\"code\" : \"python-blast\"")));

        mockMvc.perform(delete("/admin/game-modules/api/python-blast")
                .session(adminSession)
                .with(SecurityMockMvcRequestPostProcessors.csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    private MockHttpSession session(String userId, String role) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(RoleGuardInterceptor.AUTH_USER_ID, userId);
        session.setAttribute(RoleGuardInterceptor.AUTH_ROLE, role);
        return session;
    }
}
