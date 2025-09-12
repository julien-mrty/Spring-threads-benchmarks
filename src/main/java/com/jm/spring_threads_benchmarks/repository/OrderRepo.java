package com.jm.spring_threads_benchmarks.repository;

import com.jm.spring_threads_benchmarks.dto.OrderDto;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;

@Repository
public class OrderRepo {
    private final JdbcTemplate jdbc;

    public OrderRepo(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        // Optional: per-JdbcTemplate query timeout (seconds)
        this.jdbc.setQueryTimeout(1);
    }

    public OrderDto findById(long id) {
        return jdbc.queryForObject(
                "select id, customer, total_cents from orders where id = ?",
                (rs, row) -> new OrderDto(
                        rs.getLong("id"),
                        rs.getString("customer"),
                        rs.getInt("total_cents")),
                id
        );
    }

    public long create(String customer, int totalCents) {
        var sql = "insert into orders(customer, total_cents) values (?, ?) returning id";
        // Portable way without RETURNING (works too):
        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(conn -> {
            PreparedStatement ps =
                    conn.prepareStatement("insert into orders(customer, total_cents) values (?, ?)",
                            new String[] {"id"});
            ps.setString(1, customer);
            ps.setInt(2, totalCents);
            return ps;
        }, kh);
        return kh.getKey().longValue();
    }

    public void slowQueryMillis(long ms) {
        // Postgres sleep takes seconds (double). Convert ms -> seconds.
        double seconds = ms / 1000.0;
        jdbc.execute("select pg_sleep(?::double precision)", (PreparedStatement ps) -> {
            ps.setDouble(1, seconds);
            ps.execute();
            return null;
        });
    }
}
