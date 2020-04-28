package com.gamebuster19901.excite;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashSet;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class Wiimmfi {
	
	private static final Logger logger = Logger.getLogger(Wiimmfi.class.getName());
	
	private static final URL EXCITEBOTS;
	static {
		try {
			EXCITEBOTS = new URL("https://wiimmfi.de/game/exciteracewii");
		} catch (MalformedURLException e) {
			throw new AssertionError(e);
		}
	}
	
	private URL url;
	private Document document;
	
	public Wiimmfi() {
		this(EXCITEBOTS);
	}
	
	public Wiimmfi(URL url) {
		update(url);
	};
	
	public Wiimmfi(String url) throws MalformedURLException {
		this(new URL(url));
	}

	public void update(URL url) {
		this.url = url;
		update();
	}
	
	public void update() {
		if(url != null) {
			try {
				if(url.getProtocol().equals("file")) {
					logger.info("opening " + url);
					document = Jsoup.parse(new File(url.toURI()), null);
					logger.info("opened " + url);
				}
				else {
					logger.info("connecting to " + url);
					document = Jsoup.connect(url.toString()).get();
					logger.info("connected to " + url);
				}
			}
			catch(Exception e) {
				logger.log(Level.WARNING, e, () -> e.getMessage());
			}
		}
	}
	
	public Player[] getOnlinePlayers() {
		HashSet<Player> players = new HashSet<Player>();
		if(document != null) {
			document.getElementsByAttributeValueContaining("id", "game").remove();
			Elements elements = document.getElementsByClass("tr0");
			elements.addAll(document.getElementsByClass("tr1"));
			for(Element e : elements) {
				if(!e.hasClass("tr0") && !e.hasClass("tr1")) {
					e.remove();
				}
			}
			if(elements.size() > 0) {
				Elements playerEntries = elements;
				for(Element e : playerEntries) {
					
					String name = parseLine(e.html(), 10);
					int playerId = Integer.parseInt(parseLine(e.html(), 1));
					
					Player player = Player.getPlayer(playerId);
					if(player != null) {
						player.setName(name);
					}
					else {
						String friendCode = parseLine(e.html(), 2);
						player = new Player(name, friendCode, playerId);
						Player.addPlayer(player);
					}
					players.add(player);
				}
			}
		}
		return players.toArray(new Player[]{});
	}
	
	public static Player[] getKnownPlayers() {
		return Player.getEncounteredPlayers();
	}
	
	private static String parseLine(String s, int line) {
		String[] lines = s.split("\n");
		return lines[line].replace("<td>", "").replaceAll("</td>", "").replaceAll(" ", "");
	}
	
}
