package com.fixconnect;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** End-to-end smoke test of the main customer -> provider flow against the seeded demo data. */
@SpringBootTest
@AutoConfigureMockMvc
class FixConnectApiTests {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    private String login(String email, String role) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"12345\",\"role\":\"" + role + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + json.readTree(body).get("token").asText();
    }

    @Test
    void publicEndpointsAndSwaggerWork() throws Exception {
        mvc.perform(get("/api/services")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(8));
        // bundled website
        mvc.perform(get("/")).andExpect(status().isFound()).andExpect(redirectedUrl("/index.html"));
        mvc.perform(get("/index.html")).andExpect(status().isOk());
        mvc.perform(get("/api.js")).andExpect(status().isOk());
        mvc.perform(get("/customer-dashboard.html")).andExpect(status().isOk());
        mvc.perform(get("/api/technicians/top")).andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs/1-all")).andExpect(status().isOk());
        mvc.perform(post("/api/ai/diagnose").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"AC unit is leaking water from the front vent and blowing lukewarm air\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoryCode").value("AC_REPAIR"));
    }

    @Test
    void wrongRoleIsRejected() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"customer@gmail.com\",\"password\":\"12345\",\"role\":\"PROVIDER\"}"))
                .andExpect(status().isUnauthorized());
        String provider = login("provider@gmail.com", "PROVIDER");
        mvc.perform(get("/api/customer/dashboard").header("Authorization", provider)).andExpect(status().isForbidden());
        mvc.perform(get("/api/customer/dashboard")).andExpect(status().isUnauthorized());
    }

    @Test
    void fullRequestLifecycle() throws Exception {
        String customer = login("customer@gmail.com", "CUSTOMER");
        String provider = login("provider@gmail.com", "PROVIDER");

        mvc.perform(get("/api/customer/dashboard").header("Authorization", customer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.fullName").value("Naga Sudha"));

        // customer books Rahul (provider@gmail.com) directly
        long rahulId = json.readTree(mvc.perform(get("/api/users/me").header("Authorization", provider))
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
        String create = "{\"categoryCode\":\"AC_REPAIR\",\"description\":\"AC not cooling, compressor turns off\","
                + "\"preferredDate\":\"" + LocalDate.now().plusDays(3) + "\",\"timeSlot\":\"MORNING\",\"technicianId\":" + rahulId + "}";
        JsonNode req = json.readTree(mvc.perform(post("/api/customer/requests").header("Authorization", customer)
                        .contentType(MediaType.APPLICATION_JSON).content(create))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        long id = req.get("id").asLong();

        mvc.perform(post("/api/provider/requests/" + id + "/accept").header("Authorization", provider))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACCEPTED"));
        for (String s : new String[]{"EN_ROUTE", "ARRIVED", "IN_PROGRESS"}) {
            mvc.perform(patch("/api/provider/jobs/" + id + "/status").header("Authorization", provider)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"" + s + "\"}"))
                    .andExpect(status().isOk());
        }
        JsonNode inv = json.readTree(mvc.perform(post("/api/provider/jobs/" + id + "/invoice").header("Authorization", provider)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"laborCost\":499,\"partsCost\":350}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        mvc.perform(patch("/api/provider/jobs/" + id + "/status").header("Authorization", provider)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isOk());

        mvc.perform(post("/api/customer/invoices/" + inv.get("id").asLong() + "/pay").header("Authorization", customer)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"method\":\"UPI\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.pointsEarned").value(80));
        mvc.perform(get("/api/invoices/" + inv.get("id").asLong() + "/pdf").header("Authorization", customer))
                .andExpect(status().isOk()).andExpect(content().contentType(MediaType.APPLICATION_PDF));
        mvc.perform(post("/api/customer/requests/" + id + "/review").header("Authorization", customer)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"rating\":5,\"comment\":\"Great\"}"))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/customer/requests/" + id + "/timeline").header("Authorization", customer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.currentStatus").value("COMPLETED"));
        mvc.perform(get("/api/provider/performance").header("Authorization", provider)).andExpect(status().isOk());
    }

    @Test
    void passwordResetByEmailLink() throws Exception {
        String forgot = mvc.perform(post("/api/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"arjun@fixconnect.ai\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String token = json.readTree(forgot).get("resetToken").asText();

        // unknown e-mails get the same answer and no token
        mvc.perform(post("/api/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody@example.com\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.resetToken").doesNotExist());

        mvc.perform(get("/api/auth/reset-password/validate").param("token", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.valid").value(true));
        mvc.perform(post("/api/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\",\"newPassword\":\"Newpass@1\",\"confirmPassword\":\"Newpass@1\"}"))
                .andExpect(status().isOk());
        // single use
        mvc.perform(get("/api/auth/reset-password/validate").param("token", token))
                .andExpect(jsonPath("$.valid").value(false));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"arjun@fixconnect.ai\",\"password\":\"Newpass@1\",\"role\":\"PROVIDER\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void adminVerifiesProvidersAndDeactivatesAccounts() throws Exception {
        String admin = login("adminfixconnectai@gmail.com", "Admin@123", "ADMIN");
        String customer = login("customer@gmail.com", "CUSTOMER");

        // only admins can use the console
        mvc.perform(get("/api/admin/dashboard").header("Authorization", customer)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/dashboard").header("Authorization", admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.stats.pendingVerification").value(1));

        // pending technician is hidden from customers and cannot go online until verified
        long maheshId = json.readTree(mvc.perform(get("/api/admin/providers").param("verification", "PENDING")
                .header("Authorization", admin)).andReturn().getResponse().getContentAsString()).get(0).get("id").asLong();
        String mahesh = login("mahesh@fixconnect.ai", "12345", "PROVIDER");
        mvc.perform(patch("/api/provider/availability").header("Authorization", mahesh)
                .contentType(MediaType.APPLICATION_JSON).content("{\"available\":true}")).andExpect(status().isForbidden());
        mvc.perform(get("/api/technicians/" + maheshId)).andExpect(status().isNotFound());

        mvc.perform(patch("/api/admin/providers/" + maheshId + "/verification").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idVerified\":true,\"licenseVerified\":true,\"backgroundCheckCleared\":true,\"note\":\"ok\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.verified").value(true));
        mvc.perform(patch("/api/provider/availability").header("Authorization", mahesh)
                .contentType(MediaType.APPLICATION_JSON).content("{\"available\":true}")).andExpect(status().isOk());
        mvc.perform(get("/api/technicians/" + maheshId)).andExpect(status().isOk());

        // deactivate a customer: existing session stops working, login refused; reactivate restores access
        String kiran = login("kiran@example.com", "12345", "CUSTOMER");
        long kiranId = json.readTree(mvc.perform(get("/api/users/me").header("Authorization", kiran))
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
        mvc.perform(patch("/api/admin/users/" + kiranId + "/status").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"active\":false,\"reason\":\"test\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
        mvc.perform(get("/api/customer/dashboard").header("Authorization", kiran)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"kiran@example.com\",\"password\":\"12345\",\"role\":\"CUSTOMER\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/admin/users/" + kiranId + "/status").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"active\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(true));
        login("kiran@example.com", "12345", "CUSTOMER");
    }

    private String login(String email, String password, String role) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\",\"role\":\"" + role + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + json.readTree(body).get("token").asText();
    }
}
