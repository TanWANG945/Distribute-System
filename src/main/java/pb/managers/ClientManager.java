package pb.managers;


import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.logging.Logger;

import pb.managers.endpoint.Endpoint;
import pb.managers.endpoint.ProtocolAlreadyRunning;
import pb.protocols.IRequestReplyProtocol;
import pb.protocols.Protocol;
import pb.protocols.event.EventProtocol;
import pb.protocols.event.IEventProtocolHandler;
import pb.protocols.keepalive.IKeepAliveProtocolHandler;
import pb.protocols.keepalive.KeepAliveProtocol;
import pb.protocols.session.ISessionProtocolHandler;
import pb.protocols.session.SessionProtocol;


// 实现编辑: Tanner
// 部分内容近期(2021)修改为中文,并消除骚操作
public class ClientManager extends Manager implements ISessionProtocolHandler,
	IKeepAliveProtocolHandler, IEventProtocolHandler
{
	private static Logger log = Logger.getLogger(ClientManager.class.getName());

	public static final String sessionStarted="SESSION_STARTED";

	public static final String sessionStopped="SESSION_STOPPED";

	public static final String sessionError="SESSION_ERROR";

	private SessionProtocol sessionProtocol;

	private Socket socket;

	private String host;

	private int port;

	private boolean shouldWeRetry=false;

	public ClientManager(String host,int port) throws UnknownHostException, InterruptedException {
		this.host=host;
		this.port=port;
	}
	
	@Override
	public void shutdown() {
		sessionProtocol.stopSession();
	}
	
	@Override
	public void run() {
//		线程任务:

		int retries=10;
//		卧槽还可以这样写！
		while(retries-- > 0) {

			if(attemptToConnect(host,port)) {
				// the connection ended in error, so let's just
				// try to get it back up, transparently to the
				// higher layer
				try {
//					gap seconds， 5s
					Thread.sleep(5000); // short pause before retrying
				} catch (InterruptedException e) {
					continue;
				} 
			} else {
				// connection ended cleanly, so we can terminate this manager
				return;
			}
		}

//		log.severe("no more retries, giving up");
		log.severe("十次重连失败");
		
	}

//	判定是否需要重连(判定服务器问题还是网络问题)

	private boolean attemptToConnect(final String host,final int port) {
		shouldWeRetry=false; // may be set to true by another thread
						     // if errors occur on the connection

//		log.info("attempting to connect to "+host+":"+port);

		log.info("尝试连接: "+host+":"+port);
		try {
			socket=new Socket(InetAddress.getByName(host),port);
			Endpoint endpoint = new Endpoint(socket,this);
			endpoint.start();

			try {
				// just wait for this thread to terminate
				endpoint.join();
			} catch (InterruptedException e) {
				// just make sure the endpoint has done everything it should
				endpoint.close();
			}
		} catch (UnknownHostException e) {
			return false; // we wont retry
			// we should retry
		} catch (IOException e1) {
			shouldWeRetry=true;
		} finally {
			if(socket!=null)
//				关掉socket,重建一次
				try {
					socket.close();
				} catch (IOException e) {
					//ignore
				}
		}
		return shouldWeRetry;
	}

	@Override
	public void endpointReady(Endpoint endpoint) {
//		log.info("connection with server established");
		log.info("服务器连接已建立");

		sessionProtocol = new SessionProtocol(endpoint,this);

		try {
			// we need to add it to the endpoint before starting it
			endpoint.handleProtocol(sessionProtocol);
			sessionProtocol.startAsClient();
		} catch (ProtocolAlreadyRunning e) {
			// hmmm, so the server is requesting a session start?
			log.warning("server initiated the session protocol... weird");
		}
		KeepAliveProtocol keepAliveProtocol = new KeepAliveProtocol(endpoint,this);
		try {
			// we need to add it to the endpoint before starting it
			endpoint.handleProtocol(keepAliveProtocol);
			keepAliveProtocol.startAsClient();
		} catch (ProtocolAlreadyRunning e) {
			// hmmm, so the server is requesting a session start?
			log.warning("server initiated the session protocol... weird");
		}
	}
	

	public void endpointClosed(Endpoint endpoint) {
		log.info("connection with server terminated");
	}
	

	@Override
	public void endpointDisconnectedAbruptly(Endpoint endpoint) {
//		log.severe("connection with server terminated abruptly");
		log.severe("服务器连接突然中断");
		localEmit(sessionError,endpoint);
		endpoint.close();
		shouldWeRetry=true;
	}

	@Override
	public void endpointSentInvalidMessage(Endpoint endpoint) {
//		log.severe("server sent an invalid message");
		log.severe("服务器发送无效信息");
		localEmit(sessionError,endpoint);
		endpoint.close();
	}

	@Override
	public void endpointTimedOut(Endpoint endpoint,Protocol protocol) {
//		log.severe("server has timed out");
		log.severe("服务器超时");
		localEmit(sessionError,endpoint);
		endpoint.close();
		shouldWeRetry=true;
	}

	@Override
	public void protocolViolation(Endpoint endpoint,Protocol protocol) {
//		log.severe("protocol with server has been violated: "+protocol.getProtocolName());
		log.severe("协议已经失效: "+protocol.getProtocolName());
		localEmit(sessionError,endpoint);
		endpoint.close();
	}

	@Override
	public void sessionStarted(Endpoint endpoint) {
//		log.info("session has started with server");

		log.info("服务器事务开启");
		
		EventProtocol eventProtocol = new EventProtocol(endpoint,this);
		try {
			endpoint.handleProtocol(eventProtocol);
			eventProtocol.startAsServer();
		} catch (ProtocolAlreadyRunning e) {
			// hmmm... already requested by the client
		}
		
		localEmit(sessionStarted,endpoint);
	}

	@Override
	public void sessionStopped(Endpoint endpoint) {
//		log.info("session has stopped with server");
		log.info("服务器事务已停止");
		localEmit(sessionStopped,endpoint);
		endpoint.close(); // this will stop all the protocols as well
	}

	@Override
	public boolean protocolRequested(Endpoint endpoint, Protocol protocol) {
		// the only protocols in this system are this kind...
		try {
			((IRequestReplyProtocol)protocol).startAsClient();
			endpoint.handleProtocol(protocol);
			return true;
		} catch (ProtocolAlreadyRunning e) {
			// even more weird... should log this too
			return false;
		}
	}
}
