package com.gamebuster19901.excite.bot.mail;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

import com.gamebuster19901.excite.bot.user.Wii;
import com.gamebuster19901.excite.game.challenge.Rewardable;

public class RefundResponse extends MailReplyResponse implements Rewardable {

	private final Rewardable rewardable;
	
	public RefundResponse(Wii responder, MimeMessage message, Rewardable rewardable) throws MessagingException {
		super(responder, message);
		this.rewardable = rewardable;
	}

	@Override
	public int getReward() {
		return rewardable.getReward();
	}

	@Override
	protected String getResponseTemplate() {
		// TODO Auto-generated method stub
		return null;
	}
	
	@Override
	public void initVars() {
		super.initVars();
	}

}
