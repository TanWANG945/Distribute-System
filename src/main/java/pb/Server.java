package pb;

import java.io.IOException;
import java.util.logging.Logger;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import pb.managers.ServerManager;
import pb.utils.Utils;

/**
 * Server main. Parse command line options and provide default values.
 * 
 * @see {@link pb.managers.ServerManager}
 * @see {@link pb.utils.Utils}
 * @author aaron
 *
 */
public class Server {
	private static Logger log = Logger.getLogger(Server.class.getName());
	private static int port=Utils.serverPort; // default port number for the server
	

	private static void help(Options options){
//		String header = "PB Server for Unimelb COMP90015\n\n";
		String header = "PB 服务器 Tanner个人分支\n\n";

		String footer = "\n改了再改版";

		HelpFormatter formatter = new HelpFormatter();
//		formatter.printHelp("pb.Server", header, options, footer, true);
		formatter.printHelp("Server 服务器", header,options,footer);
		System.exit(-1);
	}
	
	public static void main( String[] args ) throws IOException
    {
    	// set a nice log format
		System.setProperty("java.util.logging.SimpleFormatter.format",
                "[%1$tl:%1$tM:%1$tS:%1$tL] %2$s %4$s: %5$s%n");
        
    	// parse command line options
        Options options = new Options();
        options.addOption("port",true,"服务器端口号，整数");
        
       
        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = null;
        try {
			cmd = parser.parse( options, args);
		} catch (ParseException e1) {
			help(options);
		}
        
        if(cmd.hasOption("port")){
        	try{
        		port = Integer.parseInt(cmd.getOptionValue("port"));
			} catch (NumberFormatException e){
				System.out.println("-port 后跟端口号, parsed: "+cmd.getOptionValue("port"));
				help(options);
			}
        }
        
        
        // start up the server
        log.info("PB Server启动");
        
        // the server manager will start an io thread and this will prevent
        // the JVM from terminating
        ServerManager serverManager = new ServerManager(port);
        serverManager.start();
        // The simple server does not do any application logic, but will
        // (when you have implemented it in the ServerManager class)
        // just continue to run until it is terminated by ctrl+C, is killed
        // by some other OS signal, or an "admin" client connects and sends 
        // a "SERVER_SHUTDOWN" or "SERVER_FORCE_SHUTDOWN" or if really needed ...
        // "SERVER_VADER_SHUTDOWN" event to the server, over the event protocol. 
        // See AdminClient.java for more info on what is expected.
        
        // the very last thing to do
        Utils.getInstance().cleanUp();
        
    }
}
