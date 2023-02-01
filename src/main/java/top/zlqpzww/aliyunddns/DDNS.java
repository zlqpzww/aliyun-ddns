package top.zlqpzww.aliyunddns;

import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.IAcsClient;
import com.aliyuncs.alidns.model.v20150109.DescribeDomainRecordsRequest;
import com.aliyuncs.alidns.model.v20150109.DescribeDomainRecordsResponse;
import com.aliyuncs.alidns.model.v20150109.UpdateDomainRecordRequest;
import com.aliyuncs.alidns.model.v20150109.UpdateDomainRecordResponse;
import com.aliyuncs.exceptions.ClientException;
import com.aliyuncs.profile.DefaultProfile;
import com.google.gson.Gson;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 动态域名解析
 */
public class DDNS {

    private String accessKey;
    private String accessSecret;

    /**
     * 获取主域名的所有解析记录列表
     */
    private DescribeDomainRecordsResponse describeDomainRecords(DescribeDomainRecordsRequest request, IAcsClient client){
        try {
            // 调用SDK发送请求
            return client.getAcsResponse(request);
        } catch (ClientException e) {
            e.printStackTrace();
            // 发生调用错误，抛出运行时异常
            throw new RuntimeException();
        }
    }

    /**
     * 获取当前主机公网IP
     */
    private String getCurrentHostIP(){
        // 这里使用jsonip.com第三方接口获取本地IP
        String jsonip = "https://jsonip.com/";
        // 接口返回结果
        String result = "";
        BufferedReader in = null;
        try {
            // 使用HttpURLConnection网络请求第三方接口
            URL url = new URL(jsonip);
            HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setRequestMethod("GET");
            urlConnection.connect();
            in = new BufferedReader(new InputStreamReader(
                    urlConnection.getInputStream()));
            String line;
            while ((line = in.readLine()) != null) {
                result += line;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        // 使用finally块来关闭输入流
        finally {
            try {
                if (in != null) {
                    in.close();
                }
            } catch (Exception e2) {
                e2.printStackTrace();
            }

        }
        // 正则表达式，提取xxx.xxx.xxx.xxx，将IP地址从接口返回结果中提取出来
        String rexp = "(\\d{1,3}\\.){3}\\d{1,3}";
        Pattern pat = Pattern.compile(rexp);
        Matcher mat = pat.matcher(result);
        String res="";
        while (mat.find()) {
            res=mat.group();
            break;
        }
        return res;
    }

    /**
     * 修改解析记录
     */
    private UpdateDomainRecordResponse updateDomainRecord(UpdateDomainRecordRequest request, IAcsClient client){
        try {
            // 调用SDK发送请求
            return client.getAcsResponse(request);
        } catch (ClientException e) {
            e.printStackTrace();
            // 发生调用错误，抛出运行时异常
            throw new RuntimeException();
        }
    }

    private static void log_print(String functionName, Object result) {
        Gson gson = new Gson();
        System.out.println("-------------------------------" + functionName + "-------------------------------");
        System.out.println(gson.toJson(result));
    }

    private static Config getDDNSConfig() throws  ConfigException {
        Properties properties = new Properties();
        File configFile = new File(System.getProperty("user.dir"), "conf/config.properties");
        try (InputStream inputStream = Files.newInputStream(configFile.toPath())) {
            properties.load(inputStream);
            Config config = new Config();

            String domainName = properties.getProperty(Const.KEY_DOMAIN_NAME);
            if (domainName == null || domainName.trim().isEmpty()) {
                throw new ConfigException("missing domainName configuration entry");
            }
            config.setDomainName(domainName);

            String hostName = properties.getProperty(Const.KEY_HOST_NAME);
            if (hostName == null || hostName.trim().isEmpty()) {
                throw new ConfigException("missing hostName configuration entry");
            }
            config.setHostName(hostName);

            String accessKey = properties.getProperty(Const.KEY_ACCESS_KEY);
            if (accessKey == null || accessKey.trim().isEmpty()) {
                throw new ConfigException("missing accessKey config entry");
            }
            config.setAccessKey(accessKey);

            String accessSecret = properties.getProperty(Const.KEY_ACCESS_SECRET);
            if (accessSecret == null || accessSecret.trim().isEmpty()) {
                throw new ConfigException("missing accessSecret config entry");
            }
            config.setAccessSecret(accessSecret);

            return config;
        } catch (IOException e) {
            throw new ConfigException("fail to read config file", e);
        }
    }

    public static void main(String[] args) throws IOException {
        Config ddnsConfig;
        try {
            ddnsConfig = getDDNSConfig();
        } catch (ConfigException e) {
            e.printStackTrace();
            return;
        }
        // 设置鉴权参数，初始化客户端
        DefaultProfile profile = DefaultProfile.getProfile(
                "cn-qingdao",// 地域ID
                ddnsConfig.getAccessKey(),// 您的AccessKey ID
                ddnsConfig.getAccessSecret());// 您的AccessKey Secret
        IAcsClient client = new DefaultAcsClient(profile);

        DDNS ddns = new DDNS();

        // 查询指定二级域名的最新解析记录
        DescribeDomainRecordsRequest describeDomainRecordsRequest = new DescribeDomainRecordsRequest();
        // 主域名
        describeDomainRecordsRequest.setDomainName(ddnsConfig.getDomainName());
        // 主机记录
        describeDomainRecordsRequest.setRRKeyWord(ddnsConfig.getHostName());
        // 解析记录类型
        describeDomainRecordsRequest.setType("A");
        DescribeDomainRecordsResponse describeDomainRecordsResponse = ddns.describeDomainRecords(describeDomainRecordsRequest, client);
        log_print("describeDomainRecords",describeDomainRecordsResponse);

        List<DescribeDomainRecordsResponse.Record> domainRecords = describeDomainRecordsResponse.getDomainRecords();
        // 最新的一条解析记录
        if(domainRecords.size() != 0 ){
            DescribeDomainRecordsResponse.Record record = domainRecords.get(0);
            // 记录ID
            String recordId = record.getRecordId();
            // 记录值
            String recordsValue = record.getValue();
            // 当前主机公网IP
            String currentHostIP = ddns.getCurrentHostIP();
            System.out.println("-------------------------------当前主机公网IP为："+currentHostIP+"-------------------------------");
            if(!currentHostIP.equals(recordsValue)){
                // 修改解析记录
                UpdateDomainRecordRequest updateDomainRecordRequest = new UpdateDomainRecordRequest();
                // 主机记录
                updateDomainRecordRequest.setRR(ddnsConfig.getHostName());
                // 记录ID
                updateDomainRecordRequest.setRecordId(recordId);
                // 将主机记录值改为当前主机IP
                updateDomainRecordRequest.setValue(currentHostIP);
                // 解析记录类型
                updateDomainRecordRequest.setType("A");
                UpdateDomainRecordResponse updateDomainRecordResponse = ddns.updateDomainRecord(updateDomainRecordRequest, client);
                log_print("updateDomainRecord",updateDomainRecordResponse);
            }
        }
    }
}
