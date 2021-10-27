package pb.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import pb.protocols.event.IEventCallback;

public class Eventable extends Thread {
//	用于反射生成日志对象
	private static Logger log = Logger.getLogger(Eventable.class.getName());

	private Map<String,List<IEventCallback>> callbacks;

	public Eventable() {
		callbacks=new HashMap<>();
	}

	public synchronized boolean emit(String eventName, Object... args) {
		boolean hit=false;
//		如果有服务器
		if(callbacks.containsKey("*")) {
			callbacks.get("*").forEach((callback)->{
				// TODO: make this little bit of code more efficient

				Object[] newargs=new Object[args.length+1];
				newargs[0]=eventName;
				for(int i=0;i<args.length;i++) newargs[i+1]=args[i];
				callback.callback(newargs);
			});
			hit=true;
		}
		if(localEmit(eventName,args)) hit=true;

		if(!hit)log.warning("no callbacks for event: "+eventName);
		return hit;
	}

	public synchronized boolean localEmit(String eventName, Object... args) {
		boolean hit=false;
		if(callbacks.containsKey(eventName)) {
			callbacks.get(eventName).forEach((callback)->{
				callback.callback(args);
			});
			hit=true;
		}
		return hit;
	}

//	回调函数处理事件
	public synchronized Eventable on(String eventName, IEventCallback callback) {
		if(!callbacks.containsKey(eventName)) {
			callbacks.put(eventName,new ArrayList<IEventCallback>());
		}
		callbacks.get(eventName).add(callback);
		return this;
	}
}
