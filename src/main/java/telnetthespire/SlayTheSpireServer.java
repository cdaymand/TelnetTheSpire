package telnetthespire;

import com.megacrit.cardcrawl.relics.AbstractRelic;
import com.megacrit.cardcrawl.potions.AbstractPotion;
import com.megacrit.cardcrawl.powers.AbstractPower;
import com.megacrit.cardcrawl.orbs.AbstractOrb;
import com.megacrit.cardcrawl.stances.AbstractStance;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.CardGroup;
import com.megacrit.cardcrawl.cards.DescriptionLine;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.map.MapEdge;
import com.megacrit.cardcrawl.map.MapRoomNode;

import java.io.*;
import java.util.*;
import java.net.*;
import static java.lang.System.out;

import java.util.concurrent.BlockingQueue;

public class  SlayTheSpireServer implements Runnable {
    private int port;
    private int backlog;
    private String host;
    private BlockingQueue<HashMap<String, Object>> stateQueue;
    private BlockingQueue<String> readQueue;
    private static Boolean displayColor;
    private HashMap<String, Object> state;
    private ArrayList<String> availableCommands;
    private ArrayList<String> choiceList;
    public Boolean inGame;
    public Boolean inCombat;
    private Vector<HandleClient> clients;


    private static final String[] specialCommands = {"proceed", "confirm", "return", "skip", "cancel", "leave"};
    public static final String ANSI_RESET = "\u001B[0m";
    public static final String ANSI_BLACK = "\u001B[30m";
    public static final String ANSI_RED = "\u001B[31m";
    public static final String ANSI_GREEN = "\u001B[32m";
    public static final String ANSI_YELLOW = "\u001B[33m";
    public static final String ANSI_BLUE = "\u001B[34m";
    public static final String ANSI_PURPLE = "\u001B[35m";
    public static final String ANSI_CYAN = "\u001B[36m";
    public static final String ANSI_WHITE = "\u001B[37m";

    public static String colored(String string, String color) {
	if(displayColor)
	    return color + string + ANSI_RESET;
	else
	    return string;
    }

    public SlayTheSpireServer(int port,
			      int backlog,
			      String host,
			      BlockingQueue<HashMap<String, Object>> stateQueue,
			      BlockingQueue<String> readQueue,
			      Boolean displayColor) {
	this.port = port;
	this.backlog = backlog;
	this.host = host;
	this.stateQueue = stateQueue;
	this.readQueue = readQueue;
	this.displayColor = displayColor;
	this.state = new HashMap<String,Object>();
	this.availableCommands = new ArrayList<String>();
	this.choiceList = new ArrayList<String>();
	this.inGame = false;
	this.inCombat = false;
	this.clients = new Vector<HandleClient>();
    }

    public void sendMessage(String message) {
	for ( HandleClient client : clients )
	    client.sendMessage(message);
    }


    public void setState(HashMap<String, Object> state) {
	this.state = state;
    }

    public HashMap<String, Object> getState() {
	return state;
    }

    public ArrayList<String> getAvailableCommands() {
	return availableCommands;
    }

    public ArrayList<String> getChoiceList() {
	return choiceList;
    }

    public BlockingQueue<HashMap<String, Object>> getStateQueue() {
	return stateQueue;
    }

    public BlockingQueue<String> getReadQueue() {
	return readQueue;
    }

    public void removeClient(HandleClient client) {
	clients.remove(client);
    }

    public void run() {
	try {
	    ServerSocket server = new ServerSocket(port, backlog, InetAddress.getByName(host));
	    UpdateStateThread writer = new UpdateStateThread(this);
	    writer.start();
	    while (!Thread.currentThread().isInterrupted()) {
		try {
		    Socket clientSocket = server.accept();
		    HandleClient client = new HandleClient(this, clientSocket);
		    client.start();
		    clients.add(client);
		    StringBuilder inputBuffer = new StringBuilder();
		} catch (InterruptedException e) {
		    Thread.currentThread().interrupt();
		}
	    }
	} catch(Exception ex){
	}
    }

    public static String removeTextFormatting(String text) {
        text = text.replaceAll("~|@(\\S+)~|@", "$1");
        return text.replaceAll("#.|NL", "");
    }

