package pb;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.codec.binary.Base64;

import pb.managers.ClientManager;
import pb.managers.IOThread;
import pb.managers.PeerManager;
import pb.managers.ServerManager;
import pb.managers.endpoint.Endpoint;
import pb.utils.Utils;

public class FileSharingPeer {
	private static Logger log = Logger.getLogger(FileSharingPeer.class.getName());

	private static final String getFile = "GET_FILE";

	private static final String fileContents = "FILE_CONTENTS";

	private static final String fileError = "FILE_ERROR";

	private static int peerPort=Utils.serverPort; // default port number for this peer's server

	private static int indexServerPort=Utils.indexServerPort; // default port number for index server

	private static String host=Utils.serverHost; // default host for the index server

	private static int chunkSize=Utils.chunkSize;

	private static byte[] buffer = new byte[chunkSize];

	public static void continueTransmittingFile(InputStream in,Endpoint endpoint) {
		try {
			int read = in.read(buffer);
			if(read==-1) {
				endpoint.emit(fileContents, ""); // signals no more bytes in file
				in.close();
			} else {
				endpoint.emit(fileContents, new String(Base64.encodeBase64(
						Arrays.copyOfRange(buffer, 0, read)),
						StandardCharsets.US_ASCII));
				if(read<chunkSize) {
					endpoint.emit(fileContents, "");
					in.close();
				} else {
					Utils.getInstance().setTimeout(()->{
						continueTransmittingFile(in,endpoint);
					},100); // limit throughput to about 160kB/s, hopefully your bandwidth can keep up :-)
				}
			}
		} catch (IOException e) {
			endpoint.emit(fileError,e.toString());
		}
	}

	public static void startTransmittingFile(String filename,Endpoint endpoint) {
		try {
			InputStream in = new FileInputStream(filename);
			continueTransmittingFile(in,endpoint);
		} catch (FileNotFoundException e) {
			endpoint.emit(fileError,e.toString());
		}
	}

	public static void emitIndexUpdate(String peerport,List<String> filenames,Endpoint endpoint,
			ClientManager clientManager) {
		if(filenames.size()==0) {
			clientManager.shutdown(); // no more index updates to do
		} else {
			String filename=filenames.remove(0);
			log.info("Sending index update: "+peerport+":"+filename);
			// an index update has the format: host:port:filename
			endpoint.emit(IndexServer.indexUpdate, peerport+":"+filename);
			Utils.getInstance().setTimeout(()->{
				emitIndexUpdate(peerport,filenames,endpoint,clientManager);
			}, 100); // send 10 index updates per second, this shouldn't kill the bandwidth :-]
		}
	}

	public static void uploadFileList(List<String> filenames,PeerManager peerManager,
			String peerport) throws UnknownHostException, InterruptedException {
		// connect to the index server and tell it the files we are sharing
        ClientManager clientManager = peerManager.connect(indexServerPort, host);
        clientManager.on(PeerManager.peerStarted, (args)->{
			Endpoint endpoint = (Endpoint)args[0];
			System.out.println("Connected to index server: "+endpoint.getOtherEndpointId());
			endpoint.on(IndexServer.indexUpdateError, (args2)->{
				String filename = (String) args2[0];
				System.out.println("Index server did not accept the file: "+filename);
			});
			System.out.println("Telling the index server our peer:port="+peerport);
			endpoint.emit(IndexServer.peerUpdate, peerport);
			System.out.println("Sending file list to the index server.");
			emitIndexUpdate(peerport,filenames,endpoint,clientManager);
		}).on(PeerManager.peerStopped, (args)->{
			Endpoint endpoint = (Endpoint)args[0];
			System.out.println("Disconnected from the index server: "+endpoint.getOtherEndpointId());
		}).on(PeerManager.peerError, (args)->{
			Endpoint endpoint = (Endpoint)args[0];
			System.out.println("There was an error communicating with the index server: "
					+endpoint.getOtherEndpointId());
		});
        clientManager.start();
	}

