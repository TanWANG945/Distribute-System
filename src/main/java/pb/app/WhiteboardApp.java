package pb.app;

import org.apache.commons.io.filefilter.TrueFileFilter;
import pb.WhiteboardServer;
import pb.managers.ClientManager;
import pb.managers.PeerManager;
import pb.managers.endpoint.Endpoint;
import pb.protocols.session.SessionProtocol;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.*;
import java.util.logging.Logger;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import static java.lang.Thread.getAllStackTraces;
import static java.lang.Thread.onSpinWait;
import static pb.managers.PeerManager.peerServerManager;


/**
 * Initial code obtained from:
 * https://www.ssaurel.com/blog/learn-how-to-make-a-swing-painting-and-drawing-application/
 */
public class WhiteboardApp {
	private static Logger log = Logger.getLogger(WhiteboardApp.class.getName());
	
	/**
	 * Emitted to another peer to subscribe to updates for the given board. Argument
	 * must have format "host:port:boardid".
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String listenBoard = "BOARD_LISTEN";

	/**
	 * Emitted to another peer to unsubscribe to updates for the given board.
	 * Argument must have format "host:port:boardid".
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String unlistenBoard = "BOARD_UNLISTEN";

	/**
	 * Emitted to another peer to get the entire board data for a given board.
	 * Argument must have format "host:port:boardid".
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String getBoardData = "GET_BOARD_DATA";

	/**
	 * Emitted to another peer to give the entire board data for a given board.
	 * Argument must have format "host:port:boardid%version%PATHS".
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String boardData = "BOARD_DATA";

	/**
	 * Emitted to another peer to add a path to a board managed by that peer.
	 * Argument must have format "host:port:boardid%version%PATH". The numeric value
	 * of version must be equal to the version of the board without the PATH added,
	 * i.e. the current version of the board.
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String boardPathUpdate = "BOARD_PATH_UPDATE";

	/**
	 * Emitted to another peer to indicate a new path has been accepted. Argument
	 * must have format "host:port:boardid%version%PATH". The numeric value of
	 * version must be equal to the version of the board without the PATH added,
	 * i.e. the current version of the board.
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String boardPathAccepted = "BOARD_PATH_ACCEPTED";

	/**
	 * Emitted to another peer to remove the last path on a board managed by that
	 * peer. Argument must have format "host:port:boardid%version%". The numeric
	 * value of version must be equal to the version of the board without the undo
	 * applied, i.e. the current version of the board.
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String boardUndoUpdate = "BOARD_UNDO_UPDATE";

	/**
	 * Emitted to another peer to indicate an undo has been accepted. Argument must
	 * have format "host:port:boardid%version%". The numeric value of version must
	 * be equal to the version of the board without the undo applied, i.e. the
	 * current version of the board.
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String boardUndoAccepted = "BOARD_UNDO_ACCEPTED";

	/**
	 * Emitted to another peer to clear a board managed by that peer. Argument must
	 * have format "host:port:boardid%version%". The numeric value of version must
	 * be equal to the version of the board without the clear applied, i.e. the
	 * current version of the board.
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String boardClearUpdate = "BOARD_CLEAR_UPDATE";

	/**
	 * Emitted to another peer to indicate an clear has been accepted. Argument must
	 * have format "host:port:boardid%version%". The numeric value of version must
	 * be equal to the version of the board without the clear applied, i.e. the
	 * current version of the board.
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String boardClearAccepted = "BOARD_CLEAR_ACCEPTED";

	/**
	 * Emitted to another peer to indicate a board no longer exists and should be
	 * deleted. Argument must have format "host:port:boardid".
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String boardDeleted = "BOARD_DELETED";

	/**
	 * Emitted to another peer to indicate an error has occurred.
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String boardError = "BOARD_ERROR";


	
	/**
	 * White board map from board name to board object 
	 */
	Map<String,Whiteboard> whiteboards;
	
	/**
	 * The currently selected white board
	 */
	Whiteboard selectedBoard = null;

    public static PeerManager peerManager;

    private static List<Endpoint> peerendpointList=new ArrayList<Endpoint>();

    private static List<Endpoint> connectEndpoints=new ArrayList<Endpoint>();




	/**
	 * The peer:port string of the peer. This is synonomous with IP:port, host:port,
	 * etc. where it may appear in comments.
	 */
	String peerport="standalone"; // a default value for the non-distributed version
	
	/*
	 * GUI objects, you probably don't need to modify these things... you don't
	 * need to modify these things... don't modify these things [LOTR reference?].
	 */
	
