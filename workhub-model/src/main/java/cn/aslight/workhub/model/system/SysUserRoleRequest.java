package cn.aslight.workhub.model.system;

import java.util.ArrayList;
import java.util.List;

/**
 * 系统用户角色请求。
 */
public class SysUserRoleRequest {

    private List<Long> roleIds = new ArrayList<>();

    public List<Long> getRoleIds() {
        return roleIds;
    }

    public void setRoleIds(List<Long> roleIds) {
        this.roleIds = roleIds == null ? new ArrayList<>() : roleIds;
    }
}
