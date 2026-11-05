package com.ilyas.budget.account;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.jwt.secret=Y2xlLWRlLXRlc3QtdW5pcXVlbWVudC1wb3VyLWxlcy10ZXN0cy1qd3Q=")
@AutoConfigureMockMvc
@Testcontainers
class AccountControllerTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    @Autowired
    private MockMvc mockMvc;

    private String inscrireEtConnecter() throws Exception {
        String email = UUID.randomUUID() + "@test.com";

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"unmotdepasselong","displayName":"Test"}
                                """.formatted(email)))
                .andExpect(status().isCreated());

        String reponse = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"unmotdepasselong"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return JsonPath.read(reponse, "$.token");
    }

    private String creerCompte(String token) throws Exception {
        String reponse = mockMvc.perform(post("/accounts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Compte de A","type":"COURANT","initialBalance":100.00}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return JsonPath.read(reponse, "$.id");
    }

    @Test
    void b_ne_peut_pas_lire_le_compte_de_a() throws Exception {
        // given
        String tokenA = inscrireEtConnecter();
        String tokenB = inscrireEtConnecter();
        String idCompteA = creerCompte(tokenA);

        // when + then : B essaie de lire le compte de A
        mockMvc.perform(get("/accounts/{id}", idCompteA)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());

        // cas positif : A, lui, peut le lire
        mockMvc.perform(get("/accounts/{id}", idCompteA)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk());
    }

    @Test
    void b_ne_peut_pas_modifier_le_compte_de_a() throws Exception {
        //given
        String tokenA = inscrireEtConnecter();
        String tokenB = inscrireEtConnecter();
        String idCompteA = creerCompte(tokenA);

        // when + then : B essaie de modifier le compte de A
        mockMvc.perform(put("/accounts/{id}", idCompteA)
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Compte de B","type":"COURANT","initialBalance":100.00}
                                """))
                .andExpect(status().isNotFound());

        // cas positif : A, lui, peut le lire et il est pas modifier
        mockMvc.perform(get("/accounts/{id}", idCompteA)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Compte de A"));
    }

    @Test
    void b_ne_peut_pas_supprimer_le_compte_de_a() throws Exception {
        //given
        String tokenA = inscrireEtConnecter();
        String tokenB = inscrireEtConnecter();
        String idCompteA = creerCompte(tokenA);

        // when + then : B essaie de supprimer le compte de A
        mockMvc.perform(delete("/accounts/{id}", idCompteA)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());

        // cas positif : A, lui, peut le lire
        mockMvc.perform(get("/accounts/{id}", idCompteA)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk());

    }

    @Test
    void b_ne_voit_pas_le_compte_de_a_dans_sa_liste() throws Exception {
        //given
        String tokenA = inscrireEtConnecter();
        String tokenB = inscrireEtConnecter();
        String idCompteA = creerCompte(tokenA);

        // when + then : B essaie de lire ses comptes et ne trouve rien
        mockMvc.perform(get("/accounts")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        // cas positif : A, lui, peut lire son compte
        mockMvc.perform(get("/accounts")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(idCompteA));
    }
}
