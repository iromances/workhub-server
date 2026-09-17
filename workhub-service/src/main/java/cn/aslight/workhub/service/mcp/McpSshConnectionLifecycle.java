package cn.aslight.workhub.service.mcp;

import cn.aslight.workhub.mcp.ssh.SshConnectionManager;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

@Component
public class McpSshConnectionLifecycle implements DisposableBean {
    @Override
    public void destroy() {
        SshConnectionManager.closeShared();
    }
}
