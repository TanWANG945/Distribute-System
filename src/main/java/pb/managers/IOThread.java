package pb.managers;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.logging.Logger;

public class IOThread extends Thread {
	private static Logger log = Logger.getLogger(IOThread.class.getName());
	private ServerSocket serverSocket=null;
	private int port;
	private ServerManager serverManager;

	public static final String ioThread = "IO_THREAD";

	public IOThread(int port, ServerManager serverManager) throws IOException{
		serverSocket = new ServerSocket(port); // let's throw this since its potentially unrecoverable
		this.port=port;
		this.serverManager=serverManager;
		setName("IOThread");
		start();
	}

	public void shutDown() {
		if(serverSocket!=null)
			try {
				serverSocket.close();
			} catch (IOException e) {
				log.warning("exception closing server socket: "+e.getMessage());
			}
		interrupt();
	}

	@Override
	public void run() {
		log.info("listening for connections on port "+port);
		try {
			serverManager.emit(ioThread,InetAddress.getLocalHost().getHostAddress()+":"+port);
		} catch (UnknownHostException e1) {
			log.severe("Could not get address of local host, continuing anyway, assuming 127.0.0.1");
			serverManager.emit(ioThread,"127.0.0.1:"+port);
		}
		while(!isInterrupted() && !serverSocket.isClosed()){
			Socket clientSocket;
			try {
				clientSocket = serverSocket.accept();
				log.info("Received connection from "+clientSocket.getInetAddress());
				serverManager.acceptClient(clientSocket);
			} catch (IOException e) {
				log.warning("exception accepting connection: "+e.getMessage());
			} 
		}
		log.info("IOThread terminating");
		try {
			serverSocket.close();
		} catch (IOException e) {
			log.warning("exception closing server socket: "+e.getMessage());
		}
	}
}
