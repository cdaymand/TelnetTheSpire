package telnetthespire;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.util.*;
import java.net.*;

class UpdateStateThread extends Thread {
    private SlayTheSpireServer server;
    private static final Logger logger = LogManager.getLogger(UpdateStateThread.class.getName());

    public UpdateStateThread(telnetthespire.SlayTheSpireServer server) {
	this.server = server;
    }

    public void run() {
	while(true) {
	    try {
		HashMap<String, Object> state = server.getStateQueue().take();
		server.setState(state);
		server.displayState();
	    } catch (InterruptedException e) {
		logger.info("Communications writing thread interrupted.");
		Thread.currentThread().interrupt();
	    } catch (Exception e) {
		logger.error("Exception catched in UpdateStateThread");
		e.printStackTrace();
	    }
	}
    }
}