    public Boolean intentBug() {
	HashMap<String, Object> gameState = (HashMap<String, Object>) state.get("game_state");
	if(gameState.get("combat_state") == null)
	    return false;
	HashMap<String, Object> combatState = (HashMap<String, Object>) gameState.get("combat_state");
	ArrayList<HashMap<String, Object>> monsters = (ArrayList<HashMap<String, Object>>) combatState.get("monsters");
	for(HashMap<String, Object> monster: monsters) {
	    if((int) monster.get("current_hp") <= 0) {
		continue;
	    }
	    if(monster.get("intent").equals("DEBUG"))
		return true;
	}
	return false;
    }

    public void displayHelp(){
	String message = "START PlayerClass [AscensionLevel] [Seed]\n" +
	    "\tStarts a new game with the selected class, on the selected Ascension level (default 0), with the selected seed (random seed if omitted).\n" +
	    "\tSeeds are alphanumeric, as displayed in game.\n" +
	    "\tThis and all commands are case insensitive.\n" +
	    "\tOnly currently available in the main menu of the game.\n" +
	    "CONTINUE\n" +
	    "\tContinue the previous save file.\n" +
	    "\tAvailable when there is a save file.\n" +
	    "ABANDON\n" +
	    "\tDelete the previous save file.\n" +
	    "\tAvailable when there is a save file.\n" +
	    "SAVE\n" +
	    "\tSave the game and go back to the main screen menu. Yes you will now be able to restart a lost fight :)\n" +
	    "\tOnly available from the SPIRE.\n" +
	    "POTION Use|Discard PotionSlot [TargetIndex]\n" +
	    "\tUses or discards the potion in the selected slot, on the selected target, if necessary.\n" +
	    "\tTargetIndex is the index of the target monster in the game's monster array (0-indexed).\n" +
	    "\tOnly available when potions can be used or discarded.\n" +
	    "[PLAY] CardIndex [TargetIndex]\n" +
	    "\tPlays the selected card in your hand, with the selected target, if necessary.\n" +
	    "\tOnly available when cards can be played in combat.\n" +
	    "\tWhen the PLAY command is available, you can directly enter CardIndex [TargetIndex]\n" +
	    "\tCurrently, CardIndex is 1-indexed to match up with the card numbers in game.\n" +
	    "[CHOOSE] ChoiceIndex|ChoiceName\n" + "\tMakes a choice relevant to the current screen.\n" +
	    "\tWhen the CHOOSE command is available, you can directly enter ChoiceIndex|ChoiceName\n" +
	    "\tA list of names for each choice is provided in the game state. If provided with a name, the first choice index with the matching name is selected.\n" +
	    "\tGenerally, available at any point when PLAY is not available.\n" +
	    "SHOW commands|map|health|relics|potions|gold|boss|deck|combat|draw|discard|exhaust\n" +
	    "\tShow the selected category.\n" +
	    "SHOW hand [CardIndex]\n" +
	    "\tShow the hand detailed information or only for a card.\n" +
	    "EXPLORE\n" +
	    "\tAn exploration mode for the map for accessibility purpose.\n" +
	    "END\n" +
	    "\tEnds your turn.\n" +
	    "\tOnly available when the end turn button is available, in combat.\n" +
	    "PROCEED\n" +
	    "\tClicks the button on the right side of the screen, generally causing the game to proceed to a new screen.\n" +
	    "\tEquivalent to CONFIRM.\n" +
	    "\tAvailable whenever the proceed or confirm button is present on the right side of the screen.\n" +
	    "RETURN\n" +
	    "\tClicks the button on the left side of the screen, generally causing you to return to the previous screen.\n" +
	    "\tEquivalent to SKIP, CANCEL, and LEAVE.\n" +
	    "\tAvailable whenever the return, cancel, or leave buttons are present on the left side of the screen. Also used for the skip button on card reward screens.\n" +
	    "KEY Keyname [Timeout]\n" +
	    "\tPresses the key corresponding to Keyname\n" +
	    "\tPossible keynames are: Confirm, Cancel, Map, Deck, Draw_Pile, Discard_Pile, Exhaust_Pile, End_Turn, Up, Down, Left, Right, Drop_Card, Card_1, Card_2, ..., Card_10\n" +
	    "\tThe actual keys pressed depend on the corresponding mapping in the game options\n" +
	    "\tIf no state change is detected after [Timeout] frames (default 100), Communication Mod will then transmit the new state and accept input from the game. This is useful for keypresses that open menus or pick up cards, without affecting the state as detected by Communication Mod.\n" +
	    "\tOnly available in a run (not the main menus)\n" +
	    "CLICK Left|Right X Y\n" +
	    "\tClicks the selected mouse button at the specified (X,Y) coordinates\n" +
	    "\t(0,0) is the upper left corner of the screen, and (1920,1080) is the lower right corner, regardless of game resolution\n" +
	    "\tWill move your cursor to the specified coordindates\n" +
	    "\tTimeout works the same as the CLICK command\n" +
	    "\tOnly available in a run\n" +
	    "WAIT Timeout\n" +
	    "\tWaits for the specified number of frames or until a state change is detected, then transmits the current game state (same behavior as Timeout for the CLICK and KEY commands, but no input is sent to the game)\n" +
	    "\tPossibly useful for KEY and CLICK commands which are expected to produce multiple state changes as detected by Communication Mod\n" +
	    "\tOnly available in a run\n" +
	    "STATE\n" +
	    "\tCauses the server to refresh the state of the game.\n" +
	    "DISCONNECT\n" +
	    "\tDisconnect from the server.\n" +
	    "EXIT\n" +
	    "\tForce the game to stop.\n" +
	    "HELP\n" +
	    "\tDisplay this message.\n";
	sendMessage(message);
    }

