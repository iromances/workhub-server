public class BcmHistoryQueryStandalone {

    public static void main(String[] args) {
        java.net.HttpURLConnection connection = null;
        long startedAt = System.currentTimeMillis();

        try {
            String url = "http://172.16.224.130:8899";
            String corpNo = "0020239537";
            String userNo = "00003";
            String accountNo = "310066771013008315930";

            java.time.LocalDate end = java.time.LocalDate.now().minusDays(1);
            java.time.LocalDate start = end.minusDays(30);
            java.time.format.DateTimeFormatter dayFormatter =
                    java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd");
            String startDate = start.format(dayFormatter);
            String endDate = end.format(dayFormatter);

            java.time.LocalDateTime now = java.time.LocalDateTime.now();
            String requestNo =
                    now.format(java.time.format.DateTimeFormatter.ofPattern("yyMMddHHmmss"))
                            + String.format("%06d", java.util.concurrent.ThreadLocalRandom.current()
                            .nextInt(0, 1_000_000));

            String xml =
                    "<ap>"
                            + "<head>"
                            + "<tr_code>310301</tr_code>"
                            + "<corp_no>" + corpNo + "</corp_no>"
                            + "<user_no>" + userNo + "</user_no>"
                            + "<req_no>" + requestNo + "</req_no>"
                            + "<tr_acdt>" + now.format(dayFormatter) + "</tr_acdt>"
                            + "<tr_time>" + now.format(java.time.format.DateTimeFormatter.ofPattern("HHmmss")) + "</tr_time>"
                            + "<atom_tr_count>1</atom_tr_count>"
                            + "<channel>0</channel>"
                            + "<reserved></reserved>"
                            + "</head>"
                            + "<body>"
                            + "<acno>" + accountNo + "</acno>"
                            + "<start_date>" + startDate + "</start_date>"
                            + "<end_date>" + endDate + "</end_date>"
                            + "</body>"
                            + "</ap>";

            String maskedAccount = accountNo.substring(0, 6)
                    + "***********"
                    + accountNo.substring(accountNo.length() - 4);
            String maskedXml = xml.replace(accountNo, maskedAccount);
            byte[] requestBytes = xml.getBytes(java.nio.charset.Charset.forName("GBK"));

            System.out.println("================ BCM 310301 历史交易查询开始 ================");
            System.out.println("[1/7] 准备请求");
            System.out.println("      请求地址  : " + url);
            System.out.println("      企业号    : " + corpNo);
            System.out.println("      用户号    : " + userNo);
            System.out.println("      查询账号  : " + maskedAccount);
            System.out.println("      查询区间  : " + startDate + " - " + endDate + "（31个自然日）");
            System.out.println("      请求流水号: " + requestNo);
            System.out.println("      交易码    : 310301");
            System.out.println("      请求编码  : GBK");
            System.out.println("      请求字节数: " + requestBytes.length);
            System.out.println("[2/7] 完整请求报文（账号已脱敏）\n" + maskedXml);

            long connectStartedAt = System.currentTimeMillis();
            System.out.println("[3/7] 正在建立 HTTP 连接...");
            connection = (java.net.HttpURLConnection) new java.net.URI(url).toURL().openConnection();
            connection.setRequestMethod("POST");
            connection.setDoInput(true);
            connection.setDoOutput(true);
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(90_000);
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            connection.setRequestProperty("jumpcloud-Env", "BASE");
            connection.setFixedLengthStreamingMode(requestBytes.length);

            System.out.println("[4/7] 正在发送请求报文...");
            try (java.io.OutputStream output = connection.getOutputStream()) {
                output.write(requestBytes);
                output.flush();
            }
            System.out.println("      连接并发送耗时: " + (System.currentTimeMillis() - connectStartedAt) + " ms");

            long responseStartedAt = System.currentTimeMillis();
            System.out.println("[5/7] 等待交行响应...");
            int httpCode = connection.getResponseCode();
            System.out.println("      HTTP状态码: " + httpCode);

            java.io.InputStream input = httpCode >= 400
                    ? connection.getErrorStream()
                    : connection.getInputStream();
            java.io.ByteArrayOutputStream responseBuffer = new java.io.ByteArrayOutputStream();
            if (input != null) {
                try (java.io.InputStream responseInput = input) {
                    byte[] buffer = new byte[8192];
                    int length;
                    while ((length = responseInput.read(buffer)) != -1) {
                        responseBuffer.write(buffer, 0, length);
                    }
                }
            }

            byte[] responseBytes = responseBuffer.toByteArray();
            String responseXml = new String(responseBytes, java.nio.charset.Charset.forName("GBK"));
            System.out.println("[6/7] 响应接收完成");
            System.out.println("      响应耗时  : " + (System.currentTimeMillis() - responseStartedAt) + " ms");
            System.out.println("      响应字节数: " + responseBytes.length);

            String particularCode = "";
            String particularInfo = "";
            String recordNum = "";
            java.util.regex.Matcher codeMatcher = java.util.regex.Pattern
                    .compile("<particular_code>\\s*(.*?)\\s*</particular_code>", java.util.regex.Pattern.DOTALL)
                    .matcher(responseXml);
            if (codeMatcher.find()) {
                particularCode = codeMatcher.group(1);
            }
            java.util.regex.Matcher infoMatcher = java.util.regex.Pattern
                    .compile("<particular_info>\\s*(.*?)\\s*</particular_info>", java.util.regex.Pattern.DOTALL)
                    .matcher(responseXml);
            if (infoMatcher.find()) {
                particularInfo = infoMatcher.group(1);
            }
            java.util.regex.Matcher countMatcher = java.util.regex.Pattern
                    .compile("<record_num>\\s*(.*?)\\s*</record_num>", java.util.regex.Pattern.DOTALL)
                    .matcher(responseXml);
            if (countMatcher.find()) {
                recordNum = countMatcher.group(1);
            }

            System.out.println("      交行返回码: " + particularCode);
            System.out.println("      返回信息  : " + particularInfo);
            System.out.println("      明细条数  : " + recordNum);
            System.out.println("[7/7] 完整响应报文（GBK解码）\n" + responseXml);
            System.out.println("================ BCM 310301 历史交易查询结束 ================");
            System.out.println("总耗时: " + (System.currentTimeMillis() - startedAt) + " ms");
        } catch (Exception e) {
            System.err.println("================ BCM 310301 查询失败 ================");
            System.err.println("已执行耗时: " + (System.currentTimeMillis() - startedAt) + " ms");
            e.printStackTrace(System.err);
        } finally {
            if (connection != null) {
                connection.disconnect();
                System.out.println("HTTP连接已关闭");
            }
        }
    }
}
