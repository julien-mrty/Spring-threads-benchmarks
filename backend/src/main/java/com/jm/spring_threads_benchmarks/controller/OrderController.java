package com.jm.spring_threads_benchmarks.controller;

import com.jm.spring_threads_benchmarks.dto.OrderDto;
import com.jm.spring_threads_benchmarks.repository.OrderRepo;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.Valid;

@Validated
@RestController
@RequestMapping("/orders")
public class OrderController {
    private final OrderRepo orderRepo;

    public OrderController(OrderRepo orderRepo) {
        this.orderRepo = orderRepo;
    }

    @GetMapping("/{id}")
    public OrderDto get(@PathVariable long id) {
        return orderRepo.findById(id);
    }

    public record CreateOrderRequest(
            @jakarta.validation.constraints.NotBlank String customer,
            @jakarta.validation.constraints.Positive int totalCents) {}

    @PostMapping
    public ResponseEntity<OrderDto> create(@Valid @RequestBody CreateOrderRequest req) {
        long id = orderRepo.create(req.customer(), req.totalCents());
        return ResponseEntity
                .created(java.net.URI.create("/orders/" + id))
                .body(new OrderDto(id, req.customer(), req.totalCents()));
    }

    @GetMapping("/report/slow/{ms}")
    public ResponseEntity<String> slow(@jakarta.validation.constraints.Min(0) @PathVariable long ms) {
        orderRepo.slowQueryMillis(ms);
        return ResponseEntity.ok("slept " + ms + " ms");
    }
}
