INSERT INTO pre_approved (id, customer_id, status, available_amount) VALUES
    ('PRA-1001', 'USR-10', 'ACTIVE',  1000000),
    ('PRA-1002', 'USR-10', 'BLOCKED',  800000),
    ('PRA-2001', 'USR-20', 'ACTIVE',  2000000)
ON CONFLICT (id) DO NOTHING;