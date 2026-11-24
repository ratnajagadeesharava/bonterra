-- Synthetic seed data for two nonprofits (spec: Seed fixtures). Fictional people and places only.
-- Applied by Database.reset() inside the same transaction that clears every table.
-- The tests assert these exact values (src/test/java/org/communitybridge/service/Fixtures.java).

INSERT INTO nonprofits (id, name) VALUES
    ('np_cb', 'Community Bridge'),
    ('np_hr', 'Harbor Relief');

INSERT INTO staff (id, nonprofit_id, name, role, active) VALUES
    ('stf_cb_case',   'np_cb', 'Priya Sharma', 'CASE_WORKER',     1),
    ('stf_cb_pm',     'np_cb', 'Arjun Patel',  'PROGRAM_MANAGER', 1),
    ('stf_cb_fin',    'np_cb', 'Neha Gupta',   'FINANCE',         1),
    ('stf_cb_former', 'np_cb', 'Rahul Verma',  'CASE_WORKER',     0),
    ('stf_hr_case',   'np_hr', 'Ananya Das',   'CASE_WORKER',     1),
    ('stf_hr_pm',     'np_hr', 'Vikram Singh', 'PROGRAM_MANAGER', 1),
    ('stf_hr_fin',    'np_hr', 'Isha Nair',    'FINANCE',         1);

INSERT INTO participants (id, nonprofit_id, name, household_size, city) VALUES
    ('par_cb_001', 'np_cb', 'Kavya Reddy',    3, 'Hyderabad'),
    ('par_cb_002', 'np_cb', 'Rohan Mehta',    1, 'Warangal'),
    ('par_cb_003', 'np_cb', 'Sneha Iyer',     4, 'Vijayawada'),
    ('par_hr_001', 'np_hr', 'Aditya Rao',     2, 'Chennai'),
    ('par_hr_002', 'np_hr', 'Meghana Pillai', 5, 'Kochi');

-- Amounts in cents: $10,000.00, $500.00, $1,000.00 (closed), $5,000.00.
INSERT INTO funds (id, nonprofit_id, name, status, allocation_cents, committed_cents, paid_cents) VALUES
    ('fnd_cb_housing', 'np_cb', 'Housing Stability',  'ACTIVE', 1000000, 0, 0),
    ('fnd_cb_small',   'np_cb', 'Small Emergencies',  'ACTIVE',   50000, 0, 0),
    ('fnd_cb_closed',  'np_cb', 'Winter Relief 2025', 'CLOSED',  100000, 0, 0),
    ('fnd_hr_utility', 'np_hr', 'Utility Relief',     'ACTIVE',  500000, 0, 0);
