package com.gamebuster19901.excite.util;

import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAccessor;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;

public final class TimeUtils {
	public static final DateTimeFormatter DB_DATE_FORMATTER = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withLocale(Locale.US).withZone(ZoneId.of("UTC"));
	public static final SimpleDateFormat RIICONNECT24_FORMATTER = new SimpleDateFormat("dd MMM YYYY HH:mm:ss XX");
	public static final Duration FOREVER = ChronoUnit.FOREVER.getDuration();
	public static final Instant PLAYER_EPOCH = Instant.parse("2020-11-01T07:00:00Z"); //The second 2:00 am EST that occurs due do daylight savings
	public static String readableDuration(Duration duration, boolean... units) {
		
		if(units.length == 0) {
			units = new boolean[] {true, true, true, true, true, true, true};
		}
		else if (units.length != 7) {
			units = Arrays.copyOf(units, 7);
		}
		if(units.length != 7) {
			throw new IllegalArgumentException(Arrays.toString(units));
		}
		
		int j = -1;
		for(int i = 0; i < units.length; i++) {
			if(units[i]) {
				j = i;
			}
			if(i == units.length && j == -1) {
				throw new IllegalArgumentException("Units must contain at least one true value");
			}
		}
		
		String smallestUnit;
		switch(j) {
			case 0:
				smallestUnit = "years";
				break;
			case 1:
				smallestUnit = "months";
				break;
			case 2:
				smallestUnit = "weeks";
				break;
			case 3:
				smallestUnit = "days";
				break;
			case 4:
				smallestUnit = "hours";
				break;
			case 5:
				smallestUnit = "minutes";
				break;
			case 6:
				smallestUnit = "seconds";
				break;
			default:
				throw new AssertionError();
		}
		
		String time = " ";
		int k = 0;
		
		if(duration.equals(ChronoUnit.FOREVER.getDuration())) {
			return " forever";
		}
		
		if(duration.isNegative()) {
			time = "-";
			duration = duration.abs();
		}
		if(units[k++] && duration.toDays() / 365 > 0) {
			time += duration.toDays() / 365 + " years#";
			duration = duration.minus(Duration.ofDays((duration.toDays() / 365) * 365));
		}
		if(units[k++] && duration.toDays() / 30 > 0) {
			time += duration.toDays() / 30 + " months#";
			duration = duration.minus(Duration.ofDays((duration.toDays() / 30) * 30));
		}
		if (units[k++] && duration.toDays() / 7 > 0) {
			time += duration.toDays() / 7 + " weeks#";
			duration = duration.minus(Duration.ofDays((duration.toDays() / 7) * 7));
			System.out.println(duration.toDays());
		}
		if(units[k++] && duration.toDays() > 0) {
			time += duration.toDays() + " days#";
			duration = duration.minus(Duration.ofDays(duration.toDays()));
		}
		if(units[k++] && duration.toHours() > 0) {
			time += duration.toHours() + " hours#";
			duration = duration.minus(Duration.ofHours(duration.toHours()));
		}
		if(units[k++] && duration.toMinutes() > 0) {
			time += duration.toMinutes() + " minutes#";
			duration = duration.minus(Duration.ofMinutes(duration.toMinutes()));
		}
		if(units[k++] && duration.getSeconds() > 0) {
			time += duration.getSeconds() + " seconds";
			duration = duration.minus(Duration.ofSeconds(duration.getSeconds()));
		}
		
		time = time.replaceAll("#", " ");
		time = time.replaceAll(" 1 years", " 1 year");
		time = time.replaceAll(" 1 months", " 1 month");
		time = time.replaceAll(" 1 weeks", " 1 week");
		time = time.replaceAll(" 1 days", " 1 day");
		time = time.replaceAll(" 1 hours", " 1 hour");
		time = time.replaceAll(" 1 minutes", " 1 minute");
		time = time.replaceAll(" 1 seconds", " 1 second");
		time = time.trim();
		if(time.isEmpty()) {
			return "0 " + smallestUnit;
		}
		return time;
	}
	