    public void displayCommands(){
	if (state.get("available_commands") == null)
	    return;
	String message = "";
	availableCommands = (ArrayList<String>) state.get("available_commands");
	for(String command: availableCommands)
	    message += "[" + command + "]";
	sendMessage("Commands: " + message);
    }

    public void displayFooter() {
	if (state.get("available_commands") == null)
	    return;
	availableCommands = (ArrayList<String>) state.get("available_commands");
	if(!inGame){
	    return;
	}
	HashMap<String, Object> gameState = (HashMap<String, Object>) state.get("game_state");
	if(gameState.get("choice_list") != null) {
	    choiceList = (ArrayList<String>) gameState.get("choice_list");
	    sendMessage("Choices:");
	    int i = 1;
	    for(String choice: choiceList) {
		sendMessage("\t" + String.valueOf(i) + ": " + removeTextFormatting(choice));
		i += 1;
	    }
	}
	String specialCommandsText = "";
	for(String c: specialCommands) {
	    if(availableCommands.contains(c))
		specialCommandsText += "[" + c + "]";
	}
	if(!specialCommandsText.equals(""))
	    sendMessage("Special commands: " + specialCommandsText);
    }

    public void displayState() {
	if(state.get("error") != null) {
	    sendMessage(colored("Error: " + state.get("error"), ANSI_RED));
	    // Re-ask state after an error
	    try {
		readQueue.put("state\n");
	    } catch(Exception e) {
		e.printStackTrace();
	    }
	    return;
	}
	if((Boolean) state.get("in_game") == true) {
	    if(intentBug()){
		// Monster intent is DEBUG refresh state
		try {
		    readQueue.put("state\n");
		} catch(Exception e) {
		    e.printStackTrace();
		}
		return;
	    }
	    inGame = true;
	    displayGame();
	} else {
	    inGame = false;
	    displayCommands();
	}
	displayFooter();
    }

    public void showBoss(){
	HashMap<String, Object> gameState = (HashMap<String, Object>) state.get("game_state");
	sendMessage("Boss: " + gameState.get("act_boss"));
    }

    public void showHealth(){
	HashMap<String, Object> gameState = (HashMap<String, Object>) state.get("game_state");
	sendMessage("Health: " + gameState.get("current_hp") + "/" + gameState.get("max_hp"));
    }

