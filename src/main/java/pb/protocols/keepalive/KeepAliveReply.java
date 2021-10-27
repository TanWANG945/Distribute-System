package pb.protocols.keepalive;

import pb.protocols.Document;
import pb.protocols.InvalidMessage;
import pb.protocols.Message;

public class KeepAliveReply extends Message {
	static final public String name = "KeepAliveReply";

	public KeepAliveReply() {
		super(name,KeepAliveProtocol.protocolName,Message.Type.Reply);
	}

	public KeepAliveReply(Document doc) throws InvalidMessage {
		super(name,KeepAliveProtocol.protocolName,Message.Type.Reply,doc); // really just testing the name, otherwise nothing more to test
		this.doc=doc;
	}

}