	JButton clearBtn, blackBtn, redBtn, createBoardBtn, deleteBoardBtn, undoBtn;
	JCheckBox sharedCheckbox ;
	DrawArea drawArea;
	JComboBox<String> boardComboBox;
	boolean modifyingComboBox=false;
	boolean modifyingCheckBox=false;


    /******
     *
     * Utility methods to extract fields from argument strings.
     *
     ******/

    /**
     *
     * @param data = peer:port:boardid%version%PATHS
     * @return peer:port:boardid
     */
    public static String getBoardName(String data) {
        String[] parts=data.split("%",2);
        return parts[0];
    }

    /**
     *
     * @param data = peer:port:boardid%version%PATHS
     * @return boardid%version%PATHS
     */
    public static String getBoardIdAndData(String data) {
        String[] parts=data.split(":");
        return parts[2];
    }

    /**
     *
     * @param data = peer:port:boardid%version%PATHS
     * @return version%PATHS
     */
    public static String getBoardData(String data) {
        String[] parts=data.split("%",2);
        return parts[1];
    }

    /**
     *
     * @param data = peer:port:boardid%version%PATHS
     * @return version
     */
    public static long getBoardVersion(String data) {
        String[] parts=data.split("%",3);
        return Long.parseLong(parts[1]);
    }

    /**
     *
     * @param data = peer:port:boardid%version%PATHS
     * @return PATHS
     */
    public static String getBoardPaths(String data) {
        String[] parts=data.split("%",3);
        return parts[2];
    }

    /**
     *
     * @param data = peer:port:boardid%version%PATHS
     * @return peer
     */
    public static String getIP(String data) {
        String[] parts=data.split(":");
        return parts[0];
    }

    /**
     *
     * @param data = peer:port:boardid%version%PATHS
     * @return port
     */
    public static int getPort(String data) {
        String[] parts=data.split(":");
        return Integer.parseInt(parts[1]);
    }

    public static String getAddress(String data) {
        String[] parts=data.split(":");

        return parts[0]+":"+parts[1];
    }


    private static String serverhost;


    Map<String,Endpoint> endpointHashMap;

    /******
     *
     * Methods called from events.
     *
     ******/

