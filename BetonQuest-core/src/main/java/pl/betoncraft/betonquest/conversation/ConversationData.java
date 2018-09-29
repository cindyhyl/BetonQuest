/**
 * BetonQuest - advanced quests for Bukkit
 * Copyright (C) 2016  Jakub "Co0sh" Sapalski
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package pl.betoncraft.betonquest.conversation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import pl.betoncraft.betonquest.BetonQuest;
import pl.betoncraft.betonquest.ConditionID;
import pl.betoncraft.betonquest.EventID;
import pl.betoncraft.betonquest.InstructionParseException;
import pl.betoncraft.betonquest.ObjectNotFoundException;
import pl.betoncraft.betonquest.config.Config;
import pl.betoncraft.betonquest.config.ConfigPackage;
import pl.betoncraft.betonquest.utils.Debug;

/**
 * Represents the data of the conversation.
 * 
 * @author Jakub Sapalski
 */
public class ConversationData {

	private static ArrayList<String> externalPointers = new ArrayList<>();

	private ConfigPackage pack;
	private String convName;

	private HashMap<String, String> quester = new HashMap<>(); // maps for multiple languages
	private HashMap<String, String> prefix = new HashMap<>(); // global conversation prefix
	private EventID[] finalEvents;
	private String[] startingOptions;
	private boolean blockMovement;
	private String convIO;

	private HashMap<String, Option> NPCOptions;
	private HashMap<String, Option> playerOptions;

	/**
	 * Loads conversation from package.
	 * 
	 * @param pack
	 *            the package containing this conversation
	 * @param name
	 *            the name of the conversation
	 * @throws InstructionParseException
	 *             when there is a syntax error in the defined conversation
	 */
	public ConversationData(ConfigPackage pack, String name) throws InstructionParseException {
		this.pack = pack;
		String pkg = pack.getName();
		Debug.info(String.format("Loading %s conversation from %s package", name, pkg));
		// package and name must be correct, it loads only existing
		// conversations
		convName = name;
		// get the main data
		FileConfiguration conv = pack.getConversation(name).getConfig();
		if (conv.isConfigurationSection("quester")) {
			for (String lang : conv.getConfigurationSection("quester").getKeys(false)) {
				quester.put(lang, pack.getString("conversations." + name + ".quester." + lang));
			}
		} else {
			quester.put(Config.getLanguage(), pack.getString("conversations." + name + ".quester"));
		}
		if (conv.isConfigurationSection("prefix")) {
			for (String lang : conv.getConfigurationSection("prefix").getKeys(false)) {
				String pref = pack.getString("conversations." + name + ".prefix." + lang);
				if (pref != null && !pref.equals("")) {
					prefix.put(lang, pref);
				}
			}
		} else {
			String pref = pack.getString("conversations." + name + ".prefix");
			if (pref != null && !pref.equals("")) {
				prefix.put(Config.getLanguage(), pref);
			}
		}
		String rawFinalEvents = pack.getString("conversations." + name + ".final_events");
		String rawStartingOptions = pack.getString("conversations." + name + ".first");
		String stop = pack.getString("conversations." + name + ".stop");
		blockMovement = stop != null && stop.equalsIgnoreCase("true");
		convIO = pack.getString("conversations." + name + ".conversationIO");
		if (convIO == null) {
			convIO = BetonQuest.getInstance().getConfig().getString("default_conversation_IO");
		}
		// check if all data is valid (or at least exist)
		if (BetonQuest.getInstance().getConvIO(convIO) == null) {
			throw new InstructionParseException("Conversation IO " + convIO + " is not registered!");
		}
		if (quester == null || quester.isEmpty()) {
			throw new InstructionParseException("Quester's name is not defined");
		}
		for (String value : quester.values()) {
			if (value == null) throw new InstructionParseException("Quester's name is not defined");
		}
		if (rawStartingOptions == null || rawStartingOptions.equals("")) {
			throw new InstructionParseException("Starting options are not defined");
		}
		if (rawFinalEvents != null && !rawFinalEvents.equals("")) {
			String[] array = rawFinalEvents.split(",");
			finalEvents = new EventID[array.length];
			for (int i = 0; i < array.length; i++) {
				try {
					finalEvents[i] = new EventID(pack, array[i]);
				} catch (ObjectNotFoundException e) {
					throw new InstructionParseException("Error while loading final events: " + e.getMessage());
				}
			}
		} else {
			finalEvents = new EventID[0];
		}
		// load all NPC options
		ConfigurationSection NPCSection = pack.getConversation(name).getConfig().getConfigurationSection("NPC_options");
		if (NPCSection == null) {
			throw new InstructionParseException("NPC_options section not defined");
		}
		NPCOptions = new HashMap<>();
		for (String key : NPCSection.getKeys(false)) {
			NPCOptions.put(key, new NPCOption(key));
		}
		// check if all starting options point to existing NPC options
		startingOptions = rawStartingOptions.split(",");
		// remove spaces between the options
		for (int i = 0; i < startingOptions.length; i++) {
			startingOptions[i] = startingOptions[i].trim();
		}
		for (String startingOption : startingOptions) {
			if (startingOption.contains(".")) {
				String entirePointer = pack.getName() + "." + convName + ".<starting_option>."
						+ startingOption;
				externalPointers.add(entirePointer);
			} else if (!NPCOptions.containsKey(startingOption)) {
				throw new InstructionParseException("Starting option " + startingOption + " does not exist");
			}
		}
		// load all Player options
		ConfigurationSection playerSection = pack.getConversation(name).getConfig()
				.getConfigurationSection("player_options");
		playerOptions = new HashMap<>();
		if (playerSection != null) {
			for (String key : playerSection.getKeys(false)) {
				playerOptions.put(key, new PlayerOption(key));
			}
		}

		// check if every pointer points to existing option.
		for (Option option : NPCOptions.values()) {
			for (String pointer : option.getPointers()) {
				if (!playerOptions.containsKey(pointer)) {
					throw new InstructionParseException(
							String.format("NPC option %s points to %s player option, but it does not exist",
									option.getName(), pointer));
				}
			}
			for (String include : option.getIncludes()) {
				if (!NPCOptions.containsKey(include)) {
					throw new InstructionParseException(
							String.format("NPC option %s includes %s, but it does not exist",
									option.getName(), include));
				}
			}
		}
		for (Option option : playerOptions.values()) {
			for (String pointer : option.getPointers()) {
				if (pointer.contains(".")) {
					String entirePointer = pack.getName() + "." + convName + "." + option.getName() + "." + pointer;
					externalPointers.add(entirePointer);
				} else if (!NPCOptions.containsKey(pointer)) {
					throw new InstructionParseException(
							String.format("Player option %s points to %s NPC option, but it does not exist",
									option.getName(), pointer));
				}
			}
			for (String include : option.getIncludes()) {
				if (!playerOptions.containsKey(include)) {
					throw new InstructionParseException(
							String.format("Player option %s includes %s, but it does not exist",
									option.getName(), include));
				}
			}
		}

		// done, everything will work
		Debug.info(String.format("Conversation loaded: %d NPC options and %d player options", NPCOptions.size(),
				playerOptions.size()));
	}

