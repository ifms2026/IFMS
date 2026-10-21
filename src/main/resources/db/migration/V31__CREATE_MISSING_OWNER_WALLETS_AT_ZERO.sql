-- Repair missing structural wallets only. No funds or business transactions are invented.
INSERT INTO wallets(owner_type,owner_id,balance,locked_balance,version,created_at,updated_at)
SELECT 'DEPARTMENT',d.id,0,0,0,now(),now() FROM departments d
WHERE NOT EXISTS(SELECT 1 FROM wallets w WHERE w.owner_type='DEPARTMENT' AND w.owner_id=d.id);
INSERT INTO wallets(owner_type,owner_id,balance,locked_balance,version,created_at,updated_at)
SELECT 'PROJECT',p.id,0,0,0,now(),now() FROM projects p
WHERE NOT EXISTS(SELECT 1 FROM wallets w WHERE w.owner_type='PROJECT' AND w.owner_id=p.id);
INSERT INTO wallets(owner_type,owner_id,balance,locked_balance,version,created_at,updated_at)
SELECT 'USER',u.id,0,0,0,now(),now() FROM users u
WHERE NOT EXISTS(SELECT 1 FROM wallets w WHERE w.owner_type='USER' AND w.owner_id=u.id);