    /**
	 * Initialize the white board app.
	 */
	public WhiteboardApp(int port,String whiteboardServerHost,
			int whiteboardServerPort) {
		whiteboards=new HashMap<>();
        endpointHashMap = new HashMap<>();
        String localport = String.valueOf(port);
        try {
            serverhost = InetAddress.getByName(whiteboardServerHost).toString();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
        peerManager = new PeerManager(port);

        peerport = serverhost.split("/",2)[1]+":"+localport;


        show(peerport);
        sharing_boards();
		server_connection(port,whiteboardServerHost,whiteboardServerPort,peerManager);


    }
	

	// From whiteboard server
	// From whiteboard peer
    public void sharing_boards(){
        peerManager.on(PeerManager.peerStarted, (args)->{
            Endpoint endpoint = (Endpoint)args[0];
            peerendpointList.add(endpoint);
            connectEndpoints.add(endpoint);


            // format: host:port:boardid"
            endpoint.on(listenBoard,listenargs->{

                String requiredatas = (String) listenargs[0];
                String [] parts =requiredatas.split(":",3);
            //  host:port
                String HostPort = getAddress(requiredatas);

                if ((whiteboards.containsKey(requiredatas))&&(HostPort.equals(peerport))) {
                    Whiteboard requiredboard = whiteboards.get(requiredatas);
                    requiredboard.beListenedEndpoints.add(endpoint);
                }
            });

            endpoint.on(getBoardData,boardargs->{
                    // format: host:port:boardid"
                String nowboardname = (String) boardargs[0];
                String nowHostPort = getAddress(nowboardname);

                if ((whiteboards.containsKey(nowboardname)&&nowHostPort.equals(peerport))) {
                    Whiteboard requiredboard = whiteboards.get(nowboardname);
                    String requiredata = requiredboard.toString();
                    //send the board data and version
                    endpoint.emit(boardData,requiredata);
                }
                });

            //"host:port:boardid%version%PATH"

            endpoint.on(boardPathUpdate, updatedpath -> {

                String info =(String) updatedpath[0];
                String bid = getBoardName(info);
                String nowAddress = getAddress(info);

                if (whiteboards.containsKey(bid)&&(nowAddress.equals(peerport))){
                    Whiteboard requiredWB = whiteboards.get(bid);
                    WhiteboardPath wbp = new WhiteboardPath(getBoardPaths(info));

                    if(requiredWB.addPath(wbp,getBoardVersion(info))){
                        // sent every peers the board data
                        for (Endpoint endpoint1:requiredWB.beListenedEndpoints){
                            endpoint1.emit(boardPathUpdate, updatedpath);
                        }
                        log.info("Receive updated path: "+info.split("%",2)[1]);
                        log.info("Receive updated version: "+info.split("%",2)[0]);
                        drawSelectedWhiteboard();
                }
                }


//          "host:port:boardid%version%"
            }).on(boardUndoUpdate, boardUndoUpdateArgs -> {
                String info =(String) boardUndoUpdateArgs[0];
                String bid = getBoardName(info);
                String nowAddress = getAddress(info);
                log.info("Receive boardUndoUpdate: " +info+nowAddress);
                if (whiteboards.containsKey(bid)&&nowAddress.equals(peerport)){
                    Whiteboard requiredWB = whiteboards.get(bid);

                    if(requiredWB.undo(getBoardVersion(info))){
                        // sent every peers the board data
                        for (Endpoint endpoint1:requiredWB.beListenedEndpoints){
                            endpoint1.emit(boardUndoUpdate, boardUndoUpdateArgs);
                        }
                        log.info("Received and Accepted Undo Action");
                    }
                    drawSelectedWhiteboard();
                }

//          "host:port:boardid%version%"
            }).on(boardClearUpdate, boardClearUpdateArgs -> {
                String info =(String) boardClearUpdateArgs[0];
                String bid = getBoardName(info);
                String nowAddress = getAddress(info);

                if (whiteboards.containsKey(bid)&&nowAddress.equals(peerport)){
                    Whiteboard requiredWB = whiteboards.get(bid);
                    WhiteboardPath wbp = new WhiteboardPath(getBoardPaths(info));

                    if(requiredWB.clear(getBoardVersion(info))){
                        // sent every peers the board data
                        for (Endpoint endpoint1:requiredWB.beListenedEndpoints){
                            endpoint1.emit(boardClearUpdate, boardClearUpdateArgs);
                        }
                        log.info("Received and Accepted Clear Action");
                    }
                    drawSelectedWhiteboard();
                }


            }).on(boardDeleted, boardDeleted -> {


            }).on(boardError, boardError -> {

//          "host:port:boardid"
            }).on(unlistenBoard,unlistenBoardArgs->{

                String unlistenedWB = (String) unlistenBoardArgs[0];

                whiteboards.get(unlistenedWB).beListenedEndpoints.remove(endpoint);
            });
//          "host:port:boardid%version%PATH"

            peerManager.on(boardPathUpdate,boardPathUpdateargs -> {

                log.info("Server Send local path update to each listeners");

                String info = (String) boardPathUpdateargs[0];
                String bid = getBoardName(info);
                Whiteboard updateBoard = whiteboards.get(bid);
                for (Endpoint endpoint1: updateBoard.beListenedEndpoints){

                    endpoint1.emit(boardPathUpdate, boardPathUpdateargs);
                }

            }).on(boardUndoUpdate,boardUndoargs1 -> {

                log.info("Server Send local Undo update to each listeners");

                String info = (String) boardUndoargs1[0];
                String bid = getBoardName(info);
                Whiteboard updateBoard = whiteboards.get(bid);
                for (Endpoint endpoint1: updateBoard.beListenedEndpoints){

                    endpoint1.emit(boardUndoUpdate, boardUndoargs1);

                }

            }).on(boardClearUpdate,boardClearargs1 -> {
                log.info("Server Send local Clear update to each listeners");

                String info = (String) boardClearargs1[0];
                String bid = getBoardName(info);
                Whiteboard updateBoard = whiteboards.get(bid);
                for (Endpoint endpoint1: updateBoard.beListenedEndpoints){

                    endpoint.emit(boardClearUpdate, boardClearargs1);
                }
            });

            System.out.println("Connection from peer: "+endpoint.getOtherEndpointId());

            //Listen on peerStoped

        }).on(PeerManager.peerStopped,(args)->{
            Endpoint endpoint = (Endpoint) args[0];
            System.out.println("Disconnected from peer: "+endpoint.getOtherEndpointId());
            whiteboards.values().forEach((whiteboard)->{
                whiteboard.beListenedEndpoints.remove(endpoint);
            });

            connectEndpoints.remove(endpoint);

            }).on(PeerManager.peerError,(args)->{
            Endpoint endpoint = (Endpoint)args[0];

            whiteboards.values().forEach((whiteboard)->{
                whiteboard.beListenedEndpoints.remove(endpoint);
            });

            System.out.println("There was error while communication with peer: "
                    +endpoint.getOtherEndpointId());

            endpoint.emit(WhiteboardServer.error,"Error to connect");
            });

        }

    public void server_connection(int peerPort, String whiteboardServerHost, int whiteboardServerPort, PeerManager peerManager) {

        peerManager.on(peerServerManager,args -> {
            log.info("Peer manager started");
        });
        peerManager.start();
        try {
            ClientManager serverClientManager;
            serverClientManager = peerManager.connect(whiteboardServerPort, whiteboardServerHost);
            serverClientManager.on(PeerManager.peerStarted, (peerStarted)->{
                Endpoint endpoint = (Endpoint)peerStarted[0];

                connectEndpoints.add(endpoint);

                peerManager.on(WhiteboardServer.shareBoard,shareArgs -> {

                    //send msg to endpoint

                    endpoint.emit(WhiteboardServer.shareBoard,shareArgs);

                }).on(WhiteboardServer.unshareBoard,unshareArgs -> {

                    endpoint.emit(WhiteboardServer.unshareBoard,unshareArgs);
                });

                // Receive the sharingBoard msg
                endpoint.on(WhiteboardServer.sharingBoard,sharingargs ->{
                    String boardname = (String) sharingargs[0];

                    log.info("Receive the sharingBoard from:" +boardname);

                    receiveSharing(boardname);

                }).on(WhiteboardServer.unsharingBoard,unsharingargs->{

                    String boardname = (String) unsharingargs[0];

                    log.info("Receive the unsharingBoard from:" +boardname);

                    receiveunSharing(boardname);

                });


            }).on(PeerManager.peerStopped, (peerStopped)->{
                Endpoint endpoint = (Endpoint)peerStopped[0];
                connectEndpoints.remove(endpoint);
                System.out.println("Disconnected from peer: "+endpoint.getOtherEndpointId());
            }).on(PeerManager.peerError, (peerError)->{
                Endpoint endpoint = (Endpoint)peerError[0];
                connectEndpoints.remove(endpoint);
                System.out.println("There was error while communication with peer: "
                        +endpoint.getOtherEndpointId());
                endpoint.emit(WhiteboardServer.error,"Error to connect");
            });
            serverClientManager.start();

            serverClientManager.join();

            peerManager.joinWithClientManagers();
        } catch (InterruptedException e) {
            log.warning("InterruptedException");
        } catch (UnknownHostException e) {
            log.warning("UnknownHostException");
        }


    }
    public void receiveunSharing(String boardname) {


        if (whiteboards.containsKey(boardname)) {
            deleteBoard(boardname);
            whiteboards.remove(boardname);
            //send unshare msg to localpeer
            peerManager.emit("Unshare:"+boardname,"close the connect");
        }
    }

    public void receiveSharing(String boardname){
            // format: peer:port:boardid
            String handleIp_Port = getAddress(boardname);
            if (!whiteboards.containsKey(boardname)) {

                Whiteboard showedWhitedboard = new Whiteboard(boardname,true);
                // add board to board list
                addBoard(showedWhitedboard,false);

                peerManager.on(listenBoard+boardname,nameargs -> {
                    String names =  (String) nameargs[0];
                    String ipport = getAddress(names);

                    // if the selected name of shared board is in the board list, grt the board data
                    if (boardname.equals(names)&&(!endpointHashMap.containsKey(names))){
                    try {
                        ClientManager clientManager;
                        String sharehost = getIP(boardname);

                        int shareport = getPort(boardname);

                        clientManager = peerManager.connect(shareport, sharehost);
                        log.info("Connect successfully");

                        clientManager.on(PeerManager.peerStarted, (startconnect) -> {
                            log.info("Connected to:" + sharehost+":"+shareport);
                            Endpoint endpoint = (Endpoint) startconnect[0];
                            endpointHashMap.put(names,endpoint);
                            connectEndpoints.add(endpoint);

                            endpoint.on(boardData, pathdatas -> {
                                String info = (String) pathdatas[0];
                                String updatename = getBoardName(info);
                                String boardpaths = getBoardData(info);

                                if (boardname.equals(updatename)){
                                    //"host:port:boardid%version%PATHS"
                                    showedWhitedboard.whiteboardFromString(updatename,boardpaths);
                                    drawSelectedWhiteboard();
                                }

                            }).on(boardPathUpdate, updatedpath -> {

                                String info =(String) updatedpath[0];
                                String updatename = getBoardName(info);

                                if (boardname.equals(updatename)){
                                    //"host:port:boardid%version%PATHS"
                                    WhiteboardPath wbp = new WhiteboardPath(getBoardPaths(info));

                                    log.info("Receive updated path:"+info.split("%",2)[1]);
                                    log.info("Receive updated version:"+info.split("%",2)[0]);

                                    //"host:port:boardid%version%PATH"
                                    if(showedWhitedboard.addPath(wbp,getBoardVersion(info))) {
                                        log.info("OnBoardPath Updated: " + getBoardPaths(info));
                                        drawSelectedWhiteboard();
                                    }
                                }




//                           host:port:boardid%version%
                            }).on(boardUndoUpdate, boardUndoUpdate -> {
                                String info =(String) boardUndoUpdate[0];

                                String updatename = getBoardName(info);

                                if (boardname.equals(updatename)){
                                    //"host:port:boardid%version%PATHS"
                                    if (showedWhitedboard.undo(getBoardVersion(info))) {
                                        drawSelectedWhiteboard();
                                    }
                                }



//                           host:port:boardid%version%
                            }).on(boardClearUpdate, boardClearUpdate -> {
                                String info =(String) boardClearUpdate[0];
                                String updatename = getBoardName(info);

                                if (boardname.equals(updatename)){
                                    //"host:port:boardid%version%PATHS"
                                    if (showedWhitedboard.clear(getBoardVersion(info))) {
                                        drawSelectedWhiteboard();
                                    }
                                    }

//                                if (showedWhitedboard.clear(getBoardVersion(info))) {
//                                    log.info("OnBoardPath clear " );
//                                    drawSelectedWhiteboard();
//                                }
//
                            }).on(boardError, boardError -> {

                            });

                            peerManager.on(boardPathUpdate,boardPathargs1 -> {
                                String info =(String) boardPathargs1[0];
                                if (boardname.equals(getBoardName(info))){

                                    log.info("Send path update to remote: "+(getBoardName(info)));

                                    endpoint.emit(boardPathUpdate, boardPathargs1);
                                }

                            }).on(boardUndoUpdate,boardUndoargs1 -> {
                                String info =(String) boardUndoargs1[0];
                                if (boardname.equals(getBoardName(info))){

                                    log.info("Send Undo to remote: "+(getBoardName(info)));

                                    endpoint.emit(boardUndoUpdate, boardUndoargs1);
                                }

                            }).on(boardClearUpdate,boardClearargs1 -> {
                                String info =(String) boardClearargs1[0];
                                if (boardname.equals(getBoardName(info))){
                                    log.info("Send clear update to remote: "+ getBoardName(info));
                                    endpoint.emit(boardClearUpdate, boardClearargs1);
                                }

                                //host:port:boardid"
                            }).on(unlistenBoard ,unlistebArgs->{

                                String info =(String) unlistebArgs[0];
                                if (boardname.equals(getBoardName(info))){
                                    log.info("Send Unlisten to remote: "+ getBoardName(info));
                                    endpoint.emit(unlistenBoard, unlistebArgs);
                                }
                                //  host:port:boardid"
                            }).on(boardDeleted,deleteArgs->{
                                String info =(String) deleteArgs[0];
                                if (boardname.equals(getBoardName(info))){
                                    log.info("Send delete to remote: "+ getBoardName(info));
                                    endpoint.emit(boardDeleted, deleteArgs);
                                }
                                whiteboards.remove(showedWhitedboard.getName());

                            }).on(listenBoard,listenArgs->{
                                String listenboardName = (String) listenArgs[0];
                                if (listenboardName.equals(boardname)){
                                    endpoint.emit(listenBoard,  boardname );
                                    endpoint.emit(getBoardData, boardname );
                                }
                            });

                            log.info("Send listenBoard request");

                            endpoint.emit(listenBoard,  boardname );

                            endpoint.emit(getBoardData, boardname );


                        }).on(PeerManager.peerStopped, (peerStopped) -> {
                            Endpoint endpoint = (Endpoint) peerStopped[0];
                            System.out.println("Disconnected from peer: " + endpoint.getOtherEndpointId());
                        }).on(PeerManager.peerError, (peerError) -> {
                            Endpoint endpoint = (Endpoint) peerError[0];
                            System.out.println("There was error while communication with peer: "
                                    + endpoint.getOtherEndpointId());
                            endpoint.emit(WhiteboardServer.error, "Error to connect");
                        });
                        peerManager.on("Unshare:"+boardname,Unshare -> {

                            clientManager.shutdown();

                            log.info("the connection with:"+boardname+" has been unshared");
                        });


                        clientManager.start();

                    } catch (InterruptedException e) {
                        log.warning("InterruptedException");
                    } catch (UnknownHostException e) {
                        log.warning("UnknownHostException");
                    }
                    }





                });

            }
    }
	
	/******
	 * 
	 * Methods to manipulate data locally. Distributed systems related code has been
	 * cut from these methods.
	 * 
	 ******/
	
	/**
	 * Wait for the peer manager to finish all threads.
	 */
	public void waitToFinish()  {
        try {
            peerManager.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

	
	/**
	 * Add a board to the list that the user can select from. If select is
	 * true then also select this board.
	 * @param whiteboard
	 * @param select
	 */
	public void addBoard(Whiteboard whiteboard,boolean select) {
		synchronized(whiteboards) {
			whiteboards.put(whiteboard.getName(), whiteboard);
		}
		updateComboBox(select?whiteboard.getName():null);
	}
	
	/**
	 * Delete a board from the list.
	 * @param boardname must have the form peer:port:boardid
	 */
	public void deleteBoard(String boardname) {
		synchronized(whiteboards) {
			Whiteboard whiteboard = whiteboards.get(boardname);
			if(whiteboard!=null) {
                deleteCheck(whiteboard);
				whiteboards.remove(boardname);
			}
		}
		updateComboBox(null);
	}

	public void deleteCheck(Whiteboard whiteboard){

        if (whiteboard.isShared()) {
            // ip:port:bid
            peerManager.emit(WhiteboardServer.unshareBoard,whiteboard.getName());
        }
        if(whiteboard.isRemote()) {
            log.info("Request unlisten board: "+whiteboard.getName());
            peerManager.emit(unlistenBoard,whiteboard.getName());
        }

    }
	
	/**
	 * Create a new local board with name peer:port:boardid.
	 * The boardid includes the time stamp that the board was created at.
	 */
	public void createBoard() {
		String name = peerport+":board"+Instant.now().toEpochMilli();
		Whiteboard whiteboard = new Whiteboard(name,false);
		addBoard(whiteboard,true);
	}

	
	/**
	 * Add a path to the selected board. The path has already
	 * been drawn on the draw area; so if it can't be accepted then
	 * the board needs to be redrawn without it.
	 * @param currentPath
	 */
	public void pathCreatedLocally(WhiteboardPath currentPath) {
		if(selectedBoard!=null) {
			if(!selectedBoard.addPath(currentPath,selectedBoard.getVersion())) {
				// some other peer modified the board in between
				drawSelectedWhiteboard(); // just redraw the screen without the path
			} else {
                if (selectedBoard.isRemote()|| (selectedBoard.isShared()&&(!selectedBoard.beListenedEndpoints.isEmpty()))) {
                    String data = selectedBoard.toString();
                    String boardName = getBoardName(data);
                    long version = getBoardVersion(data) - 1;
                    String path = String.valueOf(currentPath);
                    String arg = boardName+"%"+String.valueOf(version)+"%"+path;
                    peerManager.emit(boardPathUpdate ,arg);
                }


				// was accepted locally, so do remote stuff if needed
			}
		} else {
			log.severe("path created without a selected board: "+currentPath);
		}
	}
	
	/**
	 * Clear the selected whiteboard.
	 */
	public void clearedLocally() {
        if(selectedBoard!=null) {
            if(!selectedBoard.clear(selectedBoard.getVersion())) {
                // some other peer modified the board in between
                drawSelectedWhiteboard();
            } else {
                if (selectedBoard.isRemote()) {
                    String data = selectedBoard.toString();
                    String boardName = getBoardName(data);
                    long version = getBoardVersion(data)-1 ;
                    String path = getBoardPaths(data);
                    String arg = boardName+"%"+version+"%";
                    peerManager.emit(boardClearUpdate, arg);
                    drawSelectedWhiteboard();
                }else if (selectedBoard.isShared()&&(!selectedBoard.beListenedEndpoints.isEmpty())) {
                    String data = selectedBoard.toString();
                    String boardName = getBoardName(data);
                    long version = getBoardVersion(data) -1;
                    String path = getBoardPaths(data);
                    String arg = boardName+"%"+version+"%";
                    peerManager.emit(boardClearUpdate, arg);
                    drawSelectedWhiteboard();
                }else {
                    drawSelectedWhiteboard();
                }
            }
        } else {
            log.severe("undo without a selected board");
        }
	}
	
	/**
	 * Undo the last path of the selected whiteboard.
	 */
	public void undoLocally() {
		if(selectedBoard!=null) {
			if(!selectedBoard.undo(selectedBoard.getVersion())) {
				// some other peer modified the board in between
				drawSelectedWhiteboard();
			} else {
                if (selectedBoard.isRemote()) {

                    String data = selectedBoard.toString();
                    String boardName = getBoardName(data);
                    long version = getBoardVersion(data) -1;
                    String path = getBoardPaths(data);
                    String arg = boardName+"%"+version+"%";
                    peerManager.emit(boardUndoUpdate, arg);
                    drawSelectedWhiteboard();

                }else if (selectedBoard.isShared()&&(!selectedBoard.beListenedEndpoints.isEmpty())) {
                    String data = selectedBoard.toString();
                    String boardName = getBoardName(data);
                    long version = getBoardVersion(data) -1;
                    String path = getBoardPaths(data);
                    String arg = boardName+"%"+version+"%";
                    peerManager.emit(boardUndoUpdate, arg);

                    drawSelectedWhiteboard();
                }else {
                    drawSelectedWhiteboard();
                }
			}
		    } else {
			log.severe("undo without a selected board");
		}
	}
	
	/**
	 * The variable selectedBoard has been set.
	 */
	public void selectedABoard() {
		drawSelectedWhiteboard();
		log.info("Selected board: "+selectedBoard.getName());
	}
	
	/**
	 * Set the share status on the selected board.
	 */
	public void setShare(boolean share) {
		if(selectedBoard!=null) {
        	selectedBoard.setShared(share);
        } else {
        	log.severe("There is no selected board");
        }
	}
	
	/**
	 * Called by the gui when the user closes the app.
	 */
	public void guiShutdown() {
		// do some final cleanup
		HashSet<Whiteboard> existingBoards= new HashSet<>(whiteboards.values());
		existingBoards.forEach((board)->{
			deleteBoard(board.getName());
		});
    	whiteboards.values().forEach((whiteboard)->{
    	});
    	connectEndpoints.forEach(endpoint->{
            SessionProtocol sessionProtocol=(SessionProtocol) endpoint.getProtocol("SessionProtocol");
            if(sessionProtocol!=null)
                sessionProtocol.stopSession();
        });
    	peerManager.shutdown();
	}
	
	

	/******
	 *
	 * GUI methods and callbacks from GUI for user actions.
	 * You probably do not need to modify anything below here.
	 *
	 ******/

	/**
	 * Redraw the screen with the selected board
	 */
	public void drawSelectedWhiteboard() {
		drawArea.clear();
		if(selectedBoard!=null) {
			selectedBoard.draw(drawArea);
		}
	}
	
	/**
	 * Setup the Swing components and start the Swing thread, given the
	 * peer's specific information, i.e. peer:port string.
	 */
	public void show(String peerport) {
		// create main frame
		JFrame frame = new JFrame("Whiteboard Peer: "+peerport);
		Container content = frame.getContentPane();
		// set layout on content pane
		content.setLayout(new BorderLayout());
		// create draw area
		drawArea = new DrawArea(this);

		// add to content pane
		content.add(drawArea, BorderLayout.CENTER);

		// create controls to apply colors and call clear feature
		JPanel controls = new JPanel();
		controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));

		/**
		 * Action listener is called by the GUI thread.
		 */
		ActionListener actionListener = new ActionListener() {

			public void actionPerformed(ActionEvent e) {
				if (e.getSource() == clearBtn) {
					clearedLocally();
				} else if (e.getSource() == blackBtn) {
					drawArea.setColor(Color.black);
				} else if (e.getSource() == redBtn) {
					drawArea.setColor(Color.red);
				} else if (e.getSource() == boardComboBox) {
					if(modifyingComboBox) return;
					if(boardComboBox.getSelectedIndex()==-1) return;
					String selectedBoardName=(String) boardComboBox.getSelectedItem();
					if(whiteboards.get(selectedBoardName)==null) {
						log.severe("Selected a board that does not exist: "+selectedBoardName);
						return;
					}
					if (selectedBoard != null){
					    if (selectedBoard.isRemote()){
                            peerManager.emit(unlistenBoard,selectedBoard.getName());
                        }
                    }
					selectedBoard = whiteboards.get(selectedBoardName);

					// remote boards can't have their shared status modified
					if(selectedBoard.isRemote()) {
						sharedCheckbox.setEnabled(false);
						sharedCheckbox.setVisible(false);
					} else {
						modifyingCheckBox=true;
						sharedCheckbox.setSelected(selectedBoard.isShared());
						modifyingCheckBox=false;
						sharedCheckbox.setEnabled(true);
						sharedCheckbox.setVisible(true);
					}
					if (selectedBoard.isRemote() & (!selectedBoard.getUsed())){
					    selectedBoard.setUsed();
					    peerManager.emit(listenBoard+selectedBoard.getName(),selectedBoard.getName());
                    } else {
                        if (selectedBoard.isRemote()) {
                            peerManager.emit(listenBoard, selectedBoard.getName());
                        }
                    }
					selectedABoard();
				} else if (e.getSource() == createBoardBtn) {
					createBoard();
				} else if (e.getSource() == undoBtn) {
					if(selectedBoard==null) {
						log.severe("There is no selected board to undo");
						return;
					}
					undoLocally();
				} else if (e.getSource() == deleteBoardBtn) {
					if(selectedBoard==null) {
						log.severe("There is no selected board to delete");
						return;
					}
					deleteBoard(selectedBoard.getName());
				}
			}
		};
		
		clearBtn = new JButton("Clear Board");
		clearBtn.addActionListener(actionListener);
		clearBtn.setToolTipText("Clear the current board - clears remote copies as well");
		clearBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
		blackBtn = new JButton("Black");
		blackBtn.addActionListener(actionListener);
		blackBtn.setToolTipText("Draw with black pen");
		blackBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
		redBtn = new JButton("Red");
		redBtn.addActionListener(actionListener);
		redBtn.setToolTipText("Draw with red pen");
		redBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
		deleteBoardBtn = new JButton("Delete Board");
		deleteBoardBtn.addActionListener(actionListener);
		deleteBoardBtn.setToolTipText("Delete the current board - only deletes the board locally");
		deleteBoardBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
		createBoardBtn = new JButton("New Board");
		createBoardBtn.addActionListener(actionListener);
		createBoardBtn.setToolTipText("Create a new board - creates it locally and not shared by default");
		createBoardBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
		undoBtn = new JButton("Undo");
		undoBtn.addActionListener(actionListener);
		undoBtn.setToolTipText("Remove the last path drawn on the board - triggers an undo on remote copies as well");
		undoBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
		sharedCheckbox = new JCheckBox("Shared");
		sharedCheckbox.addItemListener(new ItemListener() {    
	         public void itemStateChanged(ItemEvent e) { 
	            if(!modifyingCheckBox){
	                // If user click the checkbox, modify the value of share
	                setShare(e.getStateChange()==1);
                    String data = selectedBoard.toString();
                    // if the board is shared, send msg to server
                    //
                    if (selectedBoard.isShared()) {
                        log.info("Send shareBoard to Server: " + getBoardName(data));
                        peerManager.emit(WhiteboardServer.shareBoard,getBoardName(data));
                    } else {
                        log.info("Send unshareBoard to Server: "+ getBoardName(data));
                        peerManager.emit(WhiteboardServer.unshareBoard,getBoardName(data));
                    }

	            }
	         }    
	      }); 
		sharedCheckbox.setToolTipText("Toggle whether the board is shared or not - tells the whiteboard server");
		sharedCheckbox.setAlignmentX(Component.CENTER_ALIGNMENT);
		

		// create a drop list for boards to select from
		JPanel controlsNorth = new JPanel();
		boardComboBox = new JComboBox<String>();
		boardComboBox.addActionListener(actionListener);
		
		
		// add to panel
		controlsNorth.add(boardComboBox);
		controls.add(sharedCheckbox);
		controls.add(createBoardBtn);
		controls.add(deleteBoardBtn);
		controls.add(blackBtn);
		controls.add(redBtn);
		controls.add(undoBtn);
		controls.add(clearBtn);

		// add to content pane
		content.add(controls, BorderLayout.WEST);
		content.add(controlsNorth,BorderLayout.NORTH);

		frame.setSize(600, 600);
		
		// create an initial board
		createBoard();
		
		// closing the application
		frame.addWindowListener(new WindowAdapter() {
		    @Override
		    public void windowClosing(WindowEvent windowEvent) {
		        if (JOptionPane.showConfirmDialog(frame, 
		            "Are you sure you want to close this window?", "Close Window?", 
		            JOptionPane.YES_NO_OPTION,
		            JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION)
		        {
		        	guiShutdown();
		            frame.dispose();
		        }
		    }
		});
		
		// show the swing paint result
		frame.setVisible(true);
		
	}
	
	/**
	 * Update the GUI's list of boards. Note that this method needs to update data
	 * that the GUI is using, which should only be done on the GUI's thread, which
	 * is why invoke later is used.
	 * 
	 * @param select, board to select when list is modified or null for default
	 *                selection
	 */
	private void updateComboBox(String select) {
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				modifyingComboBox=true;
				boardComboBox.removeAllItems();
				int anIndex=-1;
				synchronized(whiteboards) {
					ArrayList<String> boards = new ArrayList<String>(whiteboards.keySet());
					Collections.sort(boards);
					for(int i=0;i<boards.size();i++) {
						String boardname=boards.get(i);
						boardComboBox.addItem(boardname);
						if(select!=null && select.equals(boardname)) {
							anIndex=i;
						} else if(anIndex==-1 && selectedBoard!=null && 
								selectedBoard.getName().equals(boardname)) {
							anIndex=i;
						} 
					}
				}
				modifyingComboBox=false;
				if(anIndex!=-1) {
					boardComboBox.setSelectedIndex(anIndex);
				} else {
					if(whiteboards.size()>0) {
						boardComboBox.setSelectedIndex(0);
					} else {
						drawArea.clear();
						createBoard();
					}
				}
				
			}
		});
	}

}
