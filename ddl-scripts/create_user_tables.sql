
CREATE TABLE IF NOT EXISTS public.btc_hourly_log2 (
    hour BIGINT NOT NULL,
    amount DOUBLE PRECISION NOT NULL,
    PRIMARY KEY (hour));