	/**
	 * @return the name of this conversation
	 */
	public String getName() {
		return convName;
	}

	/**
	 * Gets the prefix of the conversation. If provided NPC option does not
	 * define one, the global one from the conversation is returned instead.
	 * 
	 * @param lang
	 *            language of the prefix
	 * @param option
	 *            the quest starting npc option that defines the prefix of the
	 *            conversation
	 * @return the conversation prefix, or null if not defined
	 */
	public String getPrefix(String lang, String option) {
		// get prefix from an option
		if (option != null) {
			String pref = NPCOptions.get(option).getInlinePrefix(lang);
			if (pref == null) {
				pref = NPCOptions.get(option).getInlinePrefix(Config.getLanguage());
			}
			if (pref != null)
				return pref;
		}

		// otherwise return global prefix
		String global = prefix.get(lang);
		if (global == null) {
			global = prefix.get(Config.getLanguage());
		}
		return global;
	}

	/**
	 * @param lang
	 *            language of quester's name
	 * @return the quester's name
	 */
	public String getQuester(String lang) {
		String text = quester.get(lang);
		if (text == null) {
			text = quester.get(Config.getLanguage());
		}
		return text;
	}

	/**
	 * @return the final events
	 */
	public EventID[] getFinalEvents() {
		return finalEvents;
	}

	/**
	 * @return the starting options
	 */
	public String[] getStartingOptions() {
		return startingOptions;
	}

	/**
	 * @return true if movement should be blocked
	 */
	public boolean isMovementBlocked() {
		return blockMovement;
	}
	
	/**
	* @return the conversationIO
	*/
	public String getConversationIO() {
		return convIO;
	}
	
	public String getText(String lang, String option, OptionType type) {
		Option o = null;
		if (type == OptionType.NPC) {
			o = NPCOptions.get(option);
		} else {
			o = playerOptions.get(option);
		}
		if (o == null)
			return null;
		return o.getText(lang);
	}

