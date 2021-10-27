package pb.protocols.keepalive;

import java.time.Instant;
import java.util.logging.Logger;

import pb.managers.Manager;
import pb.managers.endpoint.Endpoint;
import pb.protocols.Message;
import pb.protocols.Protocol;
import pb.utils.Utils;
import pb.protocols.IRequestReplyProtocol;

public class KeepAliveProtocol extends Protocol implements IRequestReplyProtocol {
	@SuppressWarnings("unused")
	private static Logger log = Logger.getLogger(KeepAliveProtocol.class.getName());

	public static final String protocolName="KeepAliveProtocol";

	private int keepAliveRequestInterval = 20000;

	private int keepAliveTimeout = 40000;

	private volatile long timeReplySeen;

	private volatile long timeRequestSeen;

	private volatile boolean stopped=false;

	private volatile boolean timeout=false; 

	public KeepAliveProtocol(Endpoint endpoint, IKeepAliveProtocolHandler manager) {
		super(endpoint,(Manager)manager);
	}

	@Override
	public String getProtocolName() {
		return protocolName;
	}

	@Override
	public void stopProtocol() {
		stopped=true;
	}

	public void startAsServer() {
		timeRequestSeen = Instant.now().toEpochMilli();
		// set a timeout callback
		Utils.getInstance().setTimeout(()->{
			checkClientTimeout();
		}, keepAliveTimeout);
	}

	public void checkClientTimeout() {
		if(stopped)return;
		long now = Instant.now().toEpochMilli();
		if(now-timeRequestSeen > keepAliveTimeout) {
			// timeout :-(
			manager.endpointTimedOut(endpoint,this);
			stopProtocol();
		} else {
			// set a timeout callback
			Utils.getInstance().setTimeout(()->{
				checkClientTimeout();
			}, keepAliveTimeout);
		}
	}

	public void startAsClient() {
		// assume we saw a reply already
		timeReplySeen = Instant.now().toEpochMilli();
		// send a request straight away
		sendAnotherRequest();	
	}

	public void sendAnotherRequest() {
		if(stopped)return;
		sendRequest(new KeepAliveRequest());
		final long timeSent = Instant.now().toEpochMilli();
		Utils.getInstance().setTimeout(()->{
			sendAnotherRequest();
		}, keepAliveRequestInterval);
		Utils.getInstance().setTimeout(()->{
			checkServerTimeout(timeSent);
		}, keepAliveTimeout);
	}

	public void checkServerTimeout(long timeSent) {
		if(stopped)return;
		if(timeout) {
			manager.endpointTimedOut(endpoint,this);
			stopProtocol();
		} else {
			if(timeReplySeen-timeSent > keepAliveTimeout) {
				//we timed out :-(
				timeout=true;
				manager.endpointDisconnectedAbruptly(endpoint);				
			} 
		}
	}

	@Override
	public void sendRequest(Message msg) {
		KeepAliveRequest keepAliveRequest = (KeepAliveRequest) msg;
		endpoint.send(keepAliveRequest);
	}

	@Override
	public void receiveReply(Message msg) {
		@SuppressWarnings("unused")
		KeepAliveReply keepAliveResponse = (KeepAliveReply) msg;
		timeReplySeen = Instant.now().toEpochMilli();
	}

	@Override
	public void receiveRequest(Message msg) {
		@SuppressWarnings("unused")
		KeepAliveRequest keepAliveRequest = (KeepAliveRequest) msg;
		timeRequestSeen = Instant.now().toEpochMilli();
		sendReply(new KeepAliveReply());
	}

	@Override
	public void sendReply(Message msg) {
		KeepAliveReply keepAliveResponse = (KeepAliveReply) msg;
		endpoint.send(keepAliveResponse);
	}
	
	
}
