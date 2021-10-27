package pb.protocols.keepalive;

import pb.protocols.Document;
import pb.protocols.InvalidMessage;
import pb.protocols.Message;

public class KeepAliveRequest extends Message {
	static final public String name = "KeepAliveRequest";

	public KeepAliveRequest() {
		super(name,KeepAliveProtocol.protocolName,Message.Type.Request);
	}

	public KeepAliveRequest(Document doc) throws InvalidMessage {
		super(name,KeepAliveProtocol.protocolName,Message.Type.Request,doc); // really just testing the name, otherwise nothing more to test
		this.doc=doc;
	}
	
}
