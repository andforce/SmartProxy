package me.smartproxy.dns;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.DhcpInfo;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.util.Log;
import android.widget.EditText;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class ConfigReader {
	private static final String TAG = "ConfigReader";
	final static Pattern patternConfig = Pattern.compile("^[ ]*([0-9]+.[0-9]+.[0-9]+.[0-9]+) ([^ ]+.*$)");
	final static Pattern patternDNS = Pattern.compile(":[ ]*\\[([0-9]+.[0-9]+.[0-9]+.[0-9]+)\\]");
	final public static Pattern patternRootDomain = Pattern.compile("([^\\.]+\\.[^\\.]+)$");

    // 记录本地host解析表
    public static HashMap<String, String>  domainIpMap = new HashMap<>();
	public static HashMap<String, String>  rootDomainIpMap = new HashMap<>();

	public static List<String> dnsList = new ArrayList<String>();

	public static List<String> initDNS(Context context){
		ConnectivityManager mConnectivity = (ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo info = mConnectivity.getActiveNetworkInfo();

		int netType = info.getType();
		int netSubtype = info.getSubtype();

		if (netType == ConnectivityManager.TYPE_WIFI) {  //WIFI
			Log.d(TAG,"当前通过Wifi 上网");
			getWifiNetInfo(context);
		} else if (netType == ConnectivityManager.TYPE_MOBILE) {   //MOBILE
			Log.d(TAG,"当前通过流量 上网");
			getLocalDNS();
		}
		if(dnsList.isEmpty()){
			dnsList.add("114.114.114.114");
		}
		for(String dns : dnsList){
			Log.d(TAG,"DNS 服务器: " + dns);
		}
		return dnsList;

	}
	public static List<String> getLocalDNS(){
		Process cmdProcess = null;
		BufferedReader reader = null;
		dnsList.clear();
		try {
			String[] cmd = new String[]{"sh","-c","getprop | grep dns"};
			cmdProcess = Runtime.getRuntime().exec(cmd);
			reader = new BufferedReader(new InputStreamReader(cmdProcess.getInputStream()));
			String dnsIP = reader.readLine();
			while(dnsIP != null){
				Matcher matcher = patternDNS.matcher(dnsIP);
				if(matcher.find()){
					dnsList.add(matcher.group(1));
				}
				dnsIP = reader.readLine();
			}
			return dnsList;
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		} finally{
			try {
				reader.close();
			} catch (IOException e) {
			}
			cmdProcess.destroy();
		}
	}
	public static void getWifiNetInfo(Context context){
		dnsList.clear();
		WifiManager wifi = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
		if(wifi  != null){
			DhcpInfo info = wifi.getDhcpInfo();
			String dns = intToIp(info.dns1);
			if(!"0.0.0.0".equals(dns)){
				dnsList.add(dns);
			}
		}
	}

	public static String intToIp(int addr) {
		return  ((addr & 0xFF) + "." +
				((addr >>>= 8) & 0xFF) + "." +
				((addr >>>= 8) & 0xFF) + "." +
				((addr >>>= 8) & 0xFF));
	}

	public static void writeHost(Context context, EditText textHost) {
		BufferedWriter buWriter = null;
		try {
			String path = context.getFilesDir().getAbsolutePath();
			File folder = new File(path);
			File file = new File(folder, "host");
			buWriter = new BufferedWriter(new FileWriter(file, false));
			buWriter.write(textHost.getText().toString());
			buWriter.flush();
		}catch (Exception e){
			e.printStackTrace();
		} finally {
			try {
				buWriter.close();
			} catch (Exception e) {
			}
		}
	}

	public static void writeHost(Context context, String str) {
		BufferedWriter buWriter = null;
		try {
			String path = context.getFilesDir().getAbsolutePath();
			File folder = new File(path);
			File file = new File(folder, "host");
			buWriter = new BufferedWriter(new FileWriter(file, false));
			buWriter.write(str);
			buWriter.flush();
		}catch (Exception e){
			e.printStackTrace();
		} finally {
			try {
				buWriter.close();
			} catch (Exception e) {
			}
		}
	}

	public static void initHost(Context context) {
		BufferedWriter buWriter = null;
		try {
			String path = context.getFilesDir().getAbsolutePath();
			File folder = new File(path);
			File file = new File(folder, "host");
			buWriter = new BufferedWriter(new FileWriter(file, false));
			buWriter.write("127.0.0.1 baidu.com\r\n");
			buWriter.flush();
		}catch (Exception e){
			e.printStackTrace();
		} finally {
			try {
				buWriter.close();
			} catch (Exception e) {
			}
		}
	}
	public static String readHost(Context context) {
		// 先初始化默认值
		BufferedReader buReader = null;
		Log.d(TAG,"----Config init begin...----");
		try {
			String path = context.getFilesDir().getAbsolutePath();
			File folder = new File(path);
			File file = new File(folder, "host");
			buReader = new BufferedReader(new FileReader(file));

            StringBuffer stringBuffer = new StringBuffer();
			String config;
			while ((config = buReader.readLine()) != null) {
				stringBuffer.append(config);
				stringBuffer.append("\r\n");
				//Log.d(TAG,config);
				Matcher matcher = patternConfig.matcher(config);
				if (matcher.find()) {
					//System.setProperty(matcher.group(1), matcher.group(2).trim());
					if(matcher.group(2).startsWith("*.")){
						rootDomainIpMap.put(matcher.group(2).replace("*.","") ,matcher.group(1).trim());
						//System.out.printf("设置查询的地址根目录是%s \r\n", matcher.group(2).replace("*.",""));
					}else{
						domainIpMap.put(matcher.group(2), matcher.group(1).trim());
					}

					System.out.printf("  key-->value:  %s --> %s\r\n", matcher.group(2), matcher.group(1));
				}
			}
			return stringBuffer.toString();

		} catch (IOException e) {
			// e.printStackTrace();
		} finally {
			try {
				buReader.close();
			} catch (Exception e) {
			}
		}
		Log.d(TAG,"----Config ini end...----");
		return "";
	}
}