	/**
	 * @return the name of the package
	 */
	public String getPackName() {
		return pack.getName();
	}
	
	public ConditionID[] getConditionIDs(String option, OptionType type) {
		HashMap<String, Option> options;
		if (type == OptionType.NPC) {
			options = NPCOptions;
		} else {
			options = playerOptions;
		}
		return options.get(option).getConditions();
	}
	
	public EventID[] getEventIDs(String option, OptionType type) {
		HashMap<String, Option> options;
		if (type == OptionType.NPC) {
			options = NPCOptions;
		} else {
			options = playerOptions;
		}
		return options.get(option).getEvents();
	}
	
	public String[] getPointers(String option, OptionType type) {
		HashMap<String, Option> options;
		if (type == OptionType.NPC) {
			options = NPCOptions;
		} else {
			options = playerOptions;
		}
		return options.get(option).getPointers();
	}

	/**
	 * Check if conversation has at least one valid option for player
	 */
	public boolean isReady(String playerID) {
		options:
		for (String option : getStartingOptions()) {
			String convName, optionName;
			if (option.contains(".")) {
				String[] parts = option.split("\\.");
				convName = parts[0];
				optionName = parts[1];
			} else {
				convName = getName();
				optionName = option;
			}
			ConfigPackage pack = Config.getPackages().get(getPackName());
			ConversationData currentData = BetonQuest.getInstance().getConversation(pack.getName() + "." + convName);
			for (ConditionID condition : currentData.getConditionIDs(optionName, ConversationData.OptionType.NPC)) {
				if (!BetonQuest.condition(playerID, condition)) {
					continue options;
				}
			}
			return true;
		}
		return false;
	}

	/**
	 * Checks if external pointers point to valid options. It cannot be checked
	 * when constructing ConversationData objects because conversations that are
	 * being pointed to may not yet exist.
	 * 
	 * This method should be called when all conversations are loaded. It will
	 * not throw any exceptions, just display errors in the console.
	 */
	public static void postEnableCheck() {
		for (String externalPointer : externalPointers) {
			String[] parts = externalPointer.split("\\.");
			String packName = parts[0];
			String sourceConv = parts[1];
			String sourceOption = parts[2];
			String targetConv = parts[3];
			String targetOption = parts[4];
			ConversationData conv = BetonQuest.getInstance().getConversation(packName + "." + targetConv);
			if (conv == null) {
				Debug.error("External pointer in '" + packName + "' package, '" + sourceConv + "' conversation, "
						+ ((sourceOption.equals("<starting_option>")) ? "starting option"
								: ("'" + sourceOption + "' player option"))
						+ " points to '" + targetConv
						+ "' conversation, but it does not even exist. Check your spelling!");
				continue;
			}
			if (conv.getText(Config.getLanguage(), targetOption, OptionType.NPC) == null) {
				Debug.error("External pointer in '" + packName + "' package, '" + sourceConv + "' conversation, "
						+ ((sourceOption.equals("<starting_option>")) ? "starting option"
								: ("'" + sourceOption + "' player option"))
						+ " points to '" + targetOption + "' NPC option in '" + targetConv
						+ "' conversation, but it does not exist.");
			}
		}
		externalPointers.clear();
	}

	/**
	 * Represents an option
	 */
	private abstract class Option {

		private String name;
		private HashMap<String, String> inlinePrefix = new HashMap<>();

		private HashMap<String, String> text = new HashMap<>();
		private List<ConditionID> conditions;
		private List<EventID> events;
		private List<String> pointers;
		private List<String> includes = new ArrayList<>();

