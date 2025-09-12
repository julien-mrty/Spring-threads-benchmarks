package com.jm.spring_threads_benchmarks.controller;

import com.jm.spring_threads_benchmarks.repository.OrderRepo;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired MockMvc mvc;

    @MockitoBean OrderRepo repo;

    @Test
    void create_returnsNewOrder() throws Exception {
        when(repo.create_order("alice", 1299)).thenReturn(42L);

        mvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customer\":\"alice\",\"totalCents\":1299}"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"id\":42,\"customer\":\"alice\",\"totalCents\":1299}"));
    }
}
