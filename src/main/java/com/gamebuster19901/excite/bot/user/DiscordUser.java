package com.gamebuster19901.excite.bot.user;

import java.io.IOError;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import com.gamebuster19901.excite.Main;
import com.gamebuster19901.excite.Player;
import com.gamebuster19901.excite.Wiimmfi;
import com.gamebuster19901.excite.bot.audit.RankChangeAudit;
import com.gamebuster19901.excite.bot.audit.ban.Ban;
import com.gamebuster19901.excite.bot.audit.ban.Banee;
import com.gamebuster19901.excite.bot.command.ConsoleContext;
import com.gamebuster19901.excite.bot.command.MessageContext;
import com.gamebuster19901.excite.bot.database.sql.DatabaseConnection;
import com.gamebuster19901.excite.bot.database.Comparison;
import com.gamebuster19901.excite.bot.database.Result;
import com.gamebuster19901.excite.bot.database.Table;
import com.gamebuster19901.excite.bot.database.sql.PreparedStatement;
import com.gamebuster19901.excite.bot.server.DiscordServer;
import com.gamebuster19901.excite.util.StacktraceUtil;
import com.gamebuster19901.excite.util.TimeUtils;

import static com.gamebuster19901.excite.bot.database.Comparator.*;
import static com.gamebuster19901.excite.bot.database.Table.DISCORD_USERS;
import static com.gamebuster19901.excite.bot.database.Table.PLAYERS;
import static com.gamebuster19901.excite.bot.database.Table.WIIS;

import static com.gamebuster19901.excite.bot.database.Column.*;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.PrivateChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;

public class DiscordUser implements Banee {
	
	private static final Set<DesiredProfile> desiredProfiles = Collections.newSetFromMap(new ConcurrentHashMap<DesiredProfile, Boolean>());
	
	protected transient DatabaseConnection connection;
	
	private final long discordId;
	
	public DiscordUser(Result results) throws SQLException {
		this.discordId = results.getLong(DISCORD_ID);
	}
	
	protected DiscordUser(long userId) {
		this.discordId = userId;
	}
	
	
	public static void addUser(User user) {
		if(getDiscordUserIncludingUnknown(ConsoleContext.INSTANCE, user.getIdLong()) instanceof UnknownDiscordUser) {
			try {
				addDiscordUser(ConsoleContext.INSTANCE, user.getIdLong(), user.getAsTag());
			} catch (SQLException e) {
				throw new AssertionError("Unable to add new discord user " + user.getAsTag() + "(" + user.getIdLong() + ")", e);
			}
		}
	}
	
	@SuppressWarnings("rawtypes")
	private static DiscordUser addDiscordUser(MessageContext context, long discordID, String name) throws SQLException {
		PreparedStatement ps = null;
		try {
			ps = context.getConnection().prepareStatement("INSERT INTO " + DISCORD_USERS + " (" + DISCORD_ID + ", "+ DISCORD_NAME + ", " + LAST_NOTIFICATION + ") VALUES (?, ?, ?);");
			
			Table.insertValue(ps, 1, discordID);
			Table.insertValue(ps, 2, name);
			Table.insertValue(ps, 3, TimeUtils.PLAYER_EPOCH);
			
			ps.execute();
		}
		catch(Exception e) {
			ConsoleUser.getConsoleUser().sendMessage(ps.toString());
			throw new IOError(e);
		}
		return getDiscordUser(context, discordID);
	}
	
	@Nullable
	public User getJDAUser() {
		User user = Main.discordBot.jda.retrieveUserById(discordId).complete();
		if(user == null) {
			System.out.println("Could not find JDA user for " + discordId);
		}
		return user;
	}
	
	@Override
	public long getID() {
		return discordId;
	}
	
	public Member getMember(DiscordServer server) {
		return getMember(this, server);
	}
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public Ban ban(MessageContext context, Duration duration, String reason) {
		Ban discordBan = Ban.addBan(context, this, reason, duration);
		sendMessage(context, this.toDetailedString() + " has been banned for " + TimeUtils.readableDuration(duration) + " with the reason: \n\n\"" + reason + "\"");
		sendMessage(new MessageContext(this), this.getName() + " " + reason);
		return discordBan;
	}
	
