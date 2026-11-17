-- CHAR(3) maps awkwardly to Hibernate's schema validation; VARCHAR(3) is equivalent for ISO codes.
ALTER TABLE portfolios ALTER COLUMN base_currency TYPE VARCHAR(3);
ALTER TABLE assets     ALTER COLUMN currency      TYPE VARCHAR(3);
