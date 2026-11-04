package cn.aslight.workhub.model.agent;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 授权范围按业务线和环境成对保存，避免两组列表产生额外组合。 */
public record AgentScope(@NotBlank @Size(max = 32) String businessLineCode,
                         @NotBlank @Size(max = 64) String environmentCode) {
}