	@SuppressWarnings("rawtypes")
	public Set<Player> getProfiles(MessageContext context) {
		try {
			HashSet<Player> players = new HashSet<Player>();
			Result results = Table.selectColumnsFromWhere(ConsoleContext.INSTANCE, PLAYER_ID, PLAYERS, new Comparison(DISCORD_ID, EQUALS, getID()));
			while(results.next()) {
				players.add(Player.getPlayerByID(context, results.getInt(PLAYER_ID)));
			}
			return players;
		}
		catch(SQLException e) {
			throw new IOError(e);
		}
	}

	public boolean isBanned() {
		for(Ban ban : Ban.getBansOf(ConsoleContext.INSTANCE, this)) {
			if(ban.isActive()) {
				return true;
			}
		}
		return false;
	}
	
	public boolean isAdmin() {
		try {
			if(isOperator()) {
				return true;
			}
			Result result = Table.selectAllFromWhere(ConsoleContext.INSTANCE, Table.ADMINS, new Comparison(DISCORD_ID, EQUALS, getID()));
			return result.next();
		}
		catch(SQLException e) {
			throw new IOError(e);
		}
	}
	
	public boolean isOperator() {
		try {
			Result result = Table.selectAllFromWhere(ConsoleContext.INSTANCE, Table.OPERATORS, new Comparison(DISCORD_ID, EQUALS, getID()));
			return result.next();
		}
		catch(SQLException e) {
			throw new IOError(e);
		}
	}
	
	@SuppressWarnings("rawtypes")
	public void setAdmin(MessageContext promoter, boolean admin) {
		if(admin) {
			Table.addAdmin(promoter, this);
		}
		else {
			Table.removeAdmin(promoter, this);
		}
		RankChangeAudit.addRankChange(promoter, this, "admin", admin);
	}
	
	@SuppressWarnings("rawtypes")
	public void setOperator(MessageContext promoter, boolean operator) {
		if(operator) {
			setAdmin(promoter, operator);
			Table.addOperator(promoter, this);
		}
		else {
			Table.removeOperator(promoter, this);
		}
		RankChangeAudit.addRankChange(promoter, this, "operator", operator);
	}
	
	public String getName() {
		return getJDAUser().getAsTag();
	}
	
	public int getNotifyThreshold() {
		try {
			Result result = Table.selectColumnsFromWhere(ConsoleContext.INSTANCE, THRESHOLD, DISCORD_USERS, new Comparison(DISCORD_ID, EQUALS, getID()));
			if(result.next()) {
				return result.getInt(THRESHOLD);
			}
			else {
				throw new AssertionError("Could not find threshold for discord user " + discordId);
			}
		} catch (SQLException e) {
			throw new IOError(e);
		}
	}
	
	public void setNotifyThreshold(int threshold) {
		if(threshold > 0 || threshold == -1) {
			try {
				Table.updateWhere(ConsoleContext.INSTANCE, DISCORD_USERS, THRESHOLD, threshold, new Comparison(DISCORD_ID, EQUALS, getID()));
			} catch (SQLException e) {
				throw new IOError(e);
			}
		}
		else {
			throw new IndexOutOfBoundsException(threshold + " < 1");
		}
	}
	
	public Duration getNotifyFrequency() {
		try {
			Result result = Table.selectColumnsFromWhere(ConsoleContext.INSTANCE, FREQUENCY, DISCORD_USERS, new Comparison(DISCORD_ID, EQUALS, getID()));
			if(result.next()) {
				return Duration.parse(result.getString(FREQUENCY));
			}
			else {
				throw new AssertionError("Could not get notification frequency for " + discordId);
			}
		} catch (SQLException e) {
			throw new IOError(e);
		}
	}
	
	public void setNotifyFrequency(Duration frequency) {
		Duration min = Duration.ofMinutes(5);
		if(frequency.compareTo(min) > -1) {
			try {
				Table.updateWhere(ConsoleContext.INSTANCE, DISCORD_USERS, FREQUENCY, frequency, new Comparison(DISCORD_ID, EQUALS, getID()));
			} catch (SQLException e) {
				throw new IOError(e);
			}
		}
		else {
			throw new IllegalArgumentException("Frequency is less than 5 minutes!");
		}
	}
	
