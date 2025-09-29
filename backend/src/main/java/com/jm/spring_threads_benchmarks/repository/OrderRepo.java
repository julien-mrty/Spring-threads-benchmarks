package com.jm.spring_threads_benchmarks.repository;

import com.jm.spring_threads_benchmarks.dto.OrderDto;
import io.micrometer.observation.annotation.Observed;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.util.Objects;

@Repository
public class OrderRepo {
    private final JdbcTemplate jdbc;
    public OrderRepo(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Observed(
            name = "db.find_by_id",
            contextualName = "orderRepo.findById",
            lowCardinalityKeyValues = {"op","select","table","orders"}
    )
    public OrderDto findById(long id) {
        try {
            return jdbc.queryForObject(
                    "select id, customer, total_cents from orders where id = ?",
                    (rs, row) -> new OrderDto(
                            rs.getLong("id"),
                            rs.getString("customer"),
                            rs.getInt("total_cents")),
                    id
            );
        } catch (EmptyResultDataAccessException e) {
            // still counted as an error by observation; keep behavior for your 404 mapper
            throw e;
        }
    }

    @Observed(
            name = "db.create_order",
            contextualName = "orderRepo.create",
            lowCardinalityKeyValues = {"op","insert","table","orders"}
    )
    public long create(String customer, int totalCents) {
        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(conn -> {
            PreparedStatement ps = conn.prepareStatement(
                    "insert into orders(customer, total_cents) values (?, ?)",
                    new String[]{"id"});
            ps.setString(1, customer);
            ps.setInt(2, totalCents);
            return ps;
        }, kh);
        return Objects.requireNonNull(kh.getKey(), "no generated id").longValue();
    }

    @Observed(
            name = "db.slow_query",
            contextualName = "orderRepo.slowQueryMillis",
            lowCardinalityKeyValues = {"op","sleep"}
    )
    public void slowQueryMillis(long ms) {
        double seconds = ms / 1000.0;
        jdbc.execute("select pg_sleep(?::double precision)", (PreparedStatement ps) -> {
            ps.setDouble(1, seconds);
            ps.execute();
            return null;
        });
    }
}
