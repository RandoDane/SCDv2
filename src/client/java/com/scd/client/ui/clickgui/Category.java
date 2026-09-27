package com.scd.client.ui.clickgui;

import java.util.List;

/** A click-GUI column. */
public record Category(String name, List<Module> modules) {
}