	public boolean isNotifyingContinuously() {
		try {
			Result result = Table.selectColumnsFromWhere(ConsoleContext.INSTANCE, NOTIFY_CONTINUOUSLY, DISCORD_USERS, new Comparison(DISCORD_ID, EQUALS, getID()));
			if(result.next()) {
				return result.getBoolean(NOTIFY_CONTINUOUSLY);
			}
			throw new AssertionError("Could not get notification frequency for " + discordId);
		} catch (SQLException e) {
			throw new IOError(e);
		} 
	}
	
	public void setNotifyContinuously(boolean continuous) {
		int value = continuous ? 1 : 0;
		try {
			Table.updateWhere(ConsoleContext.INSTANCE, DISCORD_USERS, NOTIFY_CONTINUOUSLY, value, new Comparison(DISCORD_ID, EQUALS, getID()));
			if(continuous) {
				setDippedBelowThreshold(false);
			}
		} catch (SQLException e) {
			throw new IOError(e);
		}
	}
	
	public Instant getLastNotification() {
		try {
			Result result = Table.selectColumnsFromWhere(ConsoleContext.INSTANCE, LAST_NOTIFICATION, DISCORD_USERS, new Comparison(DISCORD_ID, EQUALS, getID()));
			if(result.next()) {
				return TimeUtils.parseInstant(result.getString(LAST_NOTIFICATION));
			}
			throw new AssertionError("Could not get last notification of " + discordId);
		} catch (SQLException e) {
			throw new IOError(e);
		}
	}
	
	public void setLastNotification() {
		setLastNotification(Instant.now());
	}
	
	public void setLastNotification(Instant instant) {
		try {
			Table.updateWhere(ConsoleContext.INSTANCE, DISCORD_USERS, LAST_NOTIFICATION, instant.toString(), new Comparison(DISCORD_ID, EQUALS, getID()));
		} catch (SQLException e) {
			throw new IOError(e);
		}
	}
	
	public boolean dippedBelowThreshold() {
		try {
			Result result = Table.selectColumnsFromWhere(ConsoleContext.INSTANCE, BELOW_THRESHOLD, DISCORD_USERS, new Comparison(DISCORD_ID, EQUALS, getID()));
			if(result.next()) {
				return result.getBoolean(BELOW_THRESHOLD);
			}
			throw new AssertionError("Could not get theshold state of " + discordId);
		} catch (SQLException e) {
			throw new IOError(e);
		}
	}
	
	public void setDippedBelowThreshold(boolean dippedBelow) {
		try {
			Table.updateWhere(ConsoleContext.INSTANCE, DISCORD_USERS, BELOW_THRESHOLD, dippedBelow, new Comparison(DISCORD_ID, EQUALS, getID()));
		} catch (SQLException e) {
			throw new IOError(e);
		}
	}
	
	@Nullable
	public Wii[] getRegisteredWiis() {
		try {
			HashSet<Wii> wiis = new HashSet<Wii>();
			Result result = Table.selectColumnsFromWhere(ConsoleContext.INSTANCE, WII_ID, WIIS, new Comparison(DISCORD_ID, EQUALS, getID()));
			while(result.next()) {
				wiis.add(Wii.getWii(result.getString(WII_ID)));
			}
			return (Wii[]) wiis.toArray(new Wii[] {});
		} catch(SQLException e) {
			throw new IOError(e);
		}
	}
	
	public boolean sendDetailedPM() {
		try {
			Result result = Table.selectColumnsFromWhere(ConsoleContext.INSTANCE, DETAILED_PM, DISCORD_USERS, new Comparison(DISCORD_ID, EQUALS, getID()));
			if(result.next()) {
				return result.getBoolean(DETAILED_PM);
			}
			throw new AssertionError("Could not get desired PM output of " + discordId);
		} catch (SQLException e) {
			throw new IOError(e);
		}
	}
	
	public void setSendDetailedPM(boolean sendFullPM) {
		try {
			Table.updateWhere(ConsoleContext.INSTANCE, DISCORD_USERS, DETAILED_PM, sendFullPM, new Comparison(DISCORD_ID, EQUALS, getID()));
		} catch(SQLException e) {
			throw new IOError(e);
		}
	}
	
