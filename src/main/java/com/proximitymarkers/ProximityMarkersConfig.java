package com.proximitymarkers;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(ProximityMarkersPlugin.CONFIG_GROUP)
public interface ProximityMarkersConfig extends Config
{
	@ConfigItem(
		keyName = "pluginEnabled",
		name = "Enable plugin",
		description = "Master toggle for proximity marker highlights"
	)
	default boolean pluginEnabled()
	{
		return true;
	}
}
