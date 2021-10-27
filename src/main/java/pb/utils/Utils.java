package pb.utils;

import java.util.Timer;
import java.util.TimerTask;

import pb.protocols.ICallback;

//这里使用固定端口号实现

public class Utils {
//	单例模式
	private static Utils utils;
//	默认服务器端口号
	public static final int serverPort = 3100;
	
//	默认服务器主机名
	public static final String serverHost = "localhost";
	
//	主页服务器端口号
	public static final int indexServerPort = 3101;
	
	/**
	 * Chunk size in bytes to use when transferring a file
	 */
//	传输区块大小
	public static final int chunkSize = 16*1024;
	
	/**
	 * Use of a single timer object over the entire system helps
	 * to reduce thread usage.
	 */
	private Timer timer = new Timer();
	
	public Utils() {
		timer=new Timer();
	}
	
	public static synchronized Utils getInstance() {
		if(utils==null) utils=new Utils();
		return utils;
	}

	public void setTimeout(ICallback callback,long delay) {
		// nicely, this is thread safe
		timer.schedule(new TimerTask() {
			@Override
			public void run() {
				callback.callback();
			}
			
		}, delay);
	}

	public void cleanUp() {
		timer.cancel();
		System.gc(); // need to do this to cleanup timer tasks and allow jvm to quit
	}
}
