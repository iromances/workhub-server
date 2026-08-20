package cn.aslight.workhub.model.system;

import java.util.List;

public class DictQueryRequest {
    private String dictType;
    private List<String> dictTypeList;
    private List<String> enumCodeList;
    private List<String> neEnumCodeList;
    private String parentCode;
    public String getDictType() { return dictType; }
    public void setDictType(String value) { dictType = value; }
    public List<String> getDictTypeList() { return dictTypeList; }
    public void setDictTypeList(List<String> value) { dictTypeList = value; }
    public List<String> getEnumCodeList() { return enumCodeList; }
    public void setEnumCodeList(List<String> value) { enumCodeList = value; }
    public List<String> getNeEnumCodeList() { return neEnumCodeList; }
    public void setNeEnumCodeList(List<String> value) { neEnumCodeList = value; }
    public String getParentCode() { return parentCode; }
    public void setParentCode(String value) { parentCode = value; }
}
