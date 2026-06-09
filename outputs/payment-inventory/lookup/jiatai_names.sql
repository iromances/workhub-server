SELECT
  'jiatai' AS source_system,
  oc.org_code AS cooperation_code,
  oc.org_name AS cooperation_name,
  p.no AS project_code,
  p.name AS project_name
FROM jiatai_amp_order.amp_project p
LEFT JOIN jiatai_amp_order.org_cooperation oc ON oc.id = p.cooperation_id
WHERE p.no IN (
  'JT_XXT-PRJ-LYX-A','JT_XXT-PRJ-ZMY-A','JT_XXT-PRJ-JK-E','JT_XXT-PRJ-GM-C'
)
OR oc.org_code IN ('HZF000036','HZF000118','HZF000141','HZF000142')
ORDER BY oc.org_code, p.no;
