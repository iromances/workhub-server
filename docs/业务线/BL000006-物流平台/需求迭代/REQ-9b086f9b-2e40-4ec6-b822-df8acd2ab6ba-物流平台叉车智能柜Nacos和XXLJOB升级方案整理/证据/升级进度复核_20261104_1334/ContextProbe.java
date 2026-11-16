import com.xxl.job.core.context.*;
public class ContextProbe {
 public static String observed;
 public void job(){observed=XxlJobHelper.getJobParam();}
 public static void main(String[] args)throws Exception{
  String data="[\"TEST-ONLY\"]";
  XxlJobContext.setXxlJobContext(null);
  ContextProbe.class.getMethod("job").invoke(new ContextProbe());
  if(observed!=null)throw new AssertionError();
  System.out.println("PASS: zero-arg reflection succeeds but request data is absent without XxlJobContext");
  XxlJobContext.setXxlJobContext(new XxlJobContext(1L,data,null,0,1));
  ContextProbe.class.getMethod("job").invoke(new ContextProbe());
  if(!data.equals(observed))throw new AssertionError();
  XxlJobHelper.handleFail("synthetic failure");
  if(XxlJobContext.getXxlJobContext().getHandleCode()!=500)throw new AssertionError();
  System.out.println("PASS: official context supplies parameter; handleFail marks code 500");
  XxlJobContext.setXxlJobContext(null);
 }
}