    public void showRelics(){
	HashMap<String, Object> gameState = (HashMap<String, Object>) state.get("game_state");
	sendMessage("Relics:");
	for(AbstractRelic relic: (ArrayList<AbstractRelic>) gameState.get("relics")) {
	    String counterText = "";
	    if(relic.counter != -1)
		counterText = "(" + String.valueOf(relic.counter) + ")";
            sendMessage("[" + relic.name + counterText + "]: " + removeTextFormatting(relic.description));
        }
    }

    public void showPotions(){
	HashMap<String, Object> gameState = (HashMap<String, Object>) state.get("game_state");
	int i = 1;
	for(AbstractPotion potion: (ArrayList<AbstractPotion>) gameState.get("potions")) {
            sendMessage(i + ": [" + potion.name + "]: " + removeTextFormatting(potion.description));
	    i += 1;
        }
    }

    public void showGold(){
	HashMap<String, Object> gameState = (HashMap<String, Object>) state.get("game_state");
	sendMessage("Gold: " + gameState.get("gold"));
    }
    public void showCombat(){
	HashMap<String, Object> gameState = (HashMap<String, Object>) state.get("game_state");
	displayCombat(gameState, false);
    }
    public void displayGame() {
	HashMap<String, Object> gameState = (HashMap<String, Object>) state.get("game_state");
	if(gameState.get("combat_state") != null) {
	    inCombat = true;
            displayCombat(gameState, true);
	} else {
	    inCombat = false;
	}
	ChoiceScreenUtils.ChoiceType screenType = (ChoiceScreenUtils.ChoiceType) gameState.get("screen_type");
	if(screenType != null) {
	    HashMap<String, Object> screenState = (HashMap<String, Object>) gameState.get("screen_state");
	    switch (screenType) {
	    case MAP:
		sendMessage("MAP: Use \"show map\" or \"explore\"");
		break;
	    case EVENT:
		displayEvent(screenState);
		break;
	    case GAME_OVER:
		displayGameOver(screenState);
		break;
	    case GRID:
		if(AbstractDungeon.gridSelectScreen.confirmScreenUp && AbstractDungeon.gridSelectScreen.forUpgrade) {
		    sendMessage(showCard(AbstractDungeon.gridSelectScreen.upgradePreviewCard, false, false, false));
		}
		break;
	    }

	}
    }

