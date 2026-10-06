-- FixConnect AI: find and remove the old demo/sample data from Supabase
-- Run in Supabase: SQL Editor -> New query -> paste -> Run.
-- Real accounts are NOT touched: only these 11 built-in demo e-mails (and everything linked to them) are removed.

-- STEP 1 (safe, read-only): is there any demo data?  0 rows = nothing to clean, stop here.
SELECT id, full_name, email, role
FROM users
WHERE lower(email) IN ('customer@gmail.com', 'priya@example.com', 'kiran@example.com',
                       'provider@gmail.com', 'arjun@fixconnect.ai', 'vijay@fixconnect.ai', 'ravi@fixconnect.ai',
                       'kirankumar@fixconnect.ai', 'suresh@fixconnect.ai', 'lakshmi@fixconnect.ai', 'mahesh@fixconnect.ai');

-- STEP 2: delete them. Select everything from BEGIN to COMMIT and run it together.
-- If any line fails, nothing is deleted (the whole block is rolled back).
BEGIN;

CREATE TEMP TABLE demo_users ON COMMIT DROP AS
SELECT id FROM users
WHERE lower(email) IN ('customer@gmail.com', 'priya@example.com', 'kiran@example.com',
                       'provider@gmail.com', 'arjun@fixconnect.ai', 'vijay@fixconnect.ai', 'ravi@fixconnect.ai',
                       'kirankumar@fixconnect.ai', 'suresh@fixconnect.ai', 'lakshmi@fixconnect.ai', 'mahesh@fixconnect.ai');

CREATE TEMP TABLE demo_requests ON COMMIT DROP AS
SELECT id FROM service_requests
WHERE customer_id IN (SELECT id FROM demo_users) OR provider_id IN (SELECT id FROM demo_users);

DELETE FROM request_declined_providers WHERE request_id IN (SELECT id FROM demo_requests);
DELETE FROM status_events  WHERE request_id IN (SELECT id FROM demo_requests);
DELETE FROM job_photos     WHERE request_id IN (SELECT id FROM demo_requests) OR uploaded_by IN (SELECT id FROM demo_users);
DELETE FROM reviews        WHERE request_id IN (SELECT id FROM demo_requests) OR customer_id IN (SELECT id FROM demo_users) OR provider_id IN (SELECT id FROM demo_users);
DELETE FROM invoices       WHERE request_id IN (SELECT id FROM demo_requests) OR customer_id IN (SELECT id FROM demo_users) OR provider_id IN (SELECT id FROM demo_users);
DELETE FROM warranties     WHERE request_id IN (SELECT id FROM demo_requests) OR customer_id IN (SELECT id FROM demo_users) OR provider_id IN (SELECT id FROM demo_users);
DELETE FROM service_requests WHERE id IN (SELECT id FROM demo_requests);
DELETE FROM favourites     WHERE customer_id IN (SELECT id FROM demo_users) OR provider_id IN (SELECT id FROM demo_users);
DELETE FROM notifications  WHERE user_id IN (SELECT id FROM demo_users);
DELETE FROM password_reset_tokens WHERE user_id IN (SELECT id FROM demo_users);
DELETE FROM voucher_redemptions   WHERE user_id IN (SELECT id FROM demo_users);
DELETE FROM provider_services     WHERE provider_id IN (SELECT id FROM demo_users);
DELETE FROM provider_profiles     WHERE user_id IN (SELECT id FROM demo_users);
DELETE FROM addresses      WHERE user_id IN (SELECT id FROM demo_users);
DELETE FROM users          WHERE id IN (SELECT id FROM demo_users);

COMMIT;

-- STEP 3: run STEP 1 again - it should now return 0 rows.
