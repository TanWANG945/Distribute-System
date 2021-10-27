package pb.managers;

import java.io.IOException;
import java.net.Socket;
import java.util.HashSet;
import java.util.Set;
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

public class ServerManager extends Manager implements ISessionProtocolHandler,
	IKeepAliveProtocolHandler, IEventProtocolHandler
{
	private static Logger log = Logger.getLogger(ServerManager.class.getName());

	public static final String sessionStarted="SESSION_STARTED";

	public static final String sessionStopped="SESSION_STOPPED";

	public static final String sessionError="SESSION_ERROR";

	public static final String shutdownServer="SERVER_SHUTDOWN";

	public static final String forceShutdownServer="SERVER_FORCE_SHUTDOWN";

	public static final String vaderShutdownServer="SERVER_VADER_SHUTDOWN";

	private IOThread ioThread;

	private final Set<Endpoint> liveEndpoints;
	

	private final int port;

	private volatile boolean forceShutdown=false;
	

	private volatile boolean vaderShutdown=false;

	private String password=null;

	public ServerManager(int port) {
		this.port=port;
		liveEndpoints=new HashSet<>();
		setName("ServerManager"); // name the thread, urgh simple log can't print it :-(
	}
	

	public ServerManager(int port,String password) {
		this.port=port;
		liveEndpoints=new HashSet<>();
		this.password = password;
		setName("ServerManager"); // name the thread, urgh simple log can't print it :-(
	}
	

	public void shutdown() {
		log.info("服务器关闭指令-等待客户端关闭");
		// this will not force existing clients to finish their sessions
		ioThread.shutDown();
	}
	
	public void forceShutdown() { // Skywalker style :-)
		log.warning("服务器强制关闭客户端");
		forceShutdown=true; // this will send session stops to all the clients
		ioThread.shutDown();
	}
	
	public void vaderShutdown() { // Darkside style :-]
		log.warning("服务器立刻关闭");
		vaderShutdown=true; // this will just close all of the endpoints abruptly
		ioThread.shutDown();
	}

	public int numLiveEndpoints() {
		synchronized(liveEndpoints) {
			return liveEndpoints.size();
		}
	}
	
	@Override
	public void run() {
		log.info("启动");
		// when the IO thread terminates, and all endpoints have terminated,
		// then the server will terminate
		try {
			ioThread = new IOThread(port,this);
		} catch (IOException e1) {
			log.severe("不能开启IO线程");
			return;
		}
		
		try {
			// just wait for this thread to terminate
			ioThread.join();
		} catch (InterruptedException e) {
			// just make sure the ioThread is going to terminate
			ioThread.shutDown();
		}
		
		log.info("io thread has joined");
		
		// At this point, there still may be some endpoints that have not
		// terminated, and so the JVM will remain running until they do.
		// However no new endpoints can be created.
		
		// let's create our own list of endpoints that exist at this point
		HashSet<Endpoint> currentEndpoints = new HashSet<>();
		synchronized(liveEndpoints) {
			currentEndpoints = new HashSet<>(liveEndpoints);
		}
		
		// if we want to tell clients to end session
		// it is indeed possible that both may be set true
		if(forceShutdown && !vaderShutdown) {
			// let's send a stop session to existing clients
			currentEndpoints.forEach((endpoint)->{
				SessionProtocol sessionProtocol=(SessionProtocol) endpoint.getProtocol("SessionProtocol");
				if(sessionProtocol!=null)
					sessionProtocol.stopSession();
			});
		}
		
		// in this case we just close the endpoints, which will likely cause
		// abrupt disconnection
		if(vaderShutdown) {
			// let's just close everything
			currentEndpoints.forEach((endpoint)->{
				endpoint.close();
			});
		}
		
		// let's wait for the remaining clients if we can
		while(numLiveEndpoints()>0 && !vaderShutdown) {
			log.warning("still waiting for "+numLiveEndpoints()+" to finish");
			try {
				Thread.sleep(1000); // just wait a little longer
			} catch (InterruptedException e) {
				if(numLiveEndpoints()>0) {
					log.severe("terminating server with "+numLiveEndpoints()+
							" still unfinished");
				}
				break;
			}
			if(vaderShutdown) {
				// maybe we missed some earlier
				synchronized(liveEndpoints) {
					currentEndpoints = new HashSet<>(liveEndpoints);
				}
				currentEndpoints.forEach((endpoint)->{
				    SessionProtocol sp = (SessionProtocol) endpoint.getProtocol("SessionProtocol");
//				    sp.stopSession();
//					忘了当时写这里准备干什么了。。。。
				});
			}
		}
		log.info("terminated");
	}

	public void acceptClient(Socket clientSocket) {
		Endpoint endpoint = new Endpoint(clientSocket,this);
		endpoint.start();
	}

	@Override
	public void endpointReady(Endpoint endpoint) {
		if(vaderShutdown) {
			endpoint.close(); // we'll kill it here
			return;
		}
		synchronized(liveEndpoints) {
			liveEndpoints.add(endpoint);
		}
		
		if(password!=null) {
			// listen for admin client events
			endpoint.on(shutdownServer, (args)->{
				String msg = (String) args[0];
				if(!msg.equals(password)) {
					log.warning("incorrect password given by client: "+endpoint.getOtherEndpointId());
				} else {
					shutdown();
				}
			}).on(forceShutdownServer, (args)->{
				String msg = (String) args[0];
				if(!msg.equals(password)) {
					log.warning("incorrect password given by client: "+endpoint.getOtherEndpointId());
				} else {
					forceShutdown();
				}
			}).on(vaderShutdownServer, (args)->{
				String msg = (String) args[0];
				if(!msg.equals(password)) {
					log.warning("incorrect password given by client: "+endpoint.getOtherEndpointId());
				} else {
					vaderShutdown();
				}
			});
		}
		
		KeepAliveProtocol keepAliveProtocol = new KeepAliveProtocol(endpoint,this);
		try {
			// we need to add it to the endpoint before starting it
			endpoint.handleProtocol(keepAliveProtocol);
			keepAliveProtocol.startAsServer();
		} catch (ProtocolAlreadyRunning e) {
			// emmm... already requested by the client
		}
		SessionProtocol sessionProtocol = new SessionProtocol(endpoint,this);
		try {
			endpoint.handleProtocol(sessionProtocol);
			sessionProtocol.startAsServer();
		} catch (ProtocolAlreadyRunning e) {
			// emmm... already started by the client
		}
	}
	@Override
	public void endpointClosed(Endpoint endpoint) {
		synchronized(liveEndpoints) {
			liveEndpoints.remove(endpoint);
		}
	}

	@Override
	public void sessionStarted(Endpoint endpoint) {
		log.info("session has started with client: "+endpoint.getOtherEndpointId());
		
		if(forceShutdown) {
			// ask the client to stop now
			SessionProtocol sessionProtocol=(SessionProtocol) endpoint.getProtocol("SessionProtocol");
			if(sessionProtocol!=null)
				sessionProtocol.stopSession();
		}
		
		// now start the event protocol
		EventProtocol eventProtocol = new EventProtocol(endpoint,this);
		try {
			endpoint.handleProtocol(eventProtocol);
			eventProtocol.startAsServer();
		} catch (ProtocolAlreadyRunning e) {
			// hmmm... already requested by the client
		}
		
		// the event protocol has started but still no events
		// could have been received at this point
		localEmit(sessionStarted,endpoint);
		
	}

	@Override
	public void sessionStopped(Endpoint endpoint) {
		log.info("session has stopped with client: "+endpoint.getOtherEndpointId());
		
		localEmit(sessionStopped,endpoint);
		
		// we can now signal the client endpoint to close and forget this client
		endpoint.close(); // will stop all remaining protocols
	}

	@Override
	public boolean protocolRequested(Endpoint endpoint, Protocol protocol) {
		// the only protocols in this system are this kind...
		try {
			((IRequestReplyProtocol)protocol).startAsServer();
			endpoint.handleProtocol(protocol);
			return true;
		} catch (ProtocolAlreadyRunning e) {
			// even more weird...
			return true;
		}
		
	}

	
	/*
	 * Everything below here is handling error conditions that could
	 * arise with the client connection. Typically on error we terminate
	 * connection with the client, and we may need to clean up other stuff
	 * as well.
	 */

	@Override
	public void protocolViolation(Endpoint endpoint, Protocol protocol) {
		log.severe("client "+endpoint.getOtherEndpointId()+" violated the protocol "+protocol.getProtocolName());
		localEmit(sessionError,endpoint);
		endpoint.close();
	}

	@Override
	public void endpointDisconnectedAbruptly(Endpoint endpoint) {
		log.severe("client disconnected abruptly "+endpoint.getOtherEndpointId());
		localEmit(sessionError,endpoint);
		endpoint.close();
	}

	@Override
	public void endpointSentInvalidMessage(Endpoint endpoint) {
		log.severe("client sent an invalid message "+endpoint.getOtherEndpointId());
		localEmit(sessionError,endpoint);
		endpoint.close();
	}

	@Override
	public void endpointTimedOut(Endpoint endpoint, Protocol protocol) {
		log.severe("client "+endpoint.getOtherEndpointId()+" has timed out on protocol "+protocol.getProtocolName());
		localEmit(sessionError,endpoint);
		endpoint.close();
	}

	

	
}
