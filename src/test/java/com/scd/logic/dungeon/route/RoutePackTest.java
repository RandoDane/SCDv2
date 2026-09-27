package com.scd.logic.dungeon.route;

import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RoutePackTest {
	private static final String SECRET_ROUTES_SAMPLE = """
			{"#origin":"Secret Routes Mod","#copyright":"x","Version":"1.0.0",
			 "Altar-6":[{"locations":[[14,72,45]],"etherwarps":[],"mines":[],"interacts":[],"tnts":[],"enderpearls":[],"enderpearlangles":[],"secret":{"type":"item","location":[14,71,48]}},
			            {"locations":[[25,55,34],[7,68,29]],"etherwarps":[[26,54,35]],"mines":[[31,54,35]],"interacts":[],"tnts":[],"enderpearls":[[1,2,3]],"enderpearlangles":[[90.5,-12.0]],"secret":{"type":"exit","location":[7,68,29]}}],
			 "Altar-6:2":[]}
			""";

	@Test
	void readsSecretRoutesFormatAndRenamesRooms() {
		Map<String, String> alias = Map.of("Altar-6", "Altar");
		RoutePack pack = RoutePack.read(new StringReader(SECRET_ROUTES_SAMPLE), n -> alias.getOrDefault(n, n));
		assertEquals(2, pack.routesFor("Altar").size());
		assertEquals("Altar", pack.routesFor("Altar").getFirst().key());
		var steps = pack.rooms.get("Altar");
		assertEquals(2, steps.size());
		assertEquals(RouteStep.SecretType.ITEM, steps.get(0).secretType);
		assertArrayEquals(new int[]{14, 71, 48}, steps.get(0).secret);
		assertArrayEquals(new int[]{26, 54, 35}, steps.get(1).etherwarps.getFirst());
		assertEquals(90.5f, steps.get(1).pearlAngles.getFirst()[0]);
		assertEquals("Secret Routes Mod", pack.header.get("#origin"));
	}

	@Test
	void roundTripsThroughWriteAndShareCode() {
		RoutePack pack = RoutePack.read(new StringReader(SECRET_ROUTES_SAMPLE), n -> n);
		RoutePack again = RoutePack.read(new StringReader(pack.write()), n -> n);
		assertEquals(pack.rooms.keySet(), again.rooms.keySet());
		assertArrayEquals(new int[]{31, 54, 35}, again.rooms.get("Altar-6").get(1).mines.getFirst());
		assertEquals("x", again.header.get("#copyright"));

		String code = ShareCode.encode("Altar", pack.rooms.get("Altar-6"));
		assertTrue(code.startsWith(ShareCode.PREFIX));
		var decoded = ShareCode.decode(code);
		assertEquals("Altar", decoded.room());
		assertEquals(2, decoded.steps().size());
		assertEquals(RouteStep.SecretType.EXIT, decoded.steps().get(1).secretType);
		assertThrows(IllegalArgumentException.class, () -> ShareCode.decode("SCDR1:!!!"));
		assertThrows(IllegalArgumentException.class, () -> ShareCode.decode("hello"));
	}

	@Test
	void everyRecordingIsItsOwnRoute() {
		RoutePack pack = new RoutePack();
		RouteStep a = new RouteStep();
		a.locations.add(new int[]{2, 70, 15});
		RouteStep b = new RouteStep();
		b.locations.add(new int[]{28, 70, 15});
		assertEquals("Altar", pack.add("Altar", java.util.List.of(a)));
		assertEquals("Altar:2", pack.add("Altar", java.util.List.of(b)));
		assertEquals(2, pack.routesFor("Altar").size());
		assertArrayEquals(new int[]{28, 15}, RoutePack.start(pack.routesFor("Altar").get(1).steps()));
		RoutePack again = RoutePack.read(new StringReader(pack.write()), n -> n);
		assertEquals(2, again.routesFor("Altar").size());
		assertEquals(2, again.removeRoom("Altar"));
		assertTrue(again.rooms.isEmpty());
	}

	@Test
	void handPlacedNodesSurviveSaveAndLoad() {
		RouteStep s = new RouteStep();
		s.manual = true;
		s.locations.add(new int[]{1, 70, 1});
		s.locations.add(new int[]{5, 70, 9});
		s.secretType = RouteStep.SecretType.ITEM;
		s.secret = new int[]{5, 70, 10};
		RouteStep back = RoutePack.readStep(RoutePack.writeStep(s));
		org.junit.jupiter.api.Assertions.assertTrue(back.manual);
		org.junit.jupiter.api.Assertions.assertEquals(2, back.locations.size());
		RouteStep plain = RoutePack.readStep(RoutePack.writeStep(new RouteStep()));
		org.junit.jupiter.api.Assertions.assertFalse(plain.manual);
	}
}
