package telnetthespire;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.util.*;
import java.net.*;

import java.util.concurrent.BlockingQueue;

import com.megacrit.cardcrawl.map.MapRoomNode;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.dungeons.TheEnding;

class  HandleClient extends Thread {
    private SlayTheSpireServer server;
    private InputStreamReader inputStream;
    private BufferedReader input;
    private PrintWriter output;
    private Boolean exploreMode;
    private Stack<MapRoomNode> nodeStack;
    private static final Logger logger = LogManager.getLogger(UpdateStateThread.class.getName());

    public HandleClient(SlayTheSpireServer server, Socket clientSocket) throws Exception {
	// get input and output streams
	this.server = server;
	exploreMode = false;
	nodeStack = new Stack<MapRoomNode>();
	inputStream = new InputStreamReader( clientSocket.getInputStream());
	input = new BufferedReader(inputStream);
	output = new PrintWriter ( clientSocket.getOutputStream(),true);
	output.println("Welcome to Telnet The Spire!");
	output.println("Use command \"show commands\" to see all available commands at anytime");
    }

    public void sendMessage(String message)  {
	output.println(message);
    }

    public ArrayList<String> getMapScreenChoices() {
        ArrayList<String> choices = new ArrayList<>();
        MapRoomNode exploreNode = nodeStack.peek();
        if(exploreNode.y == 14 || (AbstractDungeon.id.equals(TheEnding.ID) && exploreNode.y == 2)) {
            sendMessage("Boss!");
            return choices;
        }
        ArrayList<MapRoomNode> availableNodes = getMapScreenNodeChoices();
        for (MapRoomNode node: availableNodes) {
	    String nodeSymbol = node.getRoomSymbol(true);
	    String coordinates = "(x=" + String.valueOf(node.x) + ", y=" + String.valueOf(node.y) + ") ";
	    switch(nodeSymbol) {
	    case "M":
		nodeSymbol = "Enemy";
		break;
	    case "E":
		if(node.hasEmeraldKey)
		    nodeSymbol = "Flaming elite";
		else
		    nodeSymbol = "Elite";
		break;
	    case "$":
		nodeSymbol = "Merchant";
		break;
	    case "T":
		nodeSymbol = "Treasure";
		break;
	    case "R":
		nodeSymbol = "Rest";
		break;
	    case "?":
		nodeSymbol = "Unknown";
	    }
            choices.add(coordinates + nodeSymbol);
        }
        return choices;
    }

    public ArrayList<MapRoomNode> getMapScreenNodeChoices() {
        ArrayList<MapRoomNode> choices = new ArrayList<>();
        MapRoomNode exploreNode = nodeStack.peek();
        ArrayList<ArrayList<MapRoomNode>> map = AbstractDungeon.map;
        if(exploreNode.y == -1 || exploreNode.y == 15) {
            for(MapRoomNode node : map.get(0)) {
                if (node.hasEdges()) {
                    choices.add(node);
                }
            }
        } else {
            for (ArrayList<MapRoomNode> rows : map) {
                for (MapRoomNode node : rows) {
                    if (node.hasEdges()) {
                        boolean normalConnection = exploreNode.isConnectedTo(node);
                        boolean wingedConnection = exploreNode.wingedIsConnectedTo(node);
                        if (normalConnection || wingedConnection) {
                            choices.add(node);
                        }
                    }
                }
            }
        }
        return choices;
    }

    public void explore(String command) {
	String usage = SlayTheSpireServer.colored("Usage: quit|back|RoomIndex", SlayTheSpireServer.ANSI_RED);
	switch(command) {
	case "explore":
	    sendMessage("You are in exploration mode");
	    sendMessage("Enter room index or \"back\" to explore the map");
	    sendMessage("Enter \"quit\" to leave this mode");
	    nodeStack.push(AbstractDungeon.getCurrMapNode());
	    exploreMode = true;
	    break;
	case "quit":
	    exploreMode = false;
	    nodeStack.removeAllElements();
	    server.displayFooter();
	    return;
	case "back":
	    if(nodeStack.size() > 1)
		nodeStack.pop();
	    else {
		sendMessage("You can't go back from your current position");
		return;
	    }
	    break;
	case "":
	    return;
	default:
	    int roomIndex;
	    try {
		roomIndex = Integer.parseInt(command);
	    } catch(NumberFormatException e) {
		sendMessage(usage);
		return;
	    }
	    roomIndex -= 1;
	    ArrayList<MapRoomNode> rooms = getMapScreenNodeChoices();
	    if (roomIndex < 0 || roomIndex >= rooms.size()){
		sendMessage(SlayTheSpireServer.colored("This room doesn't exist !", SlayTheSpireServer.ANSI_RED));
		return;
	    }
	    nodeStack.push(rooms.get(roomIndex));
	}
	int i = 1;
	String coordinates = "(x=" + String.valueOf(nodeStack.peek().x) + ", y=" + String.valueOf(nodeStack.peek().y) + ")";
	sendMessage("Current room: " + coordinates);
	for(String room: getMapScreenChoices()) {
	    sendMessage("\t" + String.valueOf(i) + ": " + room);
	    i += 1;
	}
    }

