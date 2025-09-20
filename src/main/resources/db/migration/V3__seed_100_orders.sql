INSERT INTO orders (customer, total_cents)
SELECT 'seed-' || g, (random()*100000)::int + 100
FROM generate_series(1, 1000) AS g;