    public void displayCombat(HashMap<String, Object> gameState, Boolean brief) {
	HashMap<String, Object> combatState =(HashMap<String, Object>) gameState.get("combat_state");
	HashMap<String, Object> player = (HashMap<String, Object>) combatState.get("player");
	String playerString = colored((String) gameState.get("class"), ANSI_GREEN) +
	    " HP: " + player.get("current_hp") + "/" + player.get("max_hp") +
	    " Block: " + colored(String.valueOf(player.get("block")), ANSI_GREEN);
	sendMessage(playerString);
	ArrayList<AbstractPower> powers = (ArrayList<AbstractPower>) player.get("powers");
	if (powers.size() > 0) {
	    String powersText = "Powers: ";
	    for(AbstractPower power: powers) {
		String description = "]";
		if(!brief)
		    description = ": " + removeTextFormatting(power.description) + "]\n";
		powersText += "[" + power.name + " (" + power.amount + ")" + description;
	    }
	    sendMessage(powersText);
	}
	ArrayList<AbstractOrb> orbs = (ArrayList<AbstractOrb>) player.get("orbs");
	if (orbs.size() > 0) {
	    String orbMessage = "";
	    String orbName;
	    sendMessage("Orbs:");
	    for(AbstractOrb orb: orbs) {
		orbName = orb.name;
		if(orb.ID == null) {
		    orbMessage += "[" + orbName + "]";
		    continue;
		}
		switch(orb.ID) {
		case "Lightning":
		    orbName = colored(orb.name, ANSI_YELLOW);
		    break;
		case "Dark":
		    orbName = colored(orb.name, ANSI_PURPLE);
		    break;
		case "Plasma":
		    orbName = colored(orb.name, ANSI_CYAN);
		    break;
		case "Frost":
		    orbName = colored(orb.name, ANSI_BLUE);
		    break;
		}
		orbMessage += "[" + orbName + ": " +
		    String.valueOf(orb.passiveAmount) +
		    "(" + String.valueOf(orb.evokeAmount) + ")]";
	    }
	    sendMessage(orbMessage);
	}
        AbstractStance stance = (AbstractStance) player.get("stance");
	switch(stance.ID) {
	case "Calm":
	    sendMessage("Stance: " + colored(stance.name, ANSI_BLUE));
	    break;
	case "Wrath":
	    sendMessage("Stance: " + colored(stance.name, ANSI_RED));
	    break;
	case "Divinity":
	    sendMessage("Stance: " + colored(stance.name, ANSI_PURPLE));
	    break;
	}
	int expectedDamages = -1 * (int) player.get("block");
	ArrayList<HashMap<String, Object>> monsters = (ArrayList<HashMap<String, Object>>) combatState.get("monsters");
	sendMessage("Monsters:");
	int i = 0;
	for(HashMap<String, Object> monster: monsters) {
	    i += 1;
	    if((int) monster.get("current_hp") <= 0) {
		continue;
	    }
	    String monsterString = colored(String.valueOf(i) + ": " + monster.get("name"), ANSI_RED) +
		" HP: " + colored(String.valueOf(monster.get("current_hp")) + "/" + String.valueOf(monster.get("max_hp")), ANSI_RED) +
		" Block: " + colored(String.valueOf(monster.get("block")), ANSI_GREEN) +
		" Intent: " + colored((String) monster.get("intent"), ANSI_YELLOW);
	    if ((int) monster.get("move_adjusted_damage") > 0) {
		expectedDamages += (int) monster.get("move_adjusted_damage") * (int) monster.get("move_hits");
		monsterString += " Damage: " + monster.get("move_adjusted_damage") +
		    " * " + monster.get("move_hits");
	    }
	    sendMessage(monsterString);
	    powers = (ArrayList<AbstractPower>) monster.get("powers");
	    if (powers.size() > 0) {
		String powersText = "Powers: ";
		    for(AbstractPower power: powers) {
			String description = "]";
			if(!brief)
			    description = ": " + removeTextFormatting(power.description) + "]\n";
			powersText += "[" + power.name + " (" + power.amount + ")" + description;
		    }
		sendMessage(powersText);
	    }
	}
	sendMessage("Hand:");
	showCards((ArrayList<AbstractCard>) combatState.get("hand"), 1, true, false, false, brief);
	String expectedDamagesText;
	if(expectedDamages > 0) {
	    expectedDamagesText = colored(String.valueOf(expectedDamages), ANSI_RED);
	} else {
	    expectedDamagesText = colored(String.valueOf(expectedDamages), ANSI_GREEN);
	}
	sendMessage("Expected damages: " +  expectedDamagesText);
	String energy = ((int) player.get("energy") > 0) ? colored(String.valueOf(player.get("energy")), ANSI_GREEN) : colored(String.valueOf(player.get("energy")), ANSI_RED);
	sendMessage("Remaining Energy: " + energy);
    }

    private static String paddingGenerator(int length) {
	StringBuilder str = new StringBuilder();
	for (int i = 0; i < length; i++)
	    str.append(" ");
	return str.toString();
    }

    public static String colorNodeSymbol(MapRoomNode node, String nodeSymbol) {
	MapRoomNode currentNode = AbstractDungeon.getCurrMapNode();
	if(currentNode != null && currentNode.x == node.x && currentNode.y == node.y)
	    return colored(nodeSymbol, ANSI_GREEN);
	switch(nodeSymbol) {
	case "M":
	    nodeSymbol = colored(nodeSymbol, ANSI_BLUE);
	    break;
	case "E":
	    if(node.hasEmeraldKey)
		nodeSymbol = colored(nodeSymbol, ANSI_RED);
	    else
		nodeSymbol = colored(nodeSymbol, ANSI_PURPLE);
	    break;
	case "$":
	case "T":
	    nodeSymbol = colored(nodeSymbol, ANSI_YELLOW);
	    break;

	case "R":
	    nodeSymbol = colored(nodeSymbol, ANSI_CYAN);
	    break;
	}
	return nodeSymbol;
    }