	public String requestRegistration(Player profile) {
		DesiredProfile desiredProfile = new DesiredProfile(this, profile);
		if(desiredProfiles.contains(desiredProfile)) {
			throw new IllegalStateException(profile.toString() + " is already being registered, please wait.");
		}
		else {
			desiredProfiles.add(desiredProfile);
			return desiredProfile.getRegistrationCode();
		}
	}
	
	public boolean requestingRegistration() {
		for(DesiredProfile profile : desiredProfiles) {
			if(profile.getRequester().equals(this)) {
				return true;
			}
		}
		return false;
	}
	
	public void clearRegistration() {
		for(DesiredProfile profile : desiredProfiles) {
			if(profile.getRequester() == this) {
				desiredProfiles.remove(profile);
			}
		}
	}
	
	@SuppressWarnings("rawtypes")
	public void sentCommand(MessageContext context, int amount) {
		
	}
	
	public Message sendMessage(MessageEmbed message) {
		if(Main.discordBot != null && !getJDAUser().equals(Main.discordBot.jda.getSelfUser())) {
			if(!getJDAUser().isBot()) {
				PrivateChannel privateChannel = getJDAUser().openPrivateChannel().complete();
				try {
					return privateChannel.sendMessage(message).complete();
				}
				catch(ErrorResponseException e) {
					System.out.println(this.toDetailedString());
					e.printStackTrace();
				}
			}
		}
		return null;
	}
	
	public void sendMessage(String message) {
		if(Main.discordBot != null && !getJDAUser().equals(Main.discordBot.jda.getSelfUser())) {
			if(!getJDAUser().isBot()) {
				PrivateChannel privateChannel = getJDAUser().openPrivateChannel().complete();
				try {
					privateChannel.sendMessage(message).complete();
				}
				catch(ErrorResponseException e) {
					System.out.println(this.toDetailedString());
					e.printStackTrace();
				}
			}
		}
	}
	
	@SuppressWarnings("rawtypes")
	public void sendMessage(MessageContext context, String message) {
		if(context.isGuildMessage()) {
			GuildMessageReceivedEvent e = (GuildMessageReceivedEvent) context.getEvent();
			User receiver = this.getJDAUser();
			if(e.getGuild().isMember(receiver)) {
				if(e.getChannel().canTalk(e.getGuild().getMember(receiver))) {
					e.getChannel().sendMessage(message).complete();
					return;
				}
			}
		}
		sendMessage(message);
	}
	
	@Override
	public int hashCode() {
		return Long.valueOf(discordId).hashCode();
	}
	
	@Override
	public boolean equals(Object o) {
		if(o instanceof DiscordUser) {
			return ((DiscordUser) o).discordId == discordId;
		}
		return false;
	}
	
	@Override
	public String toString() {
		return this.getJDAUser().getAsTag();
	}
	
	@Override
	public String getLookingForMatch() {
		return toString();
	}
	
	public String toDetailedString() {
		return this + " (" + discordId + ")";
	}
	
	public String getMySQLUsername() {
		return "DiscordUser#" + discordId;
	}
	
	public static final User getJDAUser(long id) {
		return Main.discordBot.jda.retrieveUserById(id).complete();
	}
	
	public static final User getJDAUser(String name, String discriminator) {
		if(Main.discordBot != null) {
			User user = Main.discordBot.jda.getUserByTag(name, discriminator);
			return user;
		}
		return null;
	}
	
	public static final User getJDAUser(String discriminator) {
		if(Main.discordBot != null) {
			return Main.discordBot.jda.getUserByTag(discriminator);
		}
		return null;
	}
	
	public static final Member getMember(String name, long id, DiscordServer server) {
		return server.getGuild().getMemberById(id);
	}
	
	public static final Member getMember(String name, String discriminator, DiscordServer server) {
		return server.getGuild().getMemberByTag(name, discriminator);
	}
	
	public static final Member getMember(String discriminator, DiscordServer server) {
		return server.getGuild().getMemberByTag(discriminator);
	}
	
	public static final Member getMember(DiscordUser user, DiscordServer server) {
		return server.getGuild().getMember(user.getJDAUser());
	}
	
