insert into orders (customer, total_cents) values
  ('alice', 1299),
  ('bob',   2599)
on conflict do nothing;
