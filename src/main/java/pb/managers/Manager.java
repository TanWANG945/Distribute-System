package pb.managers;


import pb.managers.endpoint.Endpoint;
import pb.managers.endpoint.IEndpointHandler;
import pb.protocols.IProtocolHandler;
import pb.protocols.Protocol;
import pb.utils.Eventable;


public class Manager extends Eventable implements IProtocolHandler, IEndpointHandler{

	public void shutdown() {
		
	}

	@Override
	public void endpointReady(Endpoint endpoint) {
		
	}

	@Override
	public void endpointClosed(Endpoint endpoint) {
		
	}

	@Override
	public void endpointDisconnectedAbruptly(Endpoint endpoint) {
		
	}

	@Override
	public void endpointSentInvalidMessage(Endpoint endpoint) {
		
	}

	@Override
	public void endpointTimedOut(Endpoint endpoint,Protocol protocol) {
		
	}

	@Override
	public void protocolViolation(Endpoint endpoint,Protocol protocol) {
		
	}

	@Override
	public boolean protocolRequested(Endpoint endpoint, Protocol protocol) {
		return false;
	}

	


}
