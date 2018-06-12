/*
 * Copyright (c) 2018, Tomas Slusny <slusnucky@gmail.com>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.runelite.client.ui.overlay;

import com.google.common.annotations.VisibleForTesting;
import java.awt.Dimension;
import java.awt.Point;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.RuneLiteConfig;

/**
 * Manages state of all game overlays
 */
@Singleton
public class OverlayManager
{
	private static final String OVERLAY_CONFIG_PREFERRED_LOCATION = "_preferredLocation";
	private static final String OVERLAY_CONFIG_PREFERRED_POSITION = "_preferredPosition";
	private static final String OVERLAY_CONFIG_PREFERRED_SIZE = "_preferredSize";
	private static final String RUNELITE_CONFIG_GROUP_NAME = RuneLiteConfig.class.getAnnotation(ConfigGroup.class).keyName();

	@Getter
	private final List<Overlay> overlays = new CopyOnWriteArrayList<>();

	@Getter
	private final Map<OverlayLayer, List<Overlay>> overlayLayers = new ConcurrentHashMap<>();

	private final ConfigManager configManager;

	@Inject
	private OverlayManager(final ConfigManager configManager)
	{
		this.configManager = configManager;
	}

	/**
	 * Add overlay.
	 *
	 * @param overlay the overlay
	 * @return true if overlay was added
	 */
	public boolean add(final Overlay overlay)
	{
		final boolean add = overlays.add(overlay);

		if (add)
		{
			final Point location = loadOverlayLocation(overlay);
			overlay.setPreferredLocation(location);
			final Dimension size = loadOverlaySize(overlay);
			overlay.setPreferredSize(size);
			final OverlayPosition position = loadOverlayPosition(overlay);
			overlay.setPreferredPosition(position);
			sortOverlays(overlays);
			rebuildOverlayLayers();
		}

		return add;
	}

	/**
	 * Remove overlay.
	 *
	 * @param overlay the overlay
	 * @return true if overlay was removed
	 */
	public boolean remove(final Overlay overlay)
	{
		final boolean remove = overlays.remove(overlay);

		if (remove)
		{
			sortOverlays(overlays);
			rebuildOverlayLayers();
		}

		return remove;
	}

	/**
	 * Remove if overlay matches filter
	 *
	 * @param filter the filter
	 * @return true if any overlay was removed
	 */
	public boolean removeIf(Predicate<Overlay> filter)
	{
		final boolean removeIf = overlays.removeIf(filter);
		sortOverlays(overlays);
		rebuildOverlayLayers();
		return removeIf;
	}

	/**
	 * Clear all overlays
	 */
	public void clear()
	{
		overlays.clear();
		sortOverlays(overlays);
		rebuildOverlayLayers();
	}

	/**
	 * Force save overlay data
	 *
	 * @param overlay overlay to save
	 */
	public void saveOverlay(final Overlay overlay)
	{
		saveOverlayPosition(overlay);
		saveOverlaySize(overlay);
		saveOverlayLocation(overlay);
		sortOverlays(overlays);
		rebuildOverlayLayers();
	}

	/**
	 * Resets stored overlay position data
	 *
	 * @param overlay overlay to reset
	 */
	public void resetOverlay(final Overlay overlay)
	{
		final String locationKey = overlay.getName() + OVERLAY_CONFIG_PREFERRED_LOCATION;
		final String positionKey = overlay.getName() + OVERLAY_CONFIG_PREFERRED_POSITION;
		final String sizeKey = overlay.getName() + OVERLAY_CONFIG_PREFERRED_SIZE;
		configManager.unsetConfiguration(RUNELITE_CONFIG_GROUP_NAME, locationKey);
		configManager.unsetConfiguration(RUNELITE_CONFIG_GROUP_NAME, positionKey);
		configManager.unsetConfiguration(RUNELITE_CONFIG_GROUP_NAME, sizeKey);
		sortOverlays(overlays);
		rebuildOverlayLayers();
	}

