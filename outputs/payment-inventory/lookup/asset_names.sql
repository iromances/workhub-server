SELECT
  'asset' AS source_system,
  oc.org_code AS cooperation_code,
  oc.org_name AS cooperation_name,
  p.no AS project_code,
  p.name AS project_name
FROM amp_order.amp_project p
LEFT JOIN amp_order.org_cooperation oc ON oc.id = p.cooperation_id
WHERE p.no IN (
  'TH-PRJ-LYX-A','TH-PRJ-ZMYC-A',
  'XXT-PRJ-JK-A','XXT-PRJ-JK-C','XXT-PRJ-JK-D','XXT-PRJ-JK-E',
  'XXT-PRJ-GM-A','XXT-PRJ-GM-B','XXT-PRJ-GM-C',
  'XXT-PRJ-DP-A','XXT-PRJ-FD-A','XXT-PRJ-FD-B',
  'XXT-PRJ-WKX-A','XXT-PRJ-WKX-B','XXT-PRJ-ZS-A',
  'XXT-PRJ-XC-A','ZBL-PRJ-XC-A','ZBL-PRJ-XC-B','ZBL-PRJ-XC-C',
  'XXT-PRJ-JDZ-A','XXT-PRJ-JDZ-B',
  'XXT-PRJ-QY-A','XXT-PRJ-QK-A','XXT-PRJ-QY-B'
)
OR oc.org_code IN (
  'HZF000036','HZF000038','HZF000039','HZF000118','HZF000125',
  'HZF000126','HZF000127','HZF000128','HZF000130','HZF000133',
  'HZF000135','HZF000137','HZF000138','HZF000141','HZF000142'
)
ORDER BY oc.org_code, p.no;
