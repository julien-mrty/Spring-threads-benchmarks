package com.jm.spring_threads_benchmarks.controller;

import com.jm.spring_threads_benchmarks.dto.OrderDto;
import com.jm.spring_threads_benchmarks.repository.OrderRepo;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/orders")
public class OrderController {
    private final OrderRepo repo;

    public OrderController(OrderRepo repo) { this.repo = repo; }

    @GetMapping("/{id}")
    public OrderDto get(@PathVariable long id) {
        return repo.findById(id);
    }

    public record CreateOrderRequest(String customer, int totalCents) {}

    @PostMapping
    public ResponseEntity<OrderDto> create_order(@RequestBody CreateOrderRequest req) {
        long id = repo.create_order(req.customer(), req.totalCents());
        return ResponseEntity.ok(new OrderDto(id, req.customer(), req.totalCents()));
    }

    @GetMapping("/report/slow/{ms}")
    public ResponseEntity<String> slow(@PathVariable long ms) {
        repo.slowQueryMillis(ms);
        return ResponseEntity.ok("slept " + ms + " ms");
    }
}
