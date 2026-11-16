-- 模型厂商下拉只保留在用厂商；通义千问（QWEN）无任何接入配置引用，物理删除字典项。
DELETE FROM dict
WHERE dict_type = 'aiModelProvider'
  AND enum_code = 'QWEN';
