create table if not exists orders (
  id           bigserial primary key,
  customer     text        not null,
  total_cents  int         not null,
  updated_at   timestamptz not null default now()
);