		public Option(String name, String type, String visibleType) throws InstructionParseException {
			this.name = name;
			String defaultLang = Config.getLanguage();
			ConfigurationSection conv = pack.getConversation(convName).getConfig().getConfigurationSection(type + "." + name);

			// Prefix
			if (conv.contains("prefix")) {
				if (conv.isConfigurationSection("prefix")) {
					for (String lang : conv.getConfigurationSection("prefix").getKeys(false)) {
						String pref = conv.getConfigurationSection("prefix").getString(lang);
						if (pref != null && !pref.equals("")) {
							inlinePrefix.put(lang, pref);
						}
					}
					if (!inlinePrefix.containsKey(defaultLang)) {
						throw new InstructionParseException("No default language for " + name + " " + visibleType
								+ " prefix");
					}
				} else {
					String pref = conv.getString("prefix");
					if (pref != null && !pref.equals("")) {
						inlinePrefix.put(defaultLang, pref);
					}
				}
			}

			// Text
			if (conv.contains("text")) {
				if (conv.isConfigurationSection("text")) {
					for (String lang : conv.getConfigurationSection("text").getKeys(false)) {
						text.put(lang, conv.getConfigurationSection("text").getString(lang).replace("\\n", "\n"));
					}
					if (!text.containsKey(defaultLang)) {
						throw new InstructionParseException("No default language for " + name + " " + visibleType);
					}
				} else {
					text.put(defaultLang, conv.getString("text").replace("\\n", "\n"));
				}

				ArrayList<String> variables = new ArrayList<>();
				for (String theText : text.values()) {
					if (theText == null || theText.equals(""))
						throw new InstructionParseException("Text not defined in " + visibleType + " " + name);
					// variables are possibly duplicated because there probably is
					// the same variable in every language
					ArrayList<String> possiblyDuplicatedVariables = BetonQuest.resolveVariables(theText);
					for (String possiblyDuplicatedVariable : possiblyDuplicatedVariables) {
						if (variables.contains(possiblyDuplicatedVariable))
							continue;
						variables.add(possiblyDuplicatedVariable);
					}
				}
				for (String variable : variables) {
					try {
						BetonQuest.createVariable(pack, variable);
					} catch (InstructionParseException e) {
						throw new InstructionParseException("Error while creating '" + variable + "' variable: "
								+ e.getMessage());
					}
				}
			}

			// Conditions
			if (conv.contains("conditions") || conv.contains("condition")) {
				conditions = new ArrayList<>();
				List<String> rawConditions = new ArrayList<>();

				rawConditions.addAll(Arrays.asList(conv.getString("conditions", "").split(",")));
				rawConditions.addAll(Arrays.asList(conv.getString("condition", "").split(",")));

				try {
					for (String rawCondition : rawConditions) {
						conditions.add(new ConditionID(pack, rawCondition.trim()));
					}
				} catch (ObjectNotFoundException e) {
					throw new InstructionParseException("Error in '" + name + "' " + visibleType + " option's conditions: "
							+ e.getMessage());
				}
			}

			// Events
			if (conv.contains("events") || conv.contains("event")) {
				events = new ArrayList<>();
				List<String> rawEvents = new ArrayList<>();

				rawEvents.addAll(Arrays.asList(conv.getString("events", "").split(",")));
				rawEvents.addAll(Arrays.asList(conv.getString("event", "").split(",")));

				try {
					for (String rawEvent : rawEvents) {
						events.add(new EventID(pack, rawEvent.trim()));
					}
				} catch (ObjectNotFoundException e) {
					throw new InstructionParseException("Error in '" + name + "' " + visibleType + " option's events: "
							+ e.getMessage());
				}
			}

			// Pointers
			if (conv.contains("pointers") || conv.contains("pointer")) {
				pointers = new ArrayList<>();

				pointers.addAll(Arrays.asList(conv.getString("pointers", "").split(",")));
				pointers.addAll(Arrays.asList(conv.getString("pointer", "").split(",")));
			}

			includes.addAll(Arrays.asList(pack.getString("conversations." + convName + "." + type + "." + name + "includes", "").split(",")));
			includes.addAll(Arrays.asList(pack.getString("conversations." + convName + "." + type + "." + name + "include", "").split(",")));
		}

		public String getName() {
			return name;
		}

		public String getInlinePrefix(String lang) {
			String thePrefix = inlinePrefix.get(lang);
			if (thePrefix == null) {
				thePrefix = inlinePrefix.get(Config.getLanguage());
			}
			return thePrefix;
		}

		public String getText(String lang) {
			String theText = text.get(lang);
			if (theText == null) {
				theText = text.get(Config.getLanguage());
			}

			return theText;
		}

		public ConditionID[] getConditions() {
			return conditions.toArray(new ConditionID[0]);
		}

		public EventID[] getEvents() {
			return events.toArray(new EventID[0]);
		}

		public String[] getPointers() {
			return pointers.toArray(new String[0]);
		}

		public String[] getIncludes() {
			return includes.toArray(new String[0]);
		}
	}

	/**
	 * Represents an option which can be choosen by the Player
	 */
	private class PlayerOption extends Option {
		public PlayerOption(String name) throws InstructionParseException {
			super(name, "player_options", "player option");
		}
	}

	/**
	 * Represents an option which can be choosen by the NPC
	 */
	private class NPCOption extends Option {
		public NPCOption(String name) throws InstructionParseException {
			super(name, "NPC_options", "NPC option");
		}
	}

	public static enum OptionType {
		NPC, PLAYER
	}
}
