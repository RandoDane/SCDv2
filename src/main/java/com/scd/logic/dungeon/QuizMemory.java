package com.scd.logic.dungeon;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;

import java.io.Reader;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Quiz answers SCD learned itself: follows the chat ("Question #N", the question, the three
 * options), remembers which option you picked, and when Oruo says you answered correctly, keeps
 * that question with its answer.
 */
public final class QuizMemory {
	private static final Pattern QUESTION_HEADER = Pattern.compile("^Question #\\d+$");
	private static final Pattern CHOICE = Pattern.compile("^([ⓐⓑⓒ]) (.+)$");
	private static final Pattern CORRECT = Pattern.compile("^\\[STATUE] Oruo the Omniscient: (\\w{1,16}) answered (?:Question #\\d+|the final question) correctly!$");

	private final Map<String, String> answers = new LinkedHashMap<>();
	/** Answers other players' mods confirmed (from the SCD server); yours win. */
	private final Map<String, String> shared = new java.util.concurrent.ConcurrentHashMap<>();
	private String[] lastLearned;
	private final Map<String, String> options = new HashMap<>();
	private boolean expectQuestion;
	private String question;
	private String picked;

	/** Feeds one chat line (formatting removed, trimmed). Returns true when a new answer was learned. */
	public boolean onLine(String line, String selfName) {
		if (QUESTION_HEADER.matcher(line).matches()) {
			expectQuestion = true;
			question = null;
			picked = null;
			options.clear();
			return false;
		}
		Matcher m = CHOICE.matcher(line);
		if (m.matches()) {
			options.put(m.group(1), m.group(2).trim());
			return false;
		}
		if (expectQuestion && !line.isEmpty()) {
			question = line;
			expectQuestion = false;
			return false;
		}
		m = CORRECT.matcher(line);
		if (m.matches() && m.group(1).equals(selfName) && question != null && picked != null && options.containsKey(picked)) {
			String answer = options.get(picked);
			String before = answers.put(question, answer);
			lastLearned = new String[]{question, answer};
			question = null;
			picked = null;
			return !answer.equals(before);
		}
		return false;
	}

	/** The option letter (ⓐ/ⓑ/ⓒ) the player chose for the current question. */
	public void pick(String letter) {
		picked = letter;
	}

	/** Your answer for a question, else the shared one; null if neither knows it. */
	public String answer(String question) {
		String own = answers.get(question);
		return own != null ? own : shared.get(question);
	}

	/** The {question, answer} learned by the last {@link #onLine} that returned true. */
	public String[] lastLearned() {
		return lastLearned;
	}

	public void setShared(Map<String, String> fromServer) {
		shared.clear();
		shared.putAll(fromServer);
	}

	public String currentQuestion() {
		return question;
	}

	public int size() {
		return answers.size();
	}

	public void load(Reader json) {
		JsonParser.parseReader(json).getAsJsonObject().entrySet().forEach(e -> answers.put(e.getKey(), e.getValue().getAsString()));
	}

	public String toJson() {
		return new GsonBuilder().setPrettyPrinting().create().toJson(answers);
	}
}