    public static String mapToString(ArrayList<ArrayList<MapRoomNode>> nodes, Boolean showRoomSymbols) {
	StringBuilder str = new StringBuilder();
	int row_num = nodes.size() - 1;
	int left_padding_size = 5;
	while (row_num >= 0) {
	    str.append("\n ").append(paddingGenerator(left_padding_size));
	    for (MapRoomNode node : nodes.get(row_num)) {
		String right = " ", mid = right, left = mid;
		for (MapEdge edge : node.getEdges()) {
		    if (edge.dstX < node.x)
			left = "\\";
		    if (edge.dstX == node.x)
			mid = "|";
		    if (edge.dstX > node.x)
			right = "/";
		}
		str.append(left).append(mid).append(right);
	    }
	    str.append("\n").append(row_num).append(" ");
	    str.append(paddingGenerator(left_padding_size - String.valueOf(row_num).length()));
	    for (MapRoomNode node : nodes.get(row_num)) {
		String nodeSymbol = " ";
		if (row_num == nodes.size() - 1) {
		    for (MapRoomNode lower_node : nodes.get(row_num - 1)) {
			for (MapEdge edge : lower_node.getEdges()) {
			    if (edge.dstX == node.x) {
				nodeSymbol = node.getRoomSymbol(showRoomSymbols);
			    }
			}
		    }
		} else if (node.hasEdges()) {
		    nodeSymbol = node.getRoomSymbol(showRoomSymbols);
		}
		str.append(" ").append(colorNodeSymbol(node, nodeSymbol)).append(" ");
	    }
	    row_num--;
	}
	str.append("\n");
	return str.toString();
    }

    public void displayMap() {
	sendMessage(mapToString(AbstractDungeon.map, true));

    }
    public void displayEvent(HashMap<String, Object> screenState) {
	sendMessage("Event: " + screenState.get("event_name"));
	if(screenState.get("body_text") != null && !screenState.get("body_text").equals("")) {
	    String bodyText = (String) screenState.get("body_text");
	    // Inster newline automatically
	    StringBuilder sb = new StringBuilder(bodyText);
	    int i = 0;
	    while ((i = sb.indexOf(" ", i + 70)) != -1) {
		sb.replace(i, i + 1, "\n");
	    }
	    sendMessage("\n" + sb + "\n");
	}
    }

    public void displayGameOver(HashMap<String, Object> screenState) {
	if((Boolean) screenState.get("victory")) {
	    sendMessage(colored("Victory!", ANSI_GREEN));
	} else {
	    sendMessage(colored("Game Over!", ANSI_RED));
	}
	sendMessage("Score: " + screenState.get("score"));
	HashMap<String, Object> gameState = (HashMap<String, Object>) state.get("game_state");
	sendMessage("Class: " + gameState.get("class"));
	sendMessage("Ascension level: " + gameState.get("ascension_level"));
	sendMessage("Seed: " + gameState.get("seed"));
    }