	@Deprecated
	@SuppressWarnings("rawtypes")
	public static final DiscordUser getDiscordUser(MessageContext context, long id) {
		try {
			Result results = Table.selectAllFromWhere(context, DISCORD_USERS, new Comparison(DISCORD_ID, EQUALS, id));
			
			if(results.next()) {
				return new DiscordUser(results.getLong(DISCORD_ID));
			}
			return null;
		}
		catch(SQLException e) {
			throw new IOError(e);
		}
	}
	
	@SuppressWarnings("rawtypes")
	public static final DiscordUser getDiscordUserIncludingUnknown(MessageContext context, long id) {
		DiscordUser user;
		user = getDiscordUser(context, id);
		if(user == null) {
			user = new UnknownDiscordUser(id);
		}
		return user;
	}
	
	@SuppressWarnings("rawtypes")
	public static final DiscordUser getDiscordUserIncludingUnknown(MessageContext context, String discriminator) {
		DiscordUser user;
		user = getDiscordUser(context, discriminator);
		if(user == null) {
			if(discriminator.contains("#")) {
				user = new UnknownDiscordUser(discriminator.substring(0, discriminator.indexOf('#')), discriminator.substring(discriminator.indexOf('#') + 1, discriminator.length()));
			}
			else {
				user = new UnknownDiscordUser(discriminator, "????");
			}
		}
		return user;
	}
	
	@SuppressWarnings("rawtypes")
	public static final DiscordUser getDiscordUserTreatingUnknownsAsNobody(MessageContext context, long id) {
		DiscordUser user;
		user = getDiscordUser(context, id);
		if(user == null) {
			user = Nobody.INSTANCE;
		}
		return user;
	}
	
	@SuppressWarnings("rawtypes")
	public static final DiscordUser getDiscordUser(MessageContext context, String username) {
		try {
			Result results = Table.selectAllFromWhere(context, DISCORD_USERS, new Comparison(DISCORD_NAME, EQUALS, Table.makeSafe(username)));
			if(results.next()) {
				return new DiscordUser(results.getLong(DISCORD_ID));
			}
			return null;
		} catch (SQLException e) {
			throw new IOError(e);
		}
	}
	
	@SuppressWarnings("rawtypes")
	public static final DiscordUser getDiscordUser(MessageContext context, String username, String discriminator) {
		try {
			Result results = Table.selectAllFromWhere(context, DISCORD_USERS, new Comparison(DISCORD_NAME, EQUALS, Table.makeSafe(username) + "#" + Table.makeSafe(discriminator)));
			if(results.next()) {
				return new DiscordUser(results.getLong(DISCORD_ID));
			}
			return null;
		} catch (SQLException e) {
			throw new IOError(e);
		}
	}
	
	@Deprecated
	@SuppressWarnings("rawtypes")
	public static final DiscordUser[] getDiscordUsersWithUsername(MessageContext context, String username) {
		try {
			ArrayList<DiscordUser> users = new ArrayList<DiscordUser>();
			Result results = Table.selectAllFromWhere(context, DISCORD_USERS, new Comparison(DISCORD_NAME, LIKE, Table.makeSafe(username) + "_____"));
			while(results.next()) {
				users.add(new DiscordUser(results));
			}
			return users.toArray(new DiscordUser[]{});
		}
		catch(SQLException e) {
			throw new IOError(e);
		}
	}
	
	@SuppressWarnings("rawtypes")
	public static final DiscordUser[] getDiscordUsersWithUsernameOrID(MessageContext context, String usernameOrID) {
		try {
			if(usernameOrID.indexOf("#") != -1) {
				if(usernameOrID.length() > usernameOrID.indexOf('#')) {
					String name = usernameOrID.substring(0, usernameOrID.indexOf('#'));
					String discriminator = usernameOrID.substring(usernameOrID.indexOf('#') + 1, usernameOrID.length());
					DiscordUser user = getDiscordUser(context, name, discriminator);
					if(user != null) {
						return new DiscordUser[] {user};
					}
				}
				return new DiscordUser[0];
			}
			ArrayList<DiscordUser> users = new ArrayList<DiscordUser>();
			Result results = Table.selectAllFromWhere(context, DISCORD_USERS, new Comparison(DISCORD_NAME, LIKE, Table.makeSafe(usernameOrID) + "_____").or(new Comparison(DISCORD_ID, EQUALS, Table.makeSafe(usernameOrID))));
			while(results.next()) {
				users.add(new DiscordUser(results));
			}
			return users.toArray(new DiscordUser[]{});
		}
		catch(SQLException e) {
			throw new IOError(e);
		}
	}
	
