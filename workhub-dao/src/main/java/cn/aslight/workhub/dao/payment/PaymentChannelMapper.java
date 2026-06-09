package cn.aslight.workhub.dao.payment;

import cn.aslight.workhub.model.payment.PaymentChannelDetailResponse;
import cn.aslight.workhub.model.payment.PaymentChannelEntity;
import cn.aslight.workhub.model.payment.PaymentChannelSummaryResponse;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 支付渠道数据访问接口。
 */
@Mapper
public interface PaymentChannelMapper {

    @Select({
            "<script>",
            "SELECT id,",
            "channel_code AS code,",
            "channel_name AS name,",
            "vendor_name AS vendorName,",
            "status",
            "FROM pay_channel",
            "<where>",
            "<if test='channelId != null'>",
            "AND id = #{channelId}",
            "</if>",
            "<if test='status != null and status != \"\"'>",
            "AND status = #{status}",
            "</if>",
            "</where>",
            "ORDER BY id DESC",
            "</script>"
    })
    List<PaymentChannelSummaryResponse> findAll(@Param("channelId") Long channelId, @Param("status") String status);

    @Select("""
            SELECT id,
                   channel_code AS code,
                   channel_name AS name,
                   vendor_name AS vendorName,
                   status,
                   description,
                   created_at AS createdAt,
                   updated_at AS updatedAt
            FROM pay_channel
            WHERE id = #{id}
            """)
    PaymentChannelDetailResponse findDetailById(Long id);

    @Select("""
            SELECT id,
                   channel_code,
                   channel_name,
                   vendor_name,
                   status,
                   description
            FROM pay_channel
            WHERE id = #{id}
            """)
    PaymentChannelEntity findEntityById(Long id);

    @Select("""
            SELECT id,
                   channel_code,
                   channel_name,
                   vendor_name,
                   status,
                   description
            FROM pay_channel
            WHERE channel_code = #{channelCode}
            """)
    PaymentChannelEntity findEntityByCode(String channelCode);

    @Insert("""
            INSERT INTO pay_channel (
                channel_code,
                channel_name,
                vendor_name,
                status,
                description
            ) VALUES (
                #{channelCode},
                #{channelName},
                #{vendorName},
                #{status},
                #{description}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(PaymentChannelEntity entity);

    @Update("""
            UPDATE pay_channel
            SET channel_code = #{channelCode},
                channel_name = #{channelName},
                vendor_name = #{vendorName},
                status = #{status},
                description = #{description}
            WHERE id = #{id}
            """)
    int update(PaymentChannelEntity entity);
}