	public static Instant fromNow(Duration duration) {
		try {
			return Instant.now().plus(duration);
		}
		catch(ArithmeticException e) {
			return Instant.MAX;
		}
	}
	
	public static Duration since(Instant instant) {
		return Duration.between(instant, Instant.now());
	}
	
	public static Duration computeDuration(int amount, String timeUnit) {
		Duration duration = null;
		if(isSeconds(timeUnit)) {
			duration = Duration.ofSeconds(amount);
		}
		else if(isMinutes(timeUnit)) {
			duration = Duration.ofMinutes(amount);
		}
		else if (isHours(timeUnit)) {
			duration = Duration.ofHours(amount);
		}
		else if (isDays(timeUnit)) {
			duration = Duration.ofDays(amount);
		}
		else if (isWeeks(timeUnit)) {
			duration = Duration.ofDays(amount * 7);
		}
		else if (isMonths(timeUnit)) {
			duration = Duration.ofDays(amount * 30);
		}
		else if (isYears(timeUnit)) {
			duration = Duration.ofDays(amount * 365);
		}
		return duration;
	}
	
	public static Instant parseInstant(String instant) {
		if(instant == null) {
			return Instant.MIN;
		}
		return Instant.parse(instant);
	}
	
	public static String parsePlayerInstant(Instant instant) {
		SimpleDateFormat date = new SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.ENGLISH);
		String ret = date.format(instant.toEpochMilli());
		if(instant.compareTo(PLAYER_EPOCH) <= 0) {
			ret = "Before " + ret;
		}
		return ret;
	}
	
	public static String getDBDate(TemporalAccessor temporal) {
		return DB_DATE_FORMATTER.format(temporal);
	}
	
	public static String getRC24Date(Date date) {
		return RIICONNECT24_FORMATTER.format(date);
	}
	
	public static Month getMonth(LocalDate date) {
		return date.getMonth();
	}
	
	private static boolean isSeconds(String timeUnit) {
		return timeUnit.equalsIgnoreCase("s") || timeUnit.equalsIgnoreCase("sec") || timeUnit.equalsIgnoreCase("secs") || timeUnit.equalsIgnoreCase("second") || timeUnit.equalsIgnoreCase("seconds");
	}
	
	private static boolean isMinutes(String timeUnit) {
		return timeUnit.equalsIgnoreCase("m") || timeUnit.equalsIgnoreCase("min") || timeUnit.equalsIgnoreCase("mins") ||  timeUnit.equalsIgnoreCase("minute") || timeUnit.equalsIgnoreCase("minutes");
	}
	
	private static boolean isHours(String timeUnit) {
		return timeUnit.equalsIgnoreCase("h") || timeUnit.equalsIgnoreCase("hr") || timeUnit.equalsIgnoreCase("hrs") ||  timeUnit.equalsIgnoreCase("hour") || timeUnit.equalsIgnoreCase("hours");
	}
	
	private static boolean isDays(String timeUnit) {
		return timeUnit.equalsIgnoreCase("d") || timeUnit.equalsIgnoreCase("day") || timeUnit.equalsIgnoreCase("days");
	}
	
	private static boolean isWeeks(String timeUnit) {
		return timeUnit.equalsIgnoreCase("w") || timeUnit.equalsIgnoreCase("week") || timeUnit.equalsIgnoreCase("weeks");
	}
	
	private static boolean isMonths(String timeUnit ) {
		return timeUnit.equalsIgnoreCase("mo") || timeUnit.equalsIgnoreCase("month") || timeUnit.equalsIgnoreCase("months");
	}
	
	private static boolean isYears(String timeUnit) {
		return timeUnit.equalsIgnoreCase("y") || timeUnit.equalsIgnoreCase("yr") || timeUnit.equalsIgnoreCase("yrs") || timeUnit.equalsIgnoreCase("year") || timeUnit.equalsIgnoreCase("years");
	}
}