	public static final void messageAllAdmins(String message) {
		HashSet<DiscordUser> admins = new HashSet<DiscordUser>();
		admins.addAll(Arrays.asList(getAllAdmins()));
		admins.addAll(Arrays.asList(getAllOperators()));
		for(DiscordUser admin : admins) {
			admin.sendMessage(message);
		}
	}
	
	public static final void messageAllOperators(String message) {
		for(DiscordUser operator : getAllOperators()) {
			operator.sendMessage(message);
		}
	}
	
	public static DiscordUser[] getAllAdmins() {
		try {
			PreparedStatement st = ConsoleContext.INSTANCE.getConnection().prepareStatement("SELECT * FROM discord_users INNER JOIN admins ON(discord_users.discordID = admins.discordID);");
			Result results = st.query();
			int columns = results.getColumnCount();
			DiscordUser[] operators = new DiscordUser[columns];
			for(int i = 0; i < columns; i++) {
				results.next();
				operators[i] = new DiscordUser(results);
			}
			return operators;
		} catch (SQLException e) {
			throw new AssertionError(e);
		}
	}
	
	public static DiscordUser[] getAllOperators() {
		try {
			PreparedStatement st = ConsoleContext.INSTANCE.getConnection().prepareStatement("SELECT * FROM discord_users INNER JOIN operators ON(discord_users.discordID = operators.discordID);");
			Result results = st.query();
			int columns = results.getColumnCount();
			DiscordUser[] operators = new DiscordUser[columns];
			for(int i = 0; i < columns; i++) {
				results.next();
				operators[i] = new DiscordUser(results);
			}
			return operators;
		} catch (SQLException e) {
			throw new AssertionError(e);
		}
	}
	
	public static void notifyDiscordUsers() throws SQLException {
		int count = Wiimmfi.getAcknowledgedPlayerCount();
		Result result = Table.selectAllFrom(ConsoleContext.INSTANCE, DISCORD_USERS);
		while(result.next()) {
			int threshold = result.getInt(THRESHOLD);
			if(count < threshold) {
				new DiscordUser(result).setDippedBelowThreshold(true);
				continue;
			}
			else {
				if(Instant.parse(result.getString(LAST_NOTIFICATION)).plus(Duration.parse(result.getString(FREQUENCY))).isBefore(Instant.now())) {
					if(result.getBoolean(BELOW_THRESHOLD)) {
						DiscordUser user = new DiscordUser(result);
						if(!result.getBoolean(NOTIFY_CONTINUOUSLY)) {
							user.setDippedBelowThreshold(false);
						}
						if(threshold != -1) {
							for(Player player : Wiimmfi.getOnlinePlayers()) {
								if(player.getDiscord() == result.getLong(DISCORD_ID)) {
									return;
								}
							}
							user.sendMessage("Players Online" + Wiimmfi.getOnlinePlayerList(user.sendDetailedPM()));
							user.setLastNotification();
						}
					}
				}
			}
		}
	}
	
	@Override
	public void finalize() {
		try {
			connection.close();
		} catch (SQLException e) {
			ConsoleUser.getConsoleUser().sendMessage(StacktraceUtil.getStackTrace(e));
		}
	}

	public static void attemptRegister() {
		DesiredProfile[] profiles = desiredProfiles.toArray(new DesiredProfile[] {});
		for(DesiredProfile profile : profiles) {
			if(profile.getRegistrationTimeout().isBefore(Instant.now())) {
				profile.getRequester().sendMessage("Registration for " + profile.getDesiredProfile().toFullString() + " timed out");
				desiredProfiles.remove(profile);
				continue;
			}
			if(profile.isVerified()) {
				profile.register();
				desiredProfiles.remove(profile);
				continue;
			}
		}
	}
	
}
