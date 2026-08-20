package cn.aslight.workhub.model.system;

public class DictEntity {
    private Long id;
    private String dictType;
    private String dictDesc;
    private String enumCode;
    private String enumDesc;
    private String parentCode;
    private String rootCode;
    public Long getId() { return id; }
    public void setId(Long value) { id = value; }
    public String getDictType() { return dictType; }
    public void setDictType(String value) { dictType = value; }
    public String getDictDesc() { return dictDesc; }
    public void setDictDesc(String value) { dictDesc = value; }
    public String getEnumCode() { return enumCode; }
    public void setEnumCode(String value) { enumCode = value; }
    public String getEnumDesc() { return enumDesc; }
    public void setEnumDesc(String value) { enumDesc = value; }
    public String getParentCode() { return parentCode; }
    public void setParentCode(String value) { parentCode = value; }
    public String getRootCode() { return rootCode; }
    public void setRootCode(String value) { rootCode = value; }
}
