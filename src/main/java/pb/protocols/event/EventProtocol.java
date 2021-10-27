package pb.protocols.event;

import java.util.logging.Logger;

import pb.managers.Manager;
import pb.managers.endpoint.Endpoint;
import pb.protocols.IRequestReplyProtocol;
import pb.protocols.Message;
import pb.protocols.Protocol;

public class EventProtocol extends Protocol implements IRequestReplyProtocol {
	private static Logger log = Logger.getLogger(EventProtocol.class.getName());
	
	public static final String protocolName = "EventProtocol";
	
	public int eventTimeout = 40000;
	
	public volatile boolean stopped=false;

	public EventProtocol(Endpoint endpoint, IEventProtocolHandler manager) {
		super(endpoint, (Manager)manager);	
		// Register an event to listen for all events ("*") emitted on this endpoint and
		// send them to the remote end point; making sure thats events have
		// only a String argument
		endpoint.on("*", (args)->{
			String eventName = (String) args[0];
			if(args.length==2 && args[1] instanceof String) {
				String eventData = (String) args[1];
				sendEvent(eventName,eventData);
			} else {
				log.warning("emitted event must have only a single String data argument: "+eventName);
			}			
		});
	}

	public void sendEvent(String eventName, String eventData) {
		if(stopped)return;
		sendRequest(new EventRequest(eventName,eventData));
	}
	
	@Override
	public void stopProtocol() {
		stopped=true;
	}

	@Override
	public void startAsClient() {
	}

	@Override
	public void startAsServer() {
	}

	@Override
	public void sendRequest(Message msg) {
		if(stopped)return;
		endpoint.sendWithTimeout(msg, ()->{
			if(!stopped) manager.endpointTimedOut(endpoint, this);
		}, eventTimeout);
		
	}

	@Override
	public void receiveReply(Message msg) {
		
		
	}

	@Override
	public void receiveRequest(Message msg) {
		if(stopped)return;
		EventRequest eventRequest = (EventRequest)msg;
		endpoint.sendAndCancelTimeout(new EventReply(), msg);
		endpoint.localEmit(eventRequest.getEventName(),eventRequest.getEventData());	
	}

	@Override
	public void sendReply(Message msg)  {
	}
	
	@Override
	public String getProtocolName() {
		return protocolName;
	}

}
