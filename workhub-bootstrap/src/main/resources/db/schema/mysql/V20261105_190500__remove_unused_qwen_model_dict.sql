-- 模型字典随厂商清理同步移除通义千问模型项；无任何接入配置或 AI 任务引用，物理删除。
DELETE FROM dict
WHERE dict_type = 'aiModel'
  AND enum_code = 'qwen/qwen-plus';
