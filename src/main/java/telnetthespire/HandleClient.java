package telnetthespire;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.util.*;
import java.net.*;

import java.util.concurrent.BlockingQueue;

class  HandleClient extends Thread {
    private SlayTheSpireServer server;
    private InputStreamReader inputStream;
    private BufferedReader input;
    private PrintWriter output;
    private static final Logger logger = LogManager.getLogger(UpdateStateThread.class.getName());

    public HandleClient(SlayTheSpireServer server, Socket clientSocket) throws Exception {
	// get input and output streams
	this.server = server;
	inputStream = new InputStreamReader( clientSocket.getInputStream());
	input = new BufferedReader(inputStream);
	output = new PrintWriter ( clientSocket.getOutputStream(),true);
	output.println("Welcome to SlayTheCLI Server!");
    }

    public void sendMessage(String message)  {
	output.println(message);
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
		message = message.toLowerCase();
		logger.info("Command sent: " + message);
		if ( message == null || message.equals("disconnect")) {
		    server.removeClient(this);
		    inputStream.close();
		    break;
		} else if(message.equals("map")) {
		    server.displayMap();
		    server.displayCommands();
		    continue;
		} else if(message.equals("deck")) {
		    server.displayDeck();
		    server.displayCommands();
		    continue;
		} else if(message.equals("draw")) {
		    server.displayDraw();
		    server.displayCommands();
		    continue;
		} else if(message.equals("discard")) {
		    server.displayDiscard();
		    server.displayCommands();
		    continue;
		} else if(message.equals("exhaust")) {
		    server.displayExhaust();
		    server.displayCommands();
		    continue;
		} else if(message.equals("help")) {
		    server.displayHelp();
		    server.displayCommands();
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
	    } catch(Exception e) {
		sendMessage(SlayTheSpireServer.colored("Oups an error occured! Please retry your last command", SlayTheSpireServer.ANSI_RED));
		e.printStackTrace();
	    }
	}
    }
}
