\set ON_ERROR_STOP on
BEGIN;
CREATE TEMP TABLE orders_probe (
  id bigint, portfolio_id int, user_id int, symbol text, created_at timestamptz,
  amount numeric(19,4)
);
INSERT INTO orders_probe
SELECT n,n%1000,n%500,'SYM'||(n%100),timestamptz '2026-01-01' + n*interval '1 second',n%100
FROM generate_series(1,300000) n;
CREATE TEMP TABLE balance_probe (id int PRIMARY KEY, cash numeric(19,4));
INSERT INTO balance_probe SELECT n,100000 FROM generate_series(1,1000) n;
ANALYZE orders_probe;
ANALYZE balance_probe;
CREATE TEMP TABLE results(phase text, workload text, iteration int, milliseconds numeric, plan json);
DO $$
DECLARE phase text; workload text; statement text; p json; i int;
BEGIN
  FOREACH phase IN ARRAY ARRAY['baseline','indexed'] LOOP
    IF phase='indexed' THEN
      CREATE INDEX ON orders_probe(portfolio_id,created_at DESC);
      CREATE INDEX ON orders_probe(user_id,created_at DESC);
      CREATE INDEX ON orders_probe(symbol,created_at DESC);
      ANALYZE orders_probe;
    END IF;
    FOREACH workload IN ARRAY ARRAY['portfolio_history','user_orders','symbol_orders','insert_batch','balance_update'] LOOP
      statement := CASE workload
        WHEN 'portfolio_history' THEN 'SELECT * FROM orders_probe WHERE portfolio_id=42 ORDER BY created_at DESC LIMIT 50'
        WHEN 'user_orders' THEN 'SELECT * FROM orders_probe WHERE user_id=42 ORDER BY created_at DESC LIMIT 50'
        WHEN 'symbol_orders' THEN 'SELECT * FROM orders_probe WHERE symbol=''SYM42'' ORDER BY created_at DESC LIMIT 50'
        WHEN 'insert_batch' THEN 'INSERT INTO orders_probe SELECT -n,42,42,''WRITE'',now(),1 FROM generate_series(1,100) n'
        WHEN 'balance_update' THEN 'UPDATE balance_probe SET cash=cash-0.01 WHERE id=42'
      END;
      FOR i IN 1..23 LOOP
        EXECUTE 'EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON, TIMING OFF) '||statement INTO p;
        IF i>3 THEN INSERT INTO results VALUES(phase,workload,i-3,(p->0->>'Execution Time')::numeric,p); END IF;
        IF workload='insert_batch' THEN DELETE FROM orders_probe WHERE id<0; END IF;
      END LOOP;
    END LOOP;
  END LOOP;
END $$;
SELECT json_build_object(
  'datasetRows',300000,'repetitions',20,'warmups',3,
  'method','EXPLAIN ANALYZE TIMING OFF; same session; temp tables; writes separated from reads; all rolled back',
  'summary',(SELECT json_agg(t) FROM (
    SELECT phase,workload,percentile_cont(.5) WITHIN GROUP(ORDER BY milliseconds) AS median_ms,
    percentile_cont(.95) WITHIN GROUP(ORDER BY milliseconds) AS p95_ms
    FROM results GROUP BY phase,workload ORDER BY workload,phase
  ) t),
  'samples',(SELECT json_agg(r) FROM results r)
);
ROLLBACK;
