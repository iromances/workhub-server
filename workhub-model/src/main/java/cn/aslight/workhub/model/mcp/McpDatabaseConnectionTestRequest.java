package cn.aslight.workhub.model.mcp;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class McpDatabaseConnectionTestRequest {

    private Long id;

    @NotBlank
    private String host;

    @NotNull
    private Integer port;

    private String databaseSchema;

    @NotBlank
    private String username;

    private String password;
    private Boolean sshBastionEnabled;
    private Long bastionId;

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

    public String getDatabaseSchema() {
        return databaseSchema;
    }

    public void setDatabaseSchema(String databaseSchema) {
        this.databaseSchema = databaseSchema;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Boolean getSshBastionEnabled() {
        return sshBastionEnabled;
    }

    public void setSshBastionEnabled(Boolean sshBastionEnabled) {
        this.sshBastionEnabled = sshBastionEnabled;
    }

    public Long getBastionId() {
        return bastionId;
    }

    public void setBastionId(Long bastionId) {
        this.bastionId = bastionId;
    }
}
