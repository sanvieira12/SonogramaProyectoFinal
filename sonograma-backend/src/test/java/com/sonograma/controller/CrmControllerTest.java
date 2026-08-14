package com.sonograma.controller;

import com.sonograma.dto.crm.CrmDtos;
import com.sonograma.enums.TipoInteresCrm;
import com.sonograma.service.crm.CrmInterestService;
import com.sonograma.service.crm.CrmProfileService;
import com.sonograma.service.crm.CrmRecommendationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CrmControllerTest {

    @Mock private CrmProfileService profileService;
    @Mock private CrmInterestService interestService;
    @Mock private CrmRecommendationService recommendationService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                new CrmController(profileService, interestService, recommendationService)).build();
    }

    @Test
    void returnsRecommendationContractAndPassesRequestedLimit() throws Exception {
        when(recommendationService.recommendations(7L, 100)).thenReturn(List.of(
                new CrmDtos.Recomendacion(11L, "Autechre", "Amber", "Warp", 1994,
                        "Electronic", "IDM", "LP", "NUEVO", "/cover.jpg",
                        new BigDecimal("1800"), 2, new BigDecimal("71.25"), "ALTA",
                        List.of("Ya compró discos de Autechre"))));

        mockMvc.perform(get("/crm/clientes/7/recomendaciones").param("limit", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].idDisco").value(11))
                .andExpect(jsonPath("$[0].cantidadDisponible").value(2))
                .andExpect(jsonPath("$[0].nivelAfinidad").value("ALTA"))
                .andExpect(jsonPath("$[0].razones[0]").value("Ya compró discos de Autechre"));

        verify(recommendationService).recommendations(7L, 100);
    }

    @Test
    void validatesCreatesAndUpdatesInterests() throws Exception {
        CrmDtos.Interes response = new CrmDtos.Interes(3L, 7L, TipoInteresCrm.ARTISTA,
                "Björk", true, LocalDateTime.of(2026, 8, 14, 10, 0));
        when(interestService.create(any(), any())).thenReturn(response);
        when(interestService.setActive(7L, 3L, false)).thenReturn(
                new CrmDtos.Interes(3L, 7L, TipoInteresCrm.ARTISTA,
                        "Björk", false, response.fechaCreacion()));

        mockMvc.perform(post("/crm/clientes/7/intereses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tipo\":\"ARTISTA\",\"texto\":\"Björk\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipo").value("ARTISTA"))
                .andExpect(jsonPath("$.activo").value(true));

        mockMvc.perform(post("/crm/clientes/7/intereses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"texto\":\"   \"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(patch("/crm/clientes/7/intereses/3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activo\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(false));
    }
}
