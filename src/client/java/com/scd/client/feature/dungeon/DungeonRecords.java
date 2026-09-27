package com.scd.client.feature.dungeon;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Persisted per-floor split PBs and a log of estimate-vs-final scores (dungeon.json). */
public final class DungeonRecords {
	/** floor -> split name -> best ms from the run start. */
	public Map<String, Map<String, Long>> bestSplits = new HashMap<>();
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
	}
}
