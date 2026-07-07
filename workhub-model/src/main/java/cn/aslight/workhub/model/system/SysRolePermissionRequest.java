package cn.aslight.workhub.model.system;

import java.util.ArrayList;
import java.util.List;

/**
 * 系统角色权限请求。
 */
public class SysRolePermissionRequest {

    private List<String> permissionCodes = new ArrayList<>();

    public List<String> getPermissionCodes() {
        return permissionCodes;
    }

    public void setPermissionCodes(List<String> permissionCodes) {
        this.permissionCodes = permissionCodes == null ? new ArrayList<>() : permissionCodes;
    }
}
