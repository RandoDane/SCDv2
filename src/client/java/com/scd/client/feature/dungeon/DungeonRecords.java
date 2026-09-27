package com.scd.client.feature.dungeon;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Persisted per-floor split PBs and a log of estimate-vs-final scores (dungeon.json). */
public final class DungeonRecords {
	/** floor -> split name -> best ms from the run start. */
	public Map<String, Map<String, Long>> bestSplits = new HashMap<>();
	/** Room name -> recent clear times (ms, server lag removed), oldest first. */
	public Map<String, List<Long>> roomTimes = new HashMap<>();
	/** Most recent runs first; capped. */
	public List<RunResult> runs = new ArrayList<>();

	public static final class RunResult {
		public String floor;
		public long endedAt;
		public Integer finalScore;
		public Integer estimate;
		public Map<String, Long> splits = new HashMap<>();
		public int deaths;
		public Integer secrets;
		/** Server lag during the run (ms); a laggy run's times are marked. */
		public long lagMs;
	}
}
