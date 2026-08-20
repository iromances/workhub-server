package cn.aslight.workhub.dao.system;

import cn.aslight.workhub.model.system.DictQueryRequest;
import cn.aslight.workhub.model.system.DictResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface DictMapper {
    @Select({"<script>",
            "SELECT id, dict_type AS type, dict_desc AS `desc`, enum_code AS code, enum_desc AS name FROM dict",
            "<where> is_delete = 0",
            "<if test='dictType != null and dictType != \"\"'> AND dict_type = #{dictType}</if>",
            "<if test='parentCode != null and parentCode != \"\"'> AND parent_code = #{parentCode}</if>",
            "<if test='dictTypeList != null and dictTypeList.size > 0'> AND dict_type IN <foreach collection='dictTypeList' item='v' open='(' close=')' separator=','>#{v}</foreach></if>",
            "<if test='enumCodeList != null and enumCodeList.size > 0'> AND enum_code IN <foreach collection='enumCodeList' item='v' open='(' close=')' separator=','>#{v}</foreach></if>",
            "<if test='neEnumCodeList != null and neEnumCodeList.size > 0'> AND enum_code NOT IN <foreach collection='neEnumCodeList' item='v' open='(' close=')' separator=','>#{v}</foreach></if>",
            "</where> ORDER BY dict_type, sort_order, id", "</script>"})
    List<DictResponse> query(DictQueryRequest request);
}
