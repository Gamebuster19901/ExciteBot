package com.gamebuster19901.excite.bot.audit;

import com.gamebuster19901.excite.bot.command.ConsoleContext;
import com.gamebuster19901.excite.bot.command.MessageContext;
import com.gamebuster19901.excite.bot.database.Comparison;
import com.gamebuster19901.excite.bot.database.Insertion;
import com.gamebuster19901.excite.bot.database.Row;
import com.gamebuster19901.excite.bot.database.Table;
import com.gamebuster19901.excite.bot.database.sql.PreparedStatement;

import static com.gamebuster19901.excite.bot.database.Column.*;
import static com.gamebuster19901.excite.bot.database.Comparator.*;
import static com.gamebuster19901.excite.bot.database.Table.*;

import java.io.IOError;
import java.sql.SQLException;

public class BotDeleteMessageAudit extends Audit {

	Audit parentData;
	
	protected BotDeleteMessageAudit(Row result) {
		super(result, AuditType.BOT_MSG_DELETE);
	}
	
	public static BotDeleteMessageAudit addBotDeleteMessageAudit(MessageContext deleter, MessageContext deleted, String reason) {
		Audit parent = Audit.addAudit(deleter, deleted, AuditType.BOT_MSG_DELETE, getMessage(ConsoleContext.INSTANCE, deleted, reason));
		
		PreparedStatement st;
		
		try {
			st = Insertion.insertInto(Table.AUDIT_BOT_MSG_DEL)
			.setColumns(AUDIT_ID, DISCORD_ID)
			.to(parent.getID(), deleted.getSenderId())
			.prepare(deleter, true);
			
			st.execute();
			
			BotDeleteMessageAudit ret = getBotDeleteMessageAuditByID(ConsoleContext.INSTANCE, parent.getID());
			ret.parentData = parent;
			
			return ret;
		}
		catch(SQLException e) {
			throw new IOError(e);
		}
	}
	
	private static BotDeleteMessageAudit getBotDeleteMessageAuditByID(ConsoleContext context, long auditID) {
		return new BotDeleteMessageAudit(Table.selectAllFromJoinedUsingWhere(context, AUDITS, AUDIT_BOT_MSG_DEL, AUDIT_ID, new Comparison(AUDIT_ID, EQUALS, auditID)).getRow(true));
	}
	
	private static String getMessage(MessageContext deleter, MessageContext sender, String reason) {
		return deleter.getAuthor().getIdentifierName() + " deleted a message by " + sender.getAuthor().getIdentifierName() + " due to \"" + reason + "\"";
	}

}