	private static void shareFiles(String[] files) throws InterruptedException, IOException {
		List<String> filenames=new ArrayList<String>();
		for(String file : files) {
			filenames.add(file);
		}
        PeerManager peerManager = new PeerManager(peerPort);
        peerManager.on(PeerManager.peerStarted, (args)->{
        	Endpoint endpoint = (Endpoint)args[0];
        	System.out.println("Connection from peer: "+endpoint.getOtherEndpointId());
        	endpoint.on(getFile,(args2)->{
        		String filename = (String) args2[0];
        		System.out.println("Peer is requesting file: "+filename);
        		startTransmittingFile(filename,endpoint);
        	});
        }).on(PeerManager.peerStopped,(args)->{
        	Endpoint endpoint = (Endpoint)args[0];
        	System.out.println("Disconnected from peer: "+endpoint.getOtherEndpointId());
        }).on(PeerManager.peerError,(args)->{
        	Endpoint endpoint = (Endpoint)args[0];
        	System.out.println("There was an error communicating with the peer: "
        			+endpoint.getOtherEndpointId());
        }).on(PeerManager.peerServerManager, (args)->{
        	ServerManager serverManager = (ServerManager)args[0];
        	serverManager.on(IOThread.ioThread, (args2)->{
	        	String peerport = (String) args2[0];
	        	try {
					uploadFileList(filenames,peerManager,peerport);
				} catch (UnknownHostException e) {
					System.out.println("The index server host could not be found: "+host);
				} catch (InterruptedException e) {
					System.out.println("Interrupted while trying to send updates to the index server");
				}
	        });
        });
        peerManager.start();
        // just keep sharing until the user presses "return"
        BufferedReader input= new BufferedReader(new InputStreamReader(System.in));
        System.out.println("Press RETURN to stop sharing");
        input.readLine();
        System.out.println("RETURN pressed, stopping the peer");
        peerManager.shutdown();
	}

	private static void getFileFromPeer(PeerManager peerManager,String response) throws InterruptedException {
		// Create a independent client manager (thread) for each download
		// response has the format: PeerIP:PeerPort:filename
		String[] parts=response.split(":",3);
		ClientManager clientManager;
		try {
			clientManager = peerManager.connect(Integer.valueOf(parts[1]),parts[0]);
		} catch (NumberFormatException e) {
			System.out.println("Response from index server is bad, port is not a number: "+parts[1]);
			return;
		} catch (UnknownHostException e) {
			System.out.println("Could not find the peer IP address: "+parts[0]);
			return;
		}
		try {
			OutputStream out = new FileOutputStream(parts[2]);
			clientManager.on(PeerManager.peerStarted, (args)->{
				Endpoint endpoint = (Endpoint)args[0];
				endpoint.on(fileContents,(args2)->{
					String chunk = (String) args2[0];
					if(chunk.length()==0) {
						// file download complete
						try {
							out.close();
						} catch (IOException e) {
							System.out.println("Possible error with downloaded file: "+parts[2]);
						}
						clientManager.shutdown();
					} else {
						try {
							out.write(Base64.decodeBase64(chunk));
						} catch (IOException e) {
							System.out.println("Error writing file chunk: "+chunk);
						}
					}
				}).on(fileError, (args2)->{
					System.out.println("Error downloading file");
					clientManager.shutdown();
				});
				System.out.println("Getting file "+parts[2]+" from "+endpoint.getOtherEndpointId());
				endpoint.emit(getFile, parts[2]);
			}).on(PeerManager.peerStopped, (args)->{
				Endpoint endpoint = (Endpoint)args[0];
				System.out.println("Disconnected from peer: "+endpoint.getOtherEndpointId());
			}).on(PeerManager.peerError, (args)->{
				Endpoint endpoint = (Endpoint)args[0];
				System.out.println("There was error while communication with peer: "
						+endpoint.getOtherEndpointId());
			});
			clientManager.start();
			// we can't call clientManager.join() because the thread that called this method is
			// the endpoint thread from the query to the index server, which needs
			// to continue to process its session, so we can join with this later
		} catch (FileNotFoundException e) {
			System.out.println("Could not create file: "+parts[2]);
		}	
		
	}

