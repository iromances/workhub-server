package cn.aslight.workhub.service.agent;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.function.BiFunction;

@Configuration(proxyBeanMethods=false)
public class AgentBuildTestTools {
    private final AgentBuildTestStore store;
    private final ObjectMapper json;
    public AgentBuildTestTools(AgentBuildTestStore store,ObjectMapper json){this.store=store;this.json=json;}
    @Bean AgentTool agentBuildTestBindingsTool(){return tool("build.test.bindings","读取当前候选的全部必需独立测试 Job 和最近结果。只允许 TEST；不传地址、脚本或任意参数。",true,List.of("changeSetId"),
            (context,args)->Map.of("bindings",store.bindings(context,args.required("changeSetId"))));}
    @Bean AgentTool agentBuildTestStartTool(){return tool(AgentBuildTestStore.START,"为当前已远端交付的候选登记独立构建测试，平台固定准确提交并跟踪实际 Jenkins 回执。未知请求只核对原请求，不改键重发。返回待执行时用 NEEDS_INPUT 等待，完成后平台唤醒原运行。测试通过不等于人工批准发布。",false,List.of("changeSetId","bindingId","requestKey"),
            (context,args)->store.start(context,args.required("changeSetId"),args.required("bindingId"),args.required("requestKey")));}
    @Bean AgentTool agentBuildTestStatusTool(){return tool("build.test.status","查看本次运行独立测试的固定句柄、退出码、实际测试统计和脱敏摘要；Jenkins SUCCESS 或模型报告不能替代实测凭据。",true,List.of("requestId"),
            (context,args)->store.status(context,args.required("requestId")));}
    private AgentTool tool(String code,String description,boolean readOnly,List<String> keys,BiFunction<AgentToolContext,AgentToolArguments,Object> action){
        var properties=new TreeMap<String,Object>();keys.forEach(key->properties.put(key,Map.of("type","string","maxLength",64)));
        var schema=json.valueToTree(Map.of("type","object","properties",properties,"required",keys,"additionalProperties",false));
        return new AgentTool(){
            public String code(){return code;}public String description(){return description;}public JsonNode inputSchema(){return schema;}
            public Set<String> permissions(){return Set.of(readOnly?"agent:code:use":"agent:code:deliver");}public boolean readOnly(){return readOnly;}
            public Object execute(AgentToolContext context,JsonNode arguments){return action.apply(context,new AgentToolArguments(arguments,properties.keySet()));}
        };
    }
}
