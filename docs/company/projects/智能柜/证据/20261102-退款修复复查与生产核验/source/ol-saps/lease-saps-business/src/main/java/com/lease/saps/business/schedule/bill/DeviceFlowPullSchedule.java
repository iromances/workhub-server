package com.lease.saps.business.schedule.bill;

import cn.hutool.json.JSONUtil;
import com.lease.saps.business.DeviceFlowLYBizService;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.lease.saps.business.DeviceFlowYHBizService;
import com.lease.saps.common.exception.SystemException;
import com.lease.saps.common.exception.SystemExceptionEnum;
import com.lease.saps.common.monitor.WeChatBotUtil;
import com.lease.saps.model.vo.schedule.DeviceFlowScheduleReq;
import com.xxl.job.core.biz.model.ReturnT;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.concurrent.CompletableFuture;

@Component
@Slf4j
public class DeviceFlowPullSchedule {

    @Autowired
    private DeviceFlowYHBizService deviceFlowYHBizService;

    @Autowired
    private DeviceFlowLYBizService deviceFlowLYBizService;

    @Autowired
    private WeChatBotUtil weChatBotUtil;

    /**
     * 银河设备流水拉取
     *
     * @param args
     * @return
     * @throws Exception
     */
    @XxlJob("deviceFlowPullSchedule")
    public ReturnT<String> execute(String args) throws Exception {
        try {
            DeviceFlowScheduleReq params = JSON.parseObject(args, DeviceFlowScheduleReq.class);
            if (params == null) {
                throw new SystemException(SystemExceptionEnum.PARAMS_ERROR);
            }
            //默认当前日期
            if (params.getExecuteDate() == null) {
                params.setExecuteDate(LocalDate.now());
            }
            log.info("银河设备流水拉取JOB执行参数：{}", JSONObject.toJSONString(params));
            deviceFlowYHBizService.execute(params);
            log.info("银河设备流水拉取JOB执行结束");
            return ReturnT.SUCCESS;
        } catch (Exception e) {
            log.error("银河设备流水拉取JOB执行失败", e);
            String alarmTime = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            String alarmDetail = String.format(
                "**银河设备流水拉取失败**\n" +
                "错误信息：%s",
                e.getMessage() != null ? e.getMessage() : "未知错误"
            );
            weChatBotUtil.sendAlarm(alarmTime, alarmDetail);
            throw e;
        }
    }

    @XxlJob("replaceBatterySyncSchedule")
    public ReturnT<String> replaceBatterySyncSchedule(String args) throws Exception {
        try {
            if (args != null && !JSONUtil.isJson(args))
                throw new SystemException(SystemExceptionEnum.PARAMS_ERROR);

            DeviceFlowScheduleReq params = JSON.parseObject(args, DeviceFlowScheduleReq.class);
            if (params == null)
                throw new SystemException(SystemExceptionEnum.PARAMS_ERROR);

            if (params.getExecuteDate() == null)
                params.setExecuteDate(LocalDate.now());

            log.info("里易换电设备流水JOB执行参数：{}", JSONObject.toJSONString(params));

            deviceFlowLYBizService.execute(params);

            log.info("里易换电设备流水JOB提交结束");

            return ReturnT.SUCCESS;
        } catch (Exception e) {
            log.error("里易换电设备流水JOB执行失败", e);
            String alarmTime = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            String alarmDetail = String.format(
                "**里易换电设备流水同步失败**\n" +
                "错误信息：%s",
                e.getMessage() != null ? e.getMessage() : "未知错误"
            );
            weChatBotUtil.sendAlarm(alarmTime, alarmDetail);
            throw e;
        }
    }
}



