package com.jm.spring_threads_benchmarks.controller;

import com.jm.spring_threads_benchmarks.dto.OrderDto;
import com.jm.spring_threads_benchmarks.repository.OrderRepo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import org.springframework.dao.EmptyResultDataAccessException;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean OrderRepo orderRepo;

    @Test
    void post_creates_order_201() throws Exception {
        when(orderRepo.create("alice", 1299)).thenReturn(42L);

        String json = "{\"customer\":\"alice\",\"totalCents\":1299}";

        mvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())                       // or isOk() if your controller returns 200
                .andExpect(header().string("Location", "/orders/42"))  // keep only if controller sets Location
                .andExpect(content().json("{\"id\":42,\"customer\":\"alice\",\"totalCents\":1299}"));
    }

    @Test
    void get_by_id_200() throws Exception {
        when(orderRepo.findById(7L)).thenReturn(new OrderDto(7L, "bob", 500));

        mvc.perform(get("/orders/7"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"id\":7,\"customer\":\"bob\",\"totalCents\":500}"));
    }

    @Test
    void get_by_id_404_when_missing() throws Exception {
        doThrow(new EmptyResultDataAccessException(1)).when(orderRepo).findById(999L);

        mvc.perform(get("/orders/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not Found"));
    }

    @Test
    void post_validation_400_on_bad_payload() throws Exception {
        // Only meaningful if you added @Valid + constraints on CreateOrderRequest
        String bad = "{\"customer\":\"\",\"totalCents\":-1}";
        mvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bad))
                .andExpect(status().isBadRequest());
    }
}