	private void rebuildOverlayLayers()
	{
		overlayLayers.clear();

		for (final Overlay overlay : overlays)
		{
			OverlayLayer layer = overlay.getLayer();

			if (overlay.getPreferredLocation() != null && overlay.getPreferredPosition() == null)
			{
				// When UNDER_WIDGET overlays are in preferred locations, move to
				// ABOVE_WIDGETS so that it can draw over interfaces
				if (layer == OverlayLayer.UNDER_WIDGETS)
				{
					layer = OverlayLayer.ABOVE_WIDGETS;
				}
			}

			overlayLayers.compute(layer, (key, value) ->
			{
				if (value == null)
				{
					value = new CopyOnWriteArrayList<>();
				}

				value.add(overlay);
				return value;
			});
		}
	}

	private void saveOverlayLocation(final Overlay overlay)
	{
		final String key = overlay.getName() + OVERLAY_CONFIG_PREFERRED_LOCATION;
		if (overlay.getPreferredLocation() != null)
		{
			configManager.setConfiguration(
				RUNELITE_CONFIG_GROUP_NAME,
				key,
				overlay.getPreferredLocation());
		}
		else
		{
			configManager.unsetConfiguration(
				RUNELITE_CONFIG_GROUP_NAME,
				key);
		}
	}

	private void saveOverlaySize(final Overlay overlay)
	{
		final String key = overlay.getName() + OVERLAY_CONFIG_PREFERRED_SIZE;
		if (overlay.getPreferredSize() != null)
		{
			configManager.setConfiguration(
				RUNELITE_CONFIG_GROUP_NAME,
				key,
				overlay.getPreferredSize());
		}
		else
		{
			configManager.unsetConfiguration(
				RUNELITE_CONFIG_GROUP_NAME,
				key);
		}
	}

	private void saveOverlayPosition(final Overlay overlay)
	{
		final String key = overlay.getName() + OVERLAY_CONFIG_PREFERRED_POSITION;
		if (overlay.getPreferredPosition() != null)
		{
			configManager.setConfiguration(
				RUNELITE_CONFIG_GROUP_NAME,
				key,
				overlay.getPreferredPosition());
		}
		else
		{
			configManager.unsetConfiguration(
				RUNELITE_CONFIG_GROUP_NAME,
				key);
		}
	}

	private Point loadOverlayLocation(final Overlay overlay)
	{
		final String key = overlay.getName() + OVERLAY_CONFIG_PREFERRED_LOCATION;
		return configManager.getConfiguration(RUNELITE_CONFIG_GROUP_NAME, key, Point.class);
	}

	private Dimension loadOverlaySize(final Overlay overlay)
	{
		final String key = overlay.getName() + OVERLAY_CONFIG_PREFERRED_SIZE;
		return configManager.getConfiguration(RUNELITE_CONFIG_GROUP_NAME, key, Dimension.class);
	}

	private OverlayPosition loadOverlayPosition(final Overlay overlay)
	{
		final String locationKey = overlay.getName() + OVERLAY_CONFIG_PREFERRED_POSITION;
		return configManager.getConfiguration(RUNELITE_CONFIG_GROUP_NAME, locationKey, OverlayPosition.class);
	}

	@VisibleForTesting
	static void sortOverlays(List<Overlay> overlays)
	{
		overlays.sort((a, b) ->
		{
			if (a.getPosition() != b.getPosition())
			{
				// This is so non-dynamic overlays render after dynamic
				// overlays, which are generally in the scene
				return a.getPosition().compareTo(b.getPosition());
			}

			// For dynamic overlays, higher priority means to
			// draw *later* so it is on top.
			// For non-dynamic overlays, higher priority means
			// draw *first* so that they are closer to their
			// defined position.
			return a.getPosition() == OverlayPosition.DYNAMIC
				? a.getPriority().compareTo(b.getPriority())
				: b.getPriority().compareTo(a.getPriority());
		});
	}
}