    public void showCommandHelper(String command) {
	String usage = SlayTheSpireServer.colored("Usage: show commands|map|health|relics|potions|gold|boss|deck|combat|draw|discard|exhaust\n" +
						  "\tshow hand [CardIndex]", SlayTheSpireServer.ANSI_RED);
	String notInGame = SlayTheSpireServer.colored("You are not in a game", SlayTheSpireServer.ANSI_RED);
	String notInCombat = SlayTheSpireServer.colored("You are not in a combat", SlayTheSpireServer.ANSI_RED);
	String [] tokens = command.split("\\s+");
	if(tokens.length < 2) {
	    sendMessage(usage);
	    return;
	}
	switch(tokens[1]) {
	case "commands":
	    server.displayCommands();
	    break;
	case "map":
	    if(server.inGame)
		server.displayMap();
	    else
		sendMessage(notInGame);
	    break;
	case "health":
	    if(server.inGame)
		server.showHealth();
	    else
		sendMessage(notInGame);
	    break;
	case "relics":
	    if(server.inGame)
		server.showRelics();
	    else
		sendMessage(notInGame);
	    break;
	case "potions":
	    if(server.inGame)
		server.showPotions();
	    else
		sendMessage(notInGame);
	    break;
	case "gold":
	    if(server.inGame)
		server.showGold();
	    else
		sendMessage(notInGame);
	    break;
	case "boss":
	    if(server.inGame)
		server.showBoss();
	    else
		sendMessage(notInGame);
	    break;
	case "deck":
	    if(server.inGame)
		server.displayDeck();
	    else
		sendMessage(notInGame);
	    break;
	case "combat":
	    if(server.inCombat)
		server.showCombat();
	    else
		sendMessage(notInCombat);
	    break;
	case "hand":
	    if(server.inCombat) {
		int cardIndex = -1;
		if(tokens.length == 3) {
		    try {
			cardIndex = Integer.parseInt(tokens[2]);
		    } catch(NumberFormatException e) {
			sendMessage(usage);
			return;
		    }
		}
		server.displayHand(cardIndex);
	    } else
		sendMessage(notInCombat);
	    break;
	case "draw":
	    if(server.inCombat)
		server.displayDraw();
	    else
		sendMessage(notInCombat);
	    break;
	case "discard":
	    if(server.inCombat)
		server.displayDiscard();
	    else
		sendMessage(notInCombat);
	    break;
	case "exhaust":
	    if(server.inCombat)
		server.displayExhaust();
	    else
		sendMessage(notInCombat);
	    break;
	default:
	    sendMessage(usage);
	}
    }

    public void removeClient() {
	try {
	    server.removeClient(this);
	    inputStream.close();
	} catch(IOException e) {
	    e.printStackTrace();
	}
    }

    public void run(){
	String message;
	try {
	    server.getReadQueue().put("state\n");
	} catch(InterruptedException e) {
	    e.printStackTrace();
	}
	while(true) {
	    try {
		message = input.readLine();
		logger.info("Command sent: " + message);
		if ( message == null || message.equals("disconnect")) {
		    removeClient();
		    break;
		}
		message = message.toLowerCase();
		if(message.equals("")) {
		    continue;
		} else if(exploreMode) {
		    explore(message);
		    continue;
		} else if(message.startsWith("show")) {
		    showCommandHelper(message);
		    server.displayFooter();
		    continue;
		} else if(message.equals("explore")) {
		    explore(message);
		    continue;
		} else if(message.equals("help")) {
		    server.displayHelp();
		    server.displayFooter();
		    continue;
		}
		if (Character.isDigit(message.charAt(0))) {
		    if (server.getAvailableCommands().contains("choose")) {
			message = "choose " + message;
		    } else if (server.getAvailableCommands().contains("play")) {
			message = "play " + message;
		    }
		} else if(server.getChoiceList().contains(message))
		    message = "choose " + message;

		if (server.getState().get("ready_for_command") != null && (Boolean) server.getState().get("ready_for_command"))
		    server.getReadQueue().put(message);
	    } catch(SocketException e) {
		e.printStackTrace();
		removeClient();
		break;
	    } catch(Exception e) {
		sendMessage(SlayTheSpireServer.colored("Oups an error occured! Please retry your last command", SlayTheSpireServer.ANSI_RED));
		e.printStackTrace();
	    }
	}
    }
}
