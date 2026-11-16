-- AI 任务领域只保留在用的需求整理、MCP、运维；行情、资讯、组合、策略、系统、交易
-- 当前没有对应的内置 AI 场景，物理删除字典项，不在下拉中保留。
DELETE FROM dict
WHERE dict_type = 'aiUseCaseDomain'
  AND enum_code IN ('MARKET', 'NEWS', 'PORTFOLIO', 'STRATEGY', 'SYSTEM', 'TRADE');
