package com.scd.client.feature.carry;

import java.util.ArrayList;
import java.util.List;

/** Persisted list of every carry (config/scd/carries.json). */
public final class CarryBook {
	public long nextId = 1;
	public List<Carry> carries = new ArrayList<>();
}