    public static String showCard(AbstractCard card, Boolean showUpgrade, Boolean shop, Boolean brief) {
	String description, block, damage, magicNumber, cardText;
	AbstractCard copy = null;
	showUpgrade = false;
	if(showUpgrade){
	    copy = card.makeStatEquivalentCopy();
	    copy.upgrade();
	}
	if (card.isBlockModified) {
	    block = Integer.toString(card.block);
	} else {
	    block = Integer.toString(card.baseBlock);
	    if(copy != null && card.baseBlock != copy.baseBlock)
		block += "(" + Integer.toString(copy.baseBlock) + ")";
	}
	if (card.isDamageModified) {
	    damage = Integer.toString(card.damage);
	} else {
	    damage = Integer.toString(card.baseDamage);
	    if(copy != null && card.baseDamage != copy.baseDamage)
		damage += "(" + Integer.toString(copy.baseDamage) + ")";
	}
	if (card.isMagicNumberModified) {
	    magicNumber = Integer.toString(card.magicNumber);
	} else {
	    magicNumber = Integer.toString(card.baseMagicNumber);
	    if(copy != null && card.baseMagicNumber != copy.baseMagicNumber)
		magicNumber += "(" + Integer.toString(copy.baseMagicNumber) + ")";
	}
	String cost;
	if(card.cost == -1)
	    cost = "X";
	else if(card.cost == -2)
	    cost = "UNPLAYABLE";
	else if(card.freeToPlay())
	    cost = "0";
	else
	    cost = Integer.toString(card.costForTurn);
	if(cost.equals("0"))
	    cost = colored(cost, ANSI_GREEN);
	cost = "Cost: " + cost;
	description = card.rawDescription;
	description = description.replaceAll("!D!", colored(damage, ANSI_RED));
	description = description.replaceAll("!B!", colored(block, ANSI_GREEN));
	description = description.replaceAll("!M!", colored(magicNumber, ANSI_YELLOW));
	String cardName = card.name;
	String cardType = String.valueOf(card.type);
	switch(card.rarity) {
	case UNCOMMON:
	    cardName = colored(cardName, ANSI_BLUE);
	    break;
	case RARE:
	    cardName = colored(cardName, ANSI_YELLOW);
	    break;
	}
	switch(card.type) {
	case CURSE:
	    cardName = colored(cardName, ANSI_PURPLE);
	    cardType = colored(cardType, ANSI_PURPLE);
	    break;
	case ATTACK:
	    cardType = colored(cardType, ANSI_RED);
	    break;
	case SKILL:
	    cardType = colored(cardType, ANSI_GREEN);
	    break;
	case POWER:
	    cardType = colored(cardType, ANSI_YELLOW);
	    break;
	}
	if(brief)
	    description = " ";
	else
	    description = " " + removeTextFormatting(description) + " ";
	cardText = "[" + cardName + "(" + cardType + ")]" + description + "[" + cost + "]";
	if(shop)
	    cardText += " (" + card.price + " gold)";
	return cardText;
    }

    public void showCards(ArrayList<AbstractCard> cards, int startingIndex, Boolean showIndex, Boolean showUpgrade, Boolean shop, Boolean brief) {
	int i = startingIndex;
	String cardText, cardResult;
	for(AbstractCard card: cards) {
	    cardText = "";
	    if(showIndex)
		cardText += Integer.valueOf(i) + ": ";
	    // Sometimes the game can't make an upgrade for obscure reasons
	    while(true) {
		try {
		    cardResult = showCard(card, showUpgrade, shop, brief);
		    break;
		} catch(Exception e) {
		    e.printStackTrace();
		}
	    }
	    cardText += cardResult;
	    sendMessage(cardText);
	    i += 1;
	}
	if(i == startingIndex)
	    sendMessage("No cards !\n");
	else {
	    sendMessage("");
	}
    }

    public void displayDeck() {
	showCards(AbstractDungeon.player.masterDeck.group, 1, true, true, false, false);
    }

    public void displayDraw() {
	CardGroup drawPileCopy = new CardGroup(CardGroup.CardGroupType.UNSPECIFIED);
	for (AbstractCard c : AbstractDungeon.player.drawPile.group)
	    drawPileCopy.addToBottom(c);
	if (!AbstractDungeon.player.hasRelic("Frozen Eye")) {
	    drawPileCopy.sortAlphabetically(true);
	    drawPileCopy.sortByRarityPlusStatusCardType(true);
	}
	showCards(drawPileCopy.group, 0, false, false, false, true);
    }
    public void displayDiscard() {
	showCards(AbstractDungeon.player.discardPile.group, 0, false, false, false, true);
    }
    public void displayExhaust() {
	showCards(AbstractDungeon.player.exhaustPile.group, 0, false, false, false, true);
    }
    public void displayHand(int cardIndex) {
	if(cardIndex == -1)
	    showCards(AbstractDungeon.player.hand.group, 1, true, false, false, false);
	else {
	    cardIndex -= 1;
	    if(cardIndex < 0 || cardIndex >= AbstractDungeon.player.hand.group.size()){
		sendMessage(colored("This CardIndex doesn't exist!", ANSI_RED));
		return;
	    }
	    sendMessage(showCard(AbstractDungeon.player.hand.group.get(cardIndex), false, false, false));
	}
    }
}
