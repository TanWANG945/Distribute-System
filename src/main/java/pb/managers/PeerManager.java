package pb.managers;

import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

import pb.managers.endpoint.Endpoint;



/**
 * The Peer Manager manages both a number of ClientManagers and a ServerManager.
 * @author aaron
 *
 */
public class PeerManager extends Manager {
	private static Logger log = Logger.getLogger(PeerManager.class.getName());

	public static final String peerStarted = "PEER_STARTED";

	public static final String peerStopped = "PEER_STOPPED";

	public static final String peerError = "PEER_ERROR";

	public static final String peerServerManager = "PEER_SERVER_MANAGER";

	private Set<ClientManager> clientManagers;

	private ServerManager serverManager;

	private int myServerPort;

	public PeerManager(int myServerPort) {
		clientManagers = new HashSet<>();
		this.myServerPort=myServerPort;
	}

	public ServerManager getServerManager() {
		return serverManager;
	}

	public ClientManager connect(int serverPort,String host) throws UnknownHostException, InterruptedException {
		ClientManager clientManager = new ClientManager(host,serverPort);
		clientManagers.add(clientManager);
		clientManager.on(ClientManager.sessionStarted, (args)->{
			Endpoint client = (Endpoint)args[0];
			clientManager.emit(peerStarted, client,clientManager);
		}).on(ClientManager.sessionStopped, (args)->{
			Endpoint client = (Endpoint)args[0];
			clientManager.emit(peerStopped, client,clientManager);
		}).on(ClientManager.sessionError, (args)->{
			Endpoint client = (Endpoint)args[0];
			clientManager.emit(peerError, client,clientManager);
		});
		return clientManager;
	}

	@Override
	public void shutdown() {
		serverManager.shutdown();
		clientManagers.forEach((clientManager)->{
			clientManager.shutdown(); // client manager will send a session stop
		});
	}
	
	@Override
	public void run() {
		// initialize a server manager for other peers to connect to
		serverManager=new ServerManager(myServerPort);
		// setup the callbacks for when another peer connects to this peer
		serverManager.on(ServerManager.sessionStarted, (args)->{
			Endpoint client = (Endpoint)args[0];
			localEmit(peerStarted,client,serverManager);
		}).on(ServerManager.sessionStopped, (args)->{
			Endpoint client = (Endpoint)args[0];
			localEmit(peerStopped,client,serverManager);
		}).on(ServerManager.sessionError, (args)->{
			Endpoint client = (Endpoint)args[0];
			localEmit(peerError,client,serverManager);
		});
		localEmit(peerServerManager,serverManager);
		serverManager.start();
	}

	public void joinWithClientManagers() {
		clientManagers.forEach((clientManager)->{
			try {
				clientManager.join();
			} catch (InterruptedException e) {
				log.warning("could not join with client manager");
			}
		});
	}

}
