package cn.aslight.workhub.model.mcp;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class McpServerConnectionTestRequest {

    private Long id;

    @NotBlank
    private String host;

    @NotNull
    private Integer port;

    @NotBlank
    private String username;

    private String sshPassword;
    private String sshIdentityFile;
    private Boolean sshBastionEnabled;
    private String sshBastionHost;
    private Integer sshBastionPort;
    private String sshBastionUser;
    private String sshBastionPassword;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getSshPassword() {
        return sshPassword;
    }

    public void setSshPassword(String sshPassword) {
        this.sshPassword = sshPassword;
    }

    public String getSshIdentityFile() {
        return sshIdentityFile;
    }

    public void setSshIdentityFile(String sshIdentityFile) {
        this.sshIdentityFile = sshIdentityFile;
    }

    public Boolean getSshBastionEnabled() {
        return sshBastionEnabled;
    }

    public void setSshBastionEnabled(Boolean sshBastionEnabled) {
        this.sshBastionEnabled = sshBastionEnabled;
    }

    public String getSshBastionHost() {
        return sshBastionHost;
    }

    public void setSshBastionHost(String sshBastionHost) {
        this.sshBastionHost = sshBastionHost;
    }

    public Integer getSshBastionPort() {
        return sshBastionPort;
    }

    public void setSshBastionPort(Integer sshBastionPort) {
        this.sshBastionPort = sshBastionPort;
    }

    public String getSshBastionUser() {
        return sshBastionUser;
    }

    public void setSshBastionUser(String sshBastionUser) {
        this.sshBastionUser = sshBastionUser;
    }

    public String getSshBastionPassword() {
        return sshBastionPassword;
    }

    public void setSshBastionPassword(String sshBastionPassword) {
        this.sshBastionPassword = sshBastionPassword;
    }
}