	private static void queryFiles(String[] keywords) throws UnknownHostException, InterruptedException {
		String query = String.join(",",keywords);
		// connect to the index server and tell it the files we are sharing
		PeerManager peerManager = new PeerManager(peerPort);
        ClientManager clientManager = peerManager.connect(indexServerPort, host);
        clientManager.on(PeerManager.peerStarted, (args)->{
			Endpoint endpoint = (Endpoint)args[0];
			System.out.println("Connected to index server: "+endpoint.getOtherEndpointId());
			endpoint.on(IndexServer.queryResponse, (args2)->{
				String response = (String) args2[0];
				if(response.length()==0) {
					System.out.println("Received all responses.");
					clientManager.shutdown();
				} else {
					System.out.println("Received query response: "+response);
					try {
						getFileFromPeer(peerManager,response);
					} catch (InterruptedException e) {
						System.out.println("interrupted while trying to download: "+response);
					}
				}
			}).on(IndexServer.queryError, (args2)->{
				System.out.println("Index server did not accept the query: "+query);
				clientManager.shutdown();
			});
			System.out.println("Sending query to the index server.");
			endpoint.emit(IndexServer.queryIndex, query);
		}).on(PeerManager.peerStopped, (args)->{
			Endpoint endpoint = (Endpoint)args[0];
			System.out.println("Disconnected from the index server: "+endpoint.getOtherEndpointId());
		}).on(PeerManager.peerError, (args)->{
			Endpoint endpoint = (Endpoint)args[0];
			System.out.println("There was an error communicating with the index server: "
					+endpoint.getOtherEndpointId());
		});
        clientManager.start();
        clientManager.join(); // wait for the query to finish
        /*
         * We also have to join with any other client managers that were started for
         * download purposes.
         */
        peerManager.joinWithClientManagers();
	}
	
	private static void help(Options options){
		String header = "FileShare Peer\n\n";
		String footer = "\nVersion 1.0";
		HelpFormatter formatter = new HelpFormatter();
		formatter.printHelp("pb.Peer", header, options, footer, true);
		System.exit(-1);
	}
	
	public static void main( String[] args ) throws IOException, InterruptedException
    {
    	// set a nice log format
		System.setProperty("java.util.logging.SimpleFormatter.format",
                "[%1$tl:%1$tM:%1$tS:%1$tL] [%4$s] %2$s: %5$s%n");

    	// parse command line options
        Options options = new Options();
        options.addOption("port",true,"peer server port, an integer");
        options.addOption("host",true,"index server hostname, a string");
        options.addOption("indexServerPort",true,"index server port, an integer");
        Option optionShare = new Option("share",true,"list of files to share");
        optionShare.setArgs(Option.UNLIMITED_VALUES);
        options.addOption(optionShare);
        Option optionQuery = new Option("query",true,"keywords to search for and download files that match");
        optionQuery.setArgs(Option.UNLIMITED_VALUES);
        options.addOption(optionQuery);
        
        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = null;
        try {
			cmd = parser.parse( options, args);
		} catch (ParseException e1) {
			help(options);
		}
        
        if(cmd.hasOption("port")){
        	try{
        		peerPort = Integer.parseInt(cmd.getOptionValue("port"));
			} catch (NumberFormatException e){
				System.out.println("-port requires a port number, parsed: "+
						cmd.getOptionValue("port"));
				help(options);
			}
        }
        
        if(cmd.hasOption("indexServerPort")) {
        	try{
        		indexServerPort = Integer.parseInt(cmd.getOptionValue("indexServerPort"));
			} catch (NumberFormatException e){
				System.out.println("-indexServerPort requires a port number, parsed: "+
						cmd.getOptionValue("indexServerPort"));
				help(options);
			}
        }
        
        if(cmd.hasOption("host")) {
        	host = cmd.getOptionValue("host");
        }
        
        
        // start up the client
        log.info("PB Peer starting up");
 
        if(cmd.hasOption("share")) {
        	String[] files = cmd.getOptionValues("share");
        	shareFiles(files);
        } else if(cmd.hasOption("query")) {
        	String[] keywords = cmd.getOptionValues("query");
        	queryFiles(keywords);
        } else {
        	System.out.println("must use either the -query or -share option");
        	help(options);
        }
        Utils.getInstance().cleanUp();
        log.info("PB Peer stopped");
    }
